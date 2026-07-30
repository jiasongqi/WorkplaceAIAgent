package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.rag.PipelineRagAdvisorFactory;
import com.yupi.yuaiagent.rag.QueryRewriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 简历优化专家 Agent — RAG 经 {@link PipelineRagAdvisorFactory} 统一 Pipeline。
 */
@Slf4j
public class ResumeAgent {

    private static final String SYSTEM_PROMPT = """
            你是一位资深的职场简历优化专家和求职顾问，拥有10年以上的招聘和职业规划经验。
            你的专长包括：
            1. 简历结构优化：帮助用户打造清晰、有力的简历框架
            2. 成果量化表达：将工作经历转化为可量化的成果描述
            3. 面试技巧指导：提供 STAR 法则、常见问题应对策略
            4. Offer 评估：从薪资、发展空间、公司文化等维度综合评估
            5. 求职策略：内推渠道、简历投递时机、岗位匹配度分析
            
            请基于知识库中的求职相关文档，给出专业、具体、可落地的建议。

            【知识库引用规则】
            - 当你的回答参考了知识库检索到的内容时，必须在相关段落后注明文档来源（如"（参考：《XXX》）"）。
            - 如果知识库没有检索到相关内容，请明确告知用户"知识库中未找到直接相关的资料"，再基于你的通用经验作答，不要暗示信息来自知识库。
            """;

    private final ChatClient chatClient;
    private final QueryRewriter queryRewriter;

    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final int TOP_K = 5;
    private static final String STATUS_FILTER = "求职";

    public ResumeAgent(ChatModel chatModel,
                       PipelineRagAdvisorFactory pipelineRagAdvisorFactory,
                       QueryRewriter queryRewriter,
                       ChatMemoryManager chatMemoryManager) {
        this.queryRewriter = queryRewriter;
        ChatMemory chatMemory = chatMemoryManager.getMemory("resume");

        Advisor ragAdvisor = pipelineRagAdvisorFactory.createAdvisor(
                STATUS_FILTER, SIMILARITY_THRESHOLD, TOP_K);

        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor(),
                        ragAdvisor
                )
                .build();

        log.info("ResumeAgent 初始化完成（Pipeline RAG）：相似度阈值={}, topK={}, 状态过滤={}",
                SIMILARITY_THRESHOLD, TOP_K, STATUS_FILTER);
    }

    public String chat(String message, String chatId) {
        return chat(message, chatId, null);
    }

    public String chat(String message, String chatId, String profileInjection) {
        String rewritten = queryRewriter.doQueryRewrite(message);
        log.debug("查询重写：{} -> {}", message, rewritten);

        ChatResponse response = chatClient.prompt()
                .system(buildSystemPrompt(profileInjection))
                .user(rewritten)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        return response.getResult().getOutput().getText();
    }

    public Flux<String> chatStream(String message, String chatId) {
        return chatStream(message, chatId, null);
    }

    public Flux<String> chatStream(String message, String chatId, String profileInjection) {
        String rewritten = queryRewriter.doQueryRewrite(message);
        log.debug("查询重写：{} -> {}", message, rewritten);

        return chatClient.prompt()
                .system(buildSystemPrompt(profileInjection))
                .user(rewritten)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    private static String buildSystemPrompt(String profileInjection) {
        return SYSTEM_PROMPT
                + (profileInjection != null && !profileInjection.isBlank() ? "\n\n" + profileInjection : "");
    }
}
