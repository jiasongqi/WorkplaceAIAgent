package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
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
 * 
 * 路由策略：
 * - RESUME：简历优化、面试技巧、求职相关问题 → ResumeAgent
 * - NEGOTIATION：薪资谈判、涨薪、薪酬分析 → NegotiationAgent
 * - ESCAPE：离职、辞职、劳动纠纷 → EscapeAgent
 * - GENERAL：其他职场问题（人际关系、压力、职业规划等）→ GeneralCareerAgent
 * 
 * 注意：YuManus（工具型 Agent）不再通过 Orchestrator 路由，
 * 可通过 /manus/chat 接口单独调用执行具体任务。
 */
@Slf4j
public class OrchestratorAgent {

    // 意图识别提示词
    private static final String INTENT_PROMPT = """
            你是一个职场问题分类器。请分析用户的问题，判断属于以下哪个类别，只输出类别名称，不要有任何其他内容：
            
            - RESUME：涉及简历优化、面试技巧、求职投递、offer 选择、跳槽等求职相关问题
            - NEGOTIATION：涉及薪资谈判、涨薪、薪资包分析、绩效奖金等薪酬相关问题
            - ESCAPE：涉及离职、辞职、被裁员、劳动纠纷、工作交接等离职相关问题
            - GENERAL：其他职场问题，如职场人际关系、工作压力、职业规划、职场困惑、情绪问题等
            
            用户问题：{message}
            """;

    private final ChatClient intentClient;
    private final ResumeAgent resumeAgent;
    private final NegotiationAgent negotiationAgent;
    private final EscapeAgent escapeAgent;
    private final GeneralCareerAgent generalCareerAgent;

    /**
     * 构造函数 - 使用 ChatMemoryManager
     */
    public OrchestratorAgent(ChatModel chatModel, VectorStore vectorStore,
                             ToolCallback[] tools, QueryRewriter queryRewriter,
                             ChatMemoryManager chatMemoryManager) {
        this.intentClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        
        // 创建各专业 Agent
        this.resumeAgent = new ResumeAgent(chatModel, vectorStore, queryRewriter, chatMemoryManager);
        this.negotiationAgent = new NegotiationAgent(chatModel, tools, queryRewriter, chatMemoryManager);
        this.escapeAgent = new EscapeAgent(chatModel, tools, queryRewriter, chatMemoryManager);
        this.generalCareerAgent = new GeneralCareerAgent(chatModel, chatMemoryManager);
        
        log.info("OrchestratorAgent 初始化完成，已创建 4 个专业 Agent");
    }

    /**
     * 识别用户意图（使用枚举统一处理）
     */
    private AgentIntent detectIntent(String message) {
        String rawIntent = intentClient.prompt()
                .user(INTENT_PROMPT.replace("{message}", message))
                .call()
                .content();
        
        AgentIntent intent = AgentIntent.fromRawIntent(rawIntent);
        log.info("意图识别结果：{}（原始输出：{}）", intent, rawIntent);
        return intent;
    }

    /**
     * 根据意图路由到对应子 Agent（同步）
     */
    public String chat(String message, String chatId) {
        AgentIntent intent = detectIntent(message);
        return switch (intent) {
            case RESUME -> {
                log.info("路由到 ResumeAgent");
                yield resumeAgent.chat(message, chatId);
            }
            case NEGOTIATION -> {
                log.info("路由到 NegotiationAgent");
                yield negotiationAgent.chat(message, chatId);
            }
            case ESCAPE -> {
                log.info("路由到 EscapeAgent");
                yield escapeAgent.chat(message, chatId);
            }
            default -> {
                log.info("路由到 GeneralCareerAgent");
                yield generalCareerAgent.chat(message, chatId);
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
     *   <li>订阅子 Agent 的 {@code Flux<String>}，每个 token 到来立即推送</li>
     * </ol>
     */
    public SseEmitter chatStream(String message, String chatId) {
        SseEmitter emitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                // 1. 意图识别（同步，通常很快）
                AgentIntent intent = detectIntent(message);
                log.info("意图识别结果：{}，路由到：{}", intent, intent.getAgentName());

                // 2. 推送路由事件，让前端立即知道交给了哪个专家
                emitter.send(SseEmitter.event()
                        .name("routing")
                        .data("[路由到" + intent.getAgentName() + "]"));

                // 3. 根据意图选择子 Agent 并流式输出
                Flux<String> tokenFlux = switch (intent) {
                    case RESUME -> resumeAgent.chatStream(message, chatId);
                    case NEGOTIATION -> negotiationAgent.chatStream(message, chatId);
                    case ESCAPE -> escapeAgent.chatStream(message, chatId);
                    default -> generalCareerAgent.chatStream(message, chatId);
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
}
