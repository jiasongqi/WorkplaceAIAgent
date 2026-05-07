package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.rag.QueryRewriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * 主控 Agent（Orchestrator）
 * 根据用户意图智能分发给对应的专业子 Agent
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
    private final YuManus generalAgent;

    public OrchestratorAgent(ChatModel chatModel, VectorStore vectorStore,
                             ToolCallback[] tools, QueryRewriter queryRewriter) {
        this.intentClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.resumeAgent = new ResumeAgent(chatModel, vectorStore, queryRewriter);
        this.negotiationAgent = new NegotiationAgent(chatModel, tools);
        this.escapeAgent = new EscapeAgent(chatModel, tools);
        this.generalAgent = new YuManus(tools, chatModel);
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
                // YuManus 是流式 Agent，这里用同步方式运行
                yield generalAgent.run(message);
            }
        };
    }

    /**
     * 根据意图路由到对应子 Agent（SSE 流式）
     */
    public SseEmitter chatStream(String message, String chatId) {
        SseEmitter emitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                String intent = detectIntent(message);
                // 先推送路由信息
                emitter.send(SseEmitter.event()
                        .name("routing")
                        .data("[路由到" + getAgentName(intent) + "]"));

                String result = switch (intent) {
                    case "RESUME" -> resumeAgent.chat(message, chatId);
                    case "NEGOTIATION" -> negotiationAgent.chat(message, chatId);
                    case "ESCAPE" -> escapeAgent.chat(message, chatId);
                    default -> generalAgent.run(message);
                };

                emitter.send(SseEmitter.event().name("message").data(result));
                emitter.complete();
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
