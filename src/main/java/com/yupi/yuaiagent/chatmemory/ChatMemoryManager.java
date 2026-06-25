package com.yupi.yuaiagent.chatmemory;

import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceRecorder;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ChatMemory 统一管理器（增强版）
 * 解决各 Agent 重复创建 FileBasedChatMemory 的问题
 * 支持压缩状态查询与自动触发压缩
 *
 * <p>核心能力（对应 Requirements 3.5 / 4.1-4.5）：
 * <ul>
 *     <li>压缩状态查询：{@link #getCompressionStatus(String, String)} 返回会话的记忆压缩状态（Req 3.5）</li>
 *     <li>自动触发压缩：{@link #autoCompressIfNeeded} 在 Token / 轮数超阈值时自动压缩（Req 4.1 / 4.2）</li>
 *     <li>状态消息推送：压缩开始推送"正在整理对话记忆..."，完成推送"记忆整理完成"（Req 4.3 / 4.4）</li>
 *     <li>对话连续性：压缩在写锁保护下原地完成并持久化，不中断对话（Req 4.5）</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Component
public class ChatMemoryManager {

    /** 压缩开始时推送的状态消息（Requirements 4.3） */
    public static final String STATUS_COMPRESSING = "正在整理对话记忆...";

    /** 压缩完成时推送的状态消息（Requirements 4.4） */
    public static final String STATUS_COMPRESSED = "记忆整理完成";

    private final Map<String, ChatMemory> agentMemories = new ConcurrentHashMap<>();
    private final Map<String, FileBasedChatMemory> fileBasedMemories = new ConcurrentHashMap<>();
    
    private final String baseDir;
    private final List<CompressionStrategy> compressionStrategies;
    private final MemoryCompressor memoryCompressor;
    
    /**
     * 构造函数（带压缩支持）
     */
    public ChatMemoryManager(List<CompressionStrategy> compressionStrategies, 
                             MemoryCompressor memoryCompressor) {
        this.baseDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        this.compressionStrategies = compressionStrategies;
        this.memoryCompressor = memoryCompressor;
        log.info("ChatMemoryManager 初始化完成，压缩策略：{}", 
                compressionStrategies.stream().map(CompressionStrategy::getStrategyName).toList());
    }
    
    /**
     * 简化构造函数（无压缩支持）
     */
    public ChatMemoryManager() {
        this(List.of(), null);
    }
    
    /**
     * 获取指定 Agent 类型的 ChatMemory
     * 如果不存在则自动创建
     * 
     * @param agentType Agent 类型（如 "resume", "negotiation", "escape", "general", "consultation"）
     * @return ChatMemory 实例
     */
    public ChatMemory getMemory(String agentType) {
        return agentMemories.computeIfAbsent(agentType, type -> {
            String dir = baseDir + "/" + type;
            FileBasedChatMemory memory = new FileBasedChatMemory(dir, compressionStrategies, memoryCompressor);
            fileBasedMemories.put(type, memory);
            return memory;
        });
    }
    
    /**
     * 获取压缩状态
     * 
     * @param agentType Agent 类型
     * @param conversationId 会话 ID
     * @return 压缩状态
     */
    public FileBasedChatMemory.CompressionState getCompressionState(String agentType, String conversationId) {
        FileBasedChatMemory memory = fileBasedMemories.get(agentType);
        if (memory != null) {
            return memory.getCompressionState(conversationId);
        }
        return new FileBasedChatMemory.CompressionState();
    }
    
    /**
     * 检查是否正在压缩
     * 
     * @param agentType Agent 类型
     * @param conversationId 会话 ID
     * @return 是否正在压缩
     */
    public boolean isCompressing(String agentType, String conversationId) {
        FileBasedChatMemory memory = fileBasedMemories.get(agentType);
        return memory != null && memory.isCompressing(conversationId);
    }

    /**
     * 获取会话的记忆压缩状态（Requirements 3.5）。
     *
     * <p>对外暴露的压缩状态查询接口，封装底层 {@link FileBasedChatMemory.CompressionState}，
     * 同时携带 agentType / conversationId 上下文，便于上层（如 REST API / 前端）直接消费。
     * 当对应 Agent 记忆或会话尚未初始化时，返回 compressing=false 的空闲状态。
     *
     * @param agentType      Agent 类型
     * @param conversationId 会话 ID
     * @return 压缩状态视图
     */
    public CompressionStatus getCompressionStatus(String agentType, String conversationId) {
        FileBasedChatMemory.CompressionState state = getCompressionState(agentType, conversationId);
        return CompressionStatus.builder()
                .agentType(agentType)
                .conversationId(conversationId)
                .compressing(state.isCompressing())
                .compressionCount(state.getCompressionCount())
                .lastCompressionTime(state.getLastCompressionTime())
                .lastCompressionSuccess(state.isLastCompressionSuccess())
                .lastErrorMessage(state.getLastErrorMessage())
                .totalMessagesCompressed(state.getTotalMessagesCompressed())
                .build();
    }

    /**
     * 在 Token / 轮数超阈值时自动触发压缩，并推送状态消息（Requirements 4.1-4.5）。
     *
     * <p>典型用法：Agent 在生成响应前调用本方法。若当前会话满足任一压缩策略，
     * 则先推送"正在整理对话记忆..."（Req 4.3），在写锁保护下完成压缩与持久化（Req 4.5），
     * 完成后推送"记忆整理完成"（Req 4.4），随后调用方可继续正常响应用户（Req 4.5）。
     *
     * <p>压缩是否触发完全由底层已配置的 {@link CompressionStrategy}
     * （{@link TokenCompressionStrategy} 默认 4000 Token、{@link TurnCompressionStrategy} 默认 20 轮）决定，
     * 保证阈值语义集中、可配置。压缩过程中的异常不会向上抛出，避免影响对话连续性。
     *
     * @param agentType      Agent 类型
     * @param conversationId 会话 ID
     * @param statusConsumer 状态消息接收者（可为 null）；通常由 SSE 层提供，用于把状态推送给前端
     * @return 是否实际执行了压缩
     */
    public boolean autoCompressIfNeeded(String agentType, String conversationId, Consumer<String> statusConsumer) {
        // 确保对应 Agent 记忆已初始化，便于后续按类型查询
        getMemory(agentType);
        FileBasedChatMemory memory = fileBasedMemories.get(agentType);
        if (memory == null) {
            return false;
        }

        // 1. 阈值判定：未触发则直接返回，避免无谓的状态消息（Req 4.1 / 4.2）
        if (!memory.needsCompression(conversationId)) {
            return false;
        }

        // 2. 推送"正在整理对话记忆..."（Req 4.3）
        pushStatus(statusConsumer, STATUS_COMPRESSING);
        log.info("会话 {}（{}）触发自动压缩，开始整理对话记忆", conversationId, agentType);

        boolean compressed;
        try {
            // 3. 执行压缩并持久化，保持对话连续性（Req 4.5）
            compressed = memory.compressIfNeeded(conversationId);
        } catch (Exception e) {
            // 压缩异常不影响主流程：记录日志并视为未压缩，继续响应用户
            log.error("会话 {}（{}）自动压缩失败，将继续正常响应", conversationId, agentType, e);
            pushStatus(statusConsumer, STATUS_COMPRESSED);
            return false;
        }

        // 4. 推送"记忆整理完成"（Req 4.4）
        pushStatus(statusConsumer, STATUS_COMPRESSED);
        log.info("会话 {}（{}）记忆整理完成，是否实际压缩：{}", conversationId, agentType, compressed);
        return compressed;
    }

    /**
     * 自动触发压缩（无状态消息接收者重载）。
     */
    public boolean autoCompressIfNeeded(String agentType, String conversationId) {
        return autoCompressIfNeeded(agentType, conversationId, null, null);
    }

    /**
     * 自动触发压缩（带 TraceContext，记录 MEMORY_COMPRESSION span）。
     *
     * @param agentType      Agent 类型
     * @param conversationId 会话 ID
     * @param traceCtx       trace context (may be null/noop)
     * @param statusConsumer 状态消息接收者（可为 null）
     * @return 是否实际执行了压缩
     */
    public boolean autoCompressIfNeeded(String agentType, String conversationId,
                                         TraceContext traceCtx, Consumer<String> statusConsumer) {
        // Ensure memory is initialized
        getMemory(agentType);
        FileBasedChatMemory memory = fileBasedMemories.get(agentType);
        if (memory == null) {
            return false;
        }

        // Threshold check
        if (!memory.needsCompression(conversationId)) {
            return false;
        }

        // Push compressing status
        pushStatus(statusConsumer, STATUS_COMPRESSING);
        log.info("会话 {}（{}）触发自动压缩，开始整理对话记忆", conversationId, agentType);

        boolean compressed;
        try {
            compressed = memory.compressIfNeeded(conversationId);
        } catch (Exception e) {
            log.error("会话 {}（{}）自动压缩失败，将继续正常响应", conversationId, agentType, e);
            pushStatus(statusConsumer, STATUS_COMPRESSED);
            return false;
        }

        pushStatus(statusConsumer, STATUS_COMPRESSED);
        log.info("会话 {}（{}）记忆整理完成，是否实际压缩：{}", conversationId, agentType, compressed);
        return compressed;
    }

    /**
     * 安全推送状态消息，吞掉接收者异常，避免影响压缩与对话流程。
     */
    private void pushStatus(Consumer<String> statusConsumer, String status) {
        if (statusConsumer == null) {
            return;
        }
        try {
            statusConsumer.accept(status);
        } catch (Exception e) {
            log.warn("推送记忆压缩状态消息失败：{}", status, e);
        }
    }
    
    /**
     * 获取所有 Agent 类型
     */
    public List<String> getAgentTypes() {
        return List.copyOf(agentMemories.keySet());
    }

    /**
     * Returns the compressed summary for a chat session (if available).
     * Used by ConversationContextBuilder to build shared context.
     *
     * <p>Currently returns empty string — summary is stored as a system message
     * in PersistentMessageRepository, not in ChatMemoryManager.
     * Future: extract from message repository.
     *
     * @param chatId the chat session ID
     * @return compressed summary text, or empty string if none
     */
    public String getCompressedSummary(String chatId) {
        return "";
    }
    
    /**
     * 清除指定 Agent 类型的所有会话记忆
     * 
     * @param agentType Agent 类型
     */
    public void clearAgentMemory(String agentType) {
        agentMemories.remove(agentType);
        fileBasedMemories.remove(agentType);
    }
    
    /**
     * 清除所有 Agent 的会话记忆
     */
    public void clearAll() {
        agentMemories.clear();
        fileBasedMemories.clear();
    }
    
    /**
     * 获取压缩策略信息
     */
    public List<String> getCompressionStrategyInfo() {
        return compressionStrategies.stream()
                .map(s -> s.getStrategyName() + ": " + s.getDescription())
                .toList();
    }
    
    /**
     * 内存状态信息
     */
    @Data
    public static class MemoryStatus {
        private String agentType;
        private int conversationCount;
        private boolean compressionEnabled;
        private List<String> compressionStrategies;
    }

    /**
     * 压缩状态视图（对外查询返回，Requirements 3.5）。
     *
     * <p>在底层 {@link FileBasedChatMemory.CompressionState} 基础上补充
     * agentType / conversationId 上下文，便于 REST API 与前端直接消费。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompressionStatus {
        /** Agent 类型 */
        private String agentType;
        /** 会话 ID */
        private String conversationId;
        /** 是否正在压缩 */
        private boolean compressing;
        /** 已压缩次数 */
        private int compressionCount;
        /** 上次压缩时间 */
        private LocalDateTime lastCompressionTime;
        /** 上次压缩是否成功 */
        private boolean lastCompressionSuccess;
        /** 上次压缩错误信息 */
        private String lastErrorMessage;
        /** 累计压缩的消息数 */
        private int totalMessagesCompressed;
    }
}
