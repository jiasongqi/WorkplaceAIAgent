package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.rag.QueryRewriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * 主控 Agent（Orchestrator）
 * 根据用户意图智能分发给对应的专业子 Agent，支持真正的 token 级流式输出。
 */
@Slf4j
public class OrchestratorAgent {

    // 意图识别提示词
    private static final String INTENT_PROMPT = """
            你是一个职场问题分类器。请分析用户的问题，判断属于以下哪个类别，只输出类别名称，不要有任何其他内容：
            
            - RESUME：涉及简历优化、面试技巧、求职投递、offer 选择、跳槽等求职相关问题
            - NEGOTIATION：涉及薪资谈判、涨薪、薪资包分析、绩效奖金等薪酬相关问题
            - ESCAPE：涉及离职、辞职、被裁员、劳动纠纷、工作交接等离职相关问题
            - GENERAL：其他职场问题，如职场人际关系、工作效率、职业规划等
            
            用户问题：{message}
            """;

    private final ChatClient intentClient;
    private final ResumeAgent resumeAgent;
    private final NegotiationAgent negotiationAgent;
    private final EscapeAgent escapeAgent;
    // YuManus 内部维护 messageList 状态，不能跨请求复用，保存构造参数按需创建
    private final ToolCallback[] tools;
    private final ChatModel chatModel;

    public OrchestratorAgent(ChatModel chatModel, VectorStore vectorStore,
                             ToolCallback[] tools, QueryRewriter queryRewriter) {
        this.intentClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.resumeAgent = new ResumeAgent(chatModel, vectorStore, queryRewriter);
        this.negotiationAgent = new NegotiationAgent(chatModel, tools);
        this.escapeAgent = new EscapeAgent(chatModel, tools);
        this.tools = tools;
        this.chatModel = chatModel;
    }

    /** 每次请求创建新的 YuManus 实例，避免 messageList 跨请求污染 */
    private YuManus newGeneralAgent() {
        return new YuManus(tools, chatModel);
    }

    /**
     * 识别用户意图
     */
    private String detectIntent(String message) {
        String intent = intentClient.prompt()
                .user(INTENT_PROMPT.replace("{message}", message))
                .call()
                .content();
        String trimmed = intent == null ? "GENERAL" : intent.trim().toUpperCase();
        // 模糊匹配：防止模型输出带标点或前缀（如 "RESUME。" "1. RESUME"）
        for (String key : java.util.List.of("RESUME", "NEGOTIATION", "ESCAPE")) {
            if (trimmed.contains(key)) return key;
        }
        log.info("意图识别结果：GENERAL（原始输出：{}）", trimmed);
        return "GENERAL";
    }

    /**
     * 根据意图路由到对应子 Agent（同步）
     */
    public String chat(String message, String chatId) {
        String intent = detectIntent(message);
        return switch (intent) {
            case "RESUME" -> {
                log.info("路由到 ResumeAgent");
                yield resumeAgent.chat(message, chatId);
            }
            case "NEGOTIATION" -> {
                log.info("路由到 NegotiationAgent");
                yield negotiationAgent.chat(message, chatId);
            }
            case "ESCAPE" -> {
                log.info("路由到 EscapeAgent");
                yield escapeAgent.chat(message, chatId);
            }
            default -> {
                log.info("路由到 GeneralAgent (YuManus)");
                yield newGeneralAgent().run(message);
            }
        };
    }

    /**
     * 根据意图路由到对应子 Agent（SSE 真正流式）。
     *
     * <p>流程：
     * <ol>
     *   <li>意图识别（同步，通常很快）</li>
     *   <li>立即推送 routing 事件，让前端知道交给了哪个专家</li>
     *   <li>RESUME / NEGOTIATION / ESCAPE：直接订阅子 Agent 的 {@code Flux<String>}，
     *       每个 token 到来立即推送，真正 token 级流式</li>
     *   <li>GENERAL（YuManus）：多步 ReAct Agent，每完成一步立即推送该步结果，
     *       不再等全部步骤跑完才一次性发送</li>
     * </ol>
     */
    public SseEmitter chatStream(String message, String chatId) {
        SseEmitter emitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                // 1. 意图识别（同步，通常很快）
                String intent = detectIntent(message);
                String agentName = getAgentName(intent);
                log.info("意图识别结果：{}，路由到：{}", intent, agentName);

                // 2. 推送路由事件，让前端立即知道交给了哪个专家
                emitter.send(SseEmitter.event()
                        .name("routing")
                        .data("[路由到" + agentName + "]"));

                // 3. 根据意图选择子 Agent 并流式输出
                if ("GENERAL".equals(intent)) {
                    // YuManus 是多步 ReAct Agent。
                    // 使用 BaseAgent.runStream(prompt, externalEmitter) 重载：
                    // 每完成一步立即把该步结果推送到当前 emitter，不再等全部步骤结束。
                    // runStream 内部异步执行，完成后自动调用 emitter.complete()。
                    YuManus generalAgent = newGeneralAgent();
                    generalAgent.runStream(message, emitter);
                    // 注意：emitter 的 complete 由 runStream 内部负责，这里直接返回
                    return;
                }

                // RESUME / NEGOTIATION / ESCAPE：token 级流式
                // 每个 token 到来立即推送，doOnComplete 关闭 emitter
                Flux<String> tokenFlux = switch (intent) {
                    case "RESUME" -> resumeAgent.chatStream(message, chatId);
                    case "NEGOTIATION" -> negotiationAgent.chatStream(message, chatId);
                    case "ESCAPE" -> escapeAgent.chatStream(message, chatId);
                    default -> Flux.empty();
                };

                tokenFlux
                        .doOnNext(token -> {
                            try {
                                emitter.send(SseEmitter.event().name("message").data(token));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .doOnError(e -> {
                            log.error("子 Agent 流式输出出错", e);
                            try {
                                emitter.send(SseEmitter.event().name("error").data("执行出错：" + e.getMessage()));
                                emitter.complete();
                            } catch (IOException ex) {
                                emitter.completeWithError(ex);
                            }
                        })
                        .doOnComplete(emitter::complete)
                        .subscribe();

            } catch (IOException e) {
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("OrchestratorAgent 执行出错", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("执行出错：" + e.getMessage()));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    private String getAgentName(String intent) {
        return switch (intent) {
            case "RESUME" -> "简历优化专家";
            case "NEGOTIATION" -> "薪资谈判专家";
            case "ESCAPE" -> "离职规划专家";
            default -> "职场通用顾问";
        };
    }
}
