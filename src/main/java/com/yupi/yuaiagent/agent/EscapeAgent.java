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
 * 离职规划专家 Agent
 * 专注离职流程规划，生成证据链和工作交接清单，可生成 PDF 文件
 * 
 * 优化点：
 * 1. 使用 ChatMemoryManager 统一管理会话记忆
 * 2. 添加 QueryRewriter 查询重写能力
 */
@Slf4j
public class EscapeAgent {

    private static final String SYSTEM_PROMPT = """
            你是一位专业的离职规划顾问，帮助职场人士优雅、体面地完成离职过程。
            你的专长包括：
            1. 离职时机评估：分析当前情况，判断是否是合适的离职时机
            2. 证据链整理：指导用户整理工作成果、绩效记录等重要证据
            3. 工作交接清单：生成详细的工作交接文档，确保平稳过渡
            4. 离职谈判：如何与公司谈判离职条件（补偿、离职时间等）
            5. 劳动权益保护：了解相关劳动法规，保护自身合法权益
            6. 离职后规划：背调应对、竞业协议处理、下一步职业规划
            
            当用户需要生成交接清单或证据整理文档时，请使用文件生成工具输出 PDF 文件。
            """;

    private final ChatClient chatClient;
    private final ToolCallback[] tools;
    private final QueryRewriter queryRewriter;

    /**
     * 构造函数 - 使用注入的 ChatMemoryManager
     */
    public EscapeAgent(ChatModel chatModel, ToolCallback[] tools, 
                      QueryRewriter queryRewriter, ChatMemoryManager chatMemoryManager) {
        this.tools = tools;
        this.queryRewriter = queryRewriter;
        
        // 使用 ChatMemoryManager 获取共享的 ChatMemory
        ChatMemory chatMemory = chatMemoryManager.getMemory("escape");
        
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
        
        log.info("EscapeAgent 初始化完成");
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
