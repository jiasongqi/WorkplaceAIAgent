package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.memory.MemoryCoordinator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

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
            
            排版要求（聊天气泡）：
            - 小节标题优先用 **加粗**，少用 ### 大标题
            - 若使用 Markdown 标题，# 后必须空一格（正确：### 标题；错误：###标题 或 ###🔍）
            - 不要把 emoji 紧贴在 # 后面
            """;

    private final ChatClient chatClient;
    private final MemoryCoordinator memoryCoordinator; // nullable if memory system is disabled

    /**
     * 构造函数（向后兼容 — 不使用 MemoryCoordinator）
     */
    public GeneralCareerAgent(ChatModel chatModel, ChatMemoryManager chatMemoryManager) {
        this(chatModel, chatMemoryManager, null);
    }

    /**
     * 构造函数（支持 MemoryCoordinator 注入）
     *
     * @param chatModel            LLM 模型
     * @param chatMemoryManager    聊天记忆管理器
     * @param memoryCoordinator    分层记忆协调器（可为 null，@ConditionalOnProperty 条件不满足时为 null）
     */
    public GeneralCareerAgent(ChatModel chatModel, ChatMemoryManager chatMemoryManager,
                              MemoryCoordinator memoryCoordinator) {
        this.memoryCoordinator = memoryCoordinator;

        // 使用 ChatMemoryManager 获取共享的 ChatMemory
        ChatMemory chatMemory = chatMemoryManager.getMemory("general");
        
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
        
        log.info("GeneralCareerAgent 初始化完成, memoryCoordinator={}", memoryCoordinator != null ? "enabled" : "disabled");
    }

    /**
     * 同步对话
     */
    public String chat(String message, String chatId) {
        return chat(message, chatId, (String) null);
    }

    /**
     * 同步对话（支持画像注入）
     *
     * @param profileInjection 可选的用户画像提示片段，非空时动态拼接到系统提示词
     */
    public String chat(String message, String chatId, String profileInjection) {
        return chat(message, chatId, null, profileInjection);
    }

    /**
     * 同步对话（支持 userId + 画像注入 + 分层记忆上下文）
     *
     * @param message           用户消息
     * @param chatId            会话 ID
     * @param userId            用户 ID（可为 null，为 null 时跳过分层记忆组装）
     * @param profileInjection  可选的用户画像提示片段
     */
    public String chat(String message, String chatId, String userId, String profileInjection) {
        String memoryContext = assembleMemoryContext(userId, chatId);

        ChatResponse response = chatClient.prompt()
                .system(buildSystemPrompt(memoryContext, profileInjection))
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();

        String responseText = response.getResult().getOutput().getText();

        // Trigger async extraction after response
        triggerPostTurnExtraction(userId, chatId, message, responseText);

        return responseText;
    }

    /**
     * 流式对话
     */
    public Flux<String> chatStream(String message, String chatId) {
        return chatStream(message, chatId, (String) null);
    }

    /**
     * 流式对话（支持画像注入）
     *
     * @param profileInjection 可选的用户画像提示片段，非空时动态拼接到系统提示词
     */
    public Flux<String> chatStream(String message, String chatId, String profileInjection) {
        return chatStream(message, chatId, null, profileInjection);
    }

    /**
     * 流式对话（支持 userId + 画像注入 + 分层记忆上下文）
     *
     * @param message           用户消息
     * @param chatId            会话 ID
     * @param userId            用户 ID（可为 null，为 null 时跳过分层记忆组装）
     * @param profileInjection  可选的用户画像提示片段
     */
    public Flux<String> chatStream(String message, String chatId, String userId, String profileInjection) {
        String memoryContext = assembleMemoryContext(userId, chatId);

        return chatClient.prompt()
                .system(buildSystemPrompt(memoryContext, profileInjection))
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .doFinally(signal -> triggerPostTurnExtraction(userId, chatId, message, null));
    }

    /**
     * 对话完成后异步触发记忆提取管道。
     * 构造本轮消息列表并委托给 MemoryCoordinator.onTurnCompleted()。
     * 此方法永不抛出异常，所有错误内部捕获并记录日志。
     *
     * @param userId            用户 ID（为 null 或空白时跳过）
     * @param chatId            会话 ID
     * @param userMessage       用户消息
     * @param assistantResponse 助手回复（流式场景下可为 null）
     */
    private void triggerPostTurnExtraction(String userId, String chatId, String userMessage, String assistantResponse) {
        if (memoryCoordinator == null || userId == null || userId.isBlank()) {
            return;
        }
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new UserMessage(userMessage));
            if (assistantResponse != null && !assistantResponse.isBlank()) {
                messages.add(new AssistantMessage(assistantResponse));
            }
            memoryCoordinator.onTurnCompleted(userId, chatId, "general", messages);
        } catch (Exception e) {
            log.warn("Failed to trigger post-turn extraction: {}", e.getMessage());
        }
    }

    /**
     * 调用 MemoryCoordinator 组装分层记忆上下文。
     * 当 memoryCoordinator 为 null 或 userId 为空时，返回空字符串（优雅降级）。
     *
     * @param userId 用户 ID
     * @param chatId 会话 ID
     * @return 组装后的记忆上下文文本，或空字符串
     */
    private String assembleMemoryContext(String userId, String chatId) {
        if (memoryCoordinator == null || userId == null || userId.isBlank()) {
            return "";
        }
        try {
            SystemMessage contextMsg = memoryCoordinator.assembleContext(userId, chatId, "general");
            return contextMsg.getText();
        } catch (Exception e) {
            log.warn("Memory context assembly failed, proceeding without memory: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 构建有效系统提示词：基础提示词 + 分层记忆上下文 + 可选画像注入片段。
     * 记忆上下文放在画像注入之前（提供更宽泛的上下文信息）。
     *
     * @param memoryContext    分层记忆组装的上下文（可为 null 或空白）
     * @param profileInjection 用户画像注入片段（可为 null 或空白）
     */
    private static String buildSystemPrompt(String memoryContext, String profileInjection) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);

        // 记忆上下文放在画像之前，提供更宽泛的历史信息
        if (memoryContext != null && !memoryContext.isBlank()) {
            sb.append("\n\n").append(memoryContext);
        }

        // 画像注入放在最后，距离 LLM 注意力窗口最近
        if (profileInjection != null && !profileInjection.isBlank()) {
            sb.append("\n\n").append(profileInjection);
        }

        return sb.toString();
    }
}
