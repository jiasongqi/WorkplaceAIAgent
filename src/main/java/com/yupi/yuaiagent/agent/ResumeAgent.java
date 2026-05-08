package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.FileBasedChatMemory;
import com.yupi.yuaiagent.rag.QueryRewriter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

/**
 * 简历优化专家 Agent
 * 专注简历优化、面试技巧、offer 评估，RAG 召回求职文档
 */
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
            """;

    private final ChatClient chatClient;
    private final QueryRewriter queryRewriter;

    public ResumeAgent(ChatModel chatModel, VectorStore vectorStore, QueryRewriter queryRewriter) {
        this.queryRewriter = queryRewriter;
        // 文件持久化记忆，支持多轮对话
        ChatMemory chatMemory = new FileBasedChatMemory(
                System.getProperty("user.dir") + "/tmp/chat-memory/resume");
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor(),
                        new QuestionAnswerAdvisor(vectorStore)
                )
                .build();
    }

    public String chat(String message, String chatId) {
        String rewritten = queryRewriter.doQueryRewrite(message);
        ChatResponse response = chatClient.prompt()
                .user(rewritten)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        return response.getResult().getOutput().getText();
    }

    public Flux<String> chatStream(String message, String chatId) {
        String rewritten = queryRewriter.doQueryRewrite(message);
        return chatClient.prompt()
                .user(rewritten)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }
}
