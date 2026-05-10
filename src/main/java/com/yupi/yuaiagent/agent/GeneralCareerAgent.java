package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 职场通用顾问 Agent
 * 处理不适合路由到专业 Agent 的通用职场问题，如：
 * - 职场人际关系、沟通技巧
 * - 工作压力、职业倦怠、情绪管理
 * - 职业规划、发展方向
 * - 职场困惑、迷茫期咨询
 * 
 * 与 YuManus 的区别：
 * - YuManus 是工具型 Agent，适合执行具体任务（搜索、生成文件等）
 * - GeneralCareerAgent 是对话型 Agent，适合提供职场建议和情感支持
 */
@Slf4j
public class GeneralCareerAgent {

    private static final String SYSTEM_PROMPT = """
            你是一位温暖、专业的职场心理咨询师和职业发展顾问，拥有丰富的职场辅导经验。
            你的职责是帮助职场人士解决工作困惑、缓解职业压力、规划职业发展。
            
            你的专长包括：
            1. 情感支持：倾听职场烦恼，提供心理疏导和情绪支持
            2. 人际关系：处理同事矛盾、上下级关系、团队协作问题
            3. 职业规划：帮助梳理职业目标、制定发展路径
            4. 职场适应：应对新环境、新岗位的挑战
            5. 压力管理：提供缓解工作压力的实用建议
            6. 职业转型：探索新的职业方向和可能性
            
            回答风格：
            - 先共情，理解用户的感受和处境
            - 再分析，帮助用户看清问题的本质
            - 最后给出具体、可行的建议
            - 语气温暖、真诚，像一位值得信赖的朋友
            """;

    private final ChatClient chatClient;

    /**
     * 构造函数
     */
    public GeneralCareerAgent(ChatModel chatModel, ChatMemoryManager chatMemoryManager) {
        // 使用 ChatMemoryManager 获取共享的 ChatMemory
        ChatMemory chatMemory = chatMemoryManager.getMemory("general");
        
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
        
        log.info("GeneralCareerAgent 初始化完成");
    }

    /**
     * 同步对话
     */
    public String chat(String message, String chatId) {
        ChatResponse response = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        return response.getResult().getOutput().getText();
    }

    /**
     * 流式对话
     */
    public Flux<String> chatStream(String message, String chatId) {
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }
}
