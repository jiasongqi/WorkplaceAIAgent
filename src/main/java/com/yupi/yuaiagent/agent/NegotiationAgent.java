package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.rag.QueryRewriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

/**
 * 薪资谈判专家 Agent
 * 专注薪资谈判策略，可联网搜索市场薪资数据
 * 
 * 优化点：
 * 1. 使用 ChatMemoryManager 统一管理会话记忆
 * 2. 添加 QueryRewriter 查询重写能力
 */
@Slf4j
public class NegotiationAgent {

    private static final String SYSTEM_PROMPT = """
            你是一位专业的薪资谈判顾问，擅长帮助职场人士在薪资谈判中争取最大利益。
            你的专长包括：
            1. 市场薪资调研：分析目标岗位的市场薪资区间（可使用搜索工具获取最新数据）
            2. 谈判策略制定：根据用户情况制定个性化的谈判方案
            3. 话术指导：提供具体的谈判话术和应对策略
            4. 薪资包拆解：分析基本薪资、绩效奖金、股权激励等综合薪酬结构
            5. 时机把握：指导何时提出薪资要求、如何应对反压
            
            在给出建议前，请先使用搜索工具了解当前市场薪资水平，确保建议基于真实数据。
            """;

    private final ChatClient chatClient;
    private final ToolCallback[] tools;
    private final QueryRewriter queryRewriter;

    /**
     * 构造函数 - 使用注入的 ChatMemoryManager
     */
    public NegotiationAgent(ChatModel chatModel, ToolCallback[] tools, 
                           QueryRewriter queryRewriter, ChatMemoryManager chatMemoryManager) {
        this.tools = tools;
        this.queryRewriter = queryRewriter;
        
        // 使用 ChatMemoryManager 获取共享的 ChatMemory
        ChatMemory chatMemory = chatMemoryManager.getMemory("negotiation");
        
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
        
        log.info("NegotiationAgent 初始化完成");
    }

    public String chat(String message, String chatId) {
        String rewritten = queryRewriter.doQueryRewrite(message);
        log.debug("查询重写：{} -> {}", message, rewritten);
        
        ChatResponse response = chatClient.prompt()
                .user(rewritten)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(tools)
                .call()
                .chatResponse();
        return response.getResult().getOutput().getText();
    }

    public Flux<String> chatStream(String message, String chatId) {
        String rewritten = queryRewriter.doQueryRewrite(message);
        log.debug("查询重写：{} -> {}", message, rewritten);
        
        return chatClient.prompt()
                .user(rewritten)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(tools)
                .stream()
                .content();
    }
}
