package com.yupi.yuaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于文件持久化的对话记忆（增强版）
 * 支持对话记忆压缩功能
 * 
 * 线程安全加固：Kryo 不是线程安全的，改用 ThreadLocal 让每个线程持有独立实例，
 * 避免多用户并发时序列化状态互相污染。
 * 
 * @author jsq
 */
@Slf4j
public class FileBasedChatMemory implements ChatMemory {

    private final String BASE_DIR;
    private final List<CompressionStrategy> compressionStrategies;
    private final MemoryCompressor memoryCompressor;

    // 压缩状态存储
    private final ConcurrentHashMap<String, CompressionState> compressionStates = new ConcurrentHashMap<>();

    // ThreadLocal 保证每个线程拥有独立的 Kryo 实例，彻底规避并发序列化错乱
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        return kryo;
    });

    // 读写锁
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 构造对象时，指定文件保存目录
     */
    public FileBasedChatMemory(String dir, List<CompressionStrategy> compressionStrategies, 
                               MemoryCompressor memoryCompressor) {
        this.BASE_DIR = dir;
        this.compressionStrategies = compressionStrategies;
        this.memoryCompressor = memoryCompressor;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    /**
     * 简化构造函数（无压缩功能）
     */
    public FileBasedChatMemory(String dir) {
        this(dir, List.of(), null);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        lock.writeLock().lock();
        try {
            List<Message> conversationMessages = getOrCreateConversation(conversationId);
            conversationMessages.addAll(messages);
            
            // 检查是否需要压缩
            if (shouldCompress(conversationId, conversationMessages)) {
                compressConversation(conversationId, conversationMessages);
            }
            
            saveConversation(conversationId, conversationMessages);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        lock.readLock().lock();
        try {
            return getOrCreateConversation(conversationId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clear(String conversationId) {
        lock.writeLock().lock();
        try {
            File file = getConversationFile(conversationId);
            if (file.exists()) {
                file.delete();
            }
            compressionStates.remove(conversationId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 检查是否需要压缩
     */
    private boolean shouldCompress(String conversationId, List<Message> messages) {
        if (compressionStrategies == null || compressionStrategies.isEmpty()) {
            return false;
        }

        for (CompressionStrategy strategy : compressionStrategies) {
            if (strategy.shouldCompress(messages)) {
                log.info("会话 {} 触发压缩策略：{}", conversationId, strategy.getStrategyName());
                return true;
            }
        }
        return false;
    }

    /**
     * 压缩对话。
     *
     * <p>将较早的历史对话压缩为结构化的关键信息摘要（系统消息），并保留最近 N 轮完整对话，
     * 随后由调用方 {@link #add} 通过 {@link #saveConversation} 将压缩结果持久化到文件，
     * 满足 Requirements 3.1（压缩为关键信息摘要）/ 3.2（保留最近 N 轮）/ 3.4（摘要作为系统消息加入上下文）。
     *
     * <p>实际的「分割 + LLM 摘要 + 摘要系统消息拼接」逻辑统一委托给
     * {@link MemoryCompressor#compressWithRetention(List, int)}，避免重复实现，
     * 并与 {@link MemoryCompressor} 的保留窗口配置保持一致。
     *
     * @param conversationId 会话 ID
     * @param messages       当前会话的全量消息（原地更新为压缩后的结果）
     */
    private void compressConversation(String conversationId, List<Message> messages) {
        if (memoryCompressor == null) {
            log.warn("MemoryCompressor 未配置，跳过压缩");
            return;
        }

        CompressionState state = compressionStates.computeIfAbsent(conversationId, k -> new CompressionState());
        state.setCompressing(true);
        state.setLastCompressionTime(LocalDateTime.now());

        try {
            int originalCount = messages.size();
            int retainTurns = resolveRetainTurns();

            // 委托 MemoryCompressor 完成「保留最近 N 轮 + 将较早对话压缩为摘要系统消息」
            List<Message> compressed = memoryCompressor.compressWithRetention(messages, retainTurns);

            // 消息量不足以触发实际压缩（保留窗口已覆盖全部消息），保持原样
            if (compressed.size() >= originalCount) {
                state.setCompressing(false);
                log.debug("会话 {} 消息量不足以压缩（{} 条），跳过", conversationId, originalCount);
                return;
            }

            // 原地替换为压缩结果：[摘要系统消息, 最近 N 轮完整对话...]
            // compressed = 1 条摘要 + 保留消息，故被压缩的旧消息数 = 原始数 - 保留数
            int retainedCount = compressed.size() - 1;
            int compressedCount = originalCount - retainedCount;
            messages.clear();
            messages.addAll(compressed);

            // 更新压缩状态
            state.setCompressing(false);
            state.setCompressionCount(state.getCompressionCount() + 1);
            state.setLastCompressionSuccess(true);
            state.setLastErrorMessage(null);
            state.setTotalMessagesCompressed(state.getTotalMessagesCompressed() + Math.max(0, compressedCount));

            log.info("会话 {} 压缩完成，压缩消息数：{}，保留消息数：{}",
                    conversationId, compressedCount, retainedCount);

        } catch (Exception e) {
            log.error("会话 {} 压缩失败", conversationId, e);
            state.setCompressing(false);
            state.setLastCompressionSuccess(false);
            state.setLastErrorMessage(e.getMessage());
        }
    }

    /**
     * 解析需要保留的最近对话轮数（N）。
     *
     * <p>优先采用已配置的 {@link TurnCompressionStrategy} 的保留轮数，
     * 未配置时回退到 {@link MemoryCompressor} 的默认保留轮数（默认 5），
     * 保证 Requirements 3.2 中「N 值可通过配置指定，默认值为 5」的一致性。
     */
    private int resolveRetainTurns() {
        if (compressionStrategies != null) {
            for (CompressionStrategy strategy : compressionStrategies) {
                if (strategy instanceof TurnCompressionStrategy turnStrategy) {
                    return turnStrategy.getRecentTurns();
                }
            }
        }
        return memoryCompressor.getRecentTurns();
    }

    /**
     * 检查指定会话当前是否满足压缩触发条件（按已配置策略判断），不执行压缩。
     *
     * <p>供 {@link ChatMemoryManager} 在生成响应前按 Token / 轮数阈值自动触发压缩时使用，
     * 对应 Requirements 4.1 / 4.2。
     *
     * @param conversationId 会话 ID
     * @return 满足任一压缩策略时返回 true
     */
    public boolean needsCompression(String conversationId) {
        lock.readLock().lock();
        try {
            List<Message> messages = getOrCreateConversation(conversationId);
            return shouldCompress(conversationId, messages);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 主动检查并压缩会话（供外部按策略触发）。
     *
     * <p>与 {@link #add} 内部的自动压缩共用同一套「策略判定 + 压缩 + 持久化」逻辑，
     * 在写锁保护下原地更新并持久化消息，保证对话连续性（Requirements 4.5）。
     * 仅当满足压缩策略且实际产生压缩（消息数减少）时返回 true。
     *
     * @param conversationId 会话 ID
     * @return 是否实际执行了压缩
     */
    public boolean compressIfNeeded(String conversationId) {
        lock.writeLock().lock();
        try {
            List<Message> messages = getOrCreateConversation(conversationId);
            if (!shouldCompress(conversationId, messages)) {
                return false;
            }
            int before = messages.size();
            compressConversation(conversationId, messages);
            saveConversation(conversationId, messages);
            return messages.size() < before;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取压缩状态
     */
    public CompressionState getCompressionState(String conversationId) {
        return compressionStates.getOrDefault(conversationId, new CompressionState());
    }

    /**
     * 检查是否正在压缩
     */
    public boolean isCompressing(String conversationId) {
        CompressionState state = compressionStates.get(conversationId);
        return state != null && state.isCompressing();
    }

    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                messages = kryoThreadLocal.get().readObject(input, ArrayList.class);
            } catch (Exception e) {
                // 读取失败（文件损坏或反序列化错误）：记录日志并返回空列表，避免污染后续对话
                log.error("读取会话 {} 的记忆文件失败，将以空记忆继续：{}", conversationId, e.getMessage(), e);
                messages = new ArrayList<>();
            }
        }
        return messages;
    }

    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            kryoThreadLocal.get().writeObject(output, messages);
        } catch (Exception e) {
            // 写入失败：记录日志，便于排查记忆丢失问题
            log.error("保存会话 {} 的记忆文件失败：{}", conversationId, e.getMessage(), e);
        }
    }

    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }

    /**
     * 压缩状态类
     */
    @Data
    public static class CompressionState {
        /**
         * 是否正在压缩
         */
        private boolean compressing;

        /**
         * 压缩次数
         */
        private int compressionCount;

        /**
         * 上次压缩时间
         */
        private LocalDateTime lastCompressionTime;

        /**
         * 上次压缩是否成功
         */
        private boolean lastCompressionSuccess;

        /**
         * 上次压缩错误信息
         */
        private String lastErrorMessage;

        /**
         * 总共压缩的消息数
         */
        private int totalMessagesCompressed;
    }
}
