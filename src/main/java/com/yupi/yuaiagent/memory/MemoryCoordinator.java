package com.yupi.yuaiagent.memory;

import com.yupi.yuaiagent.memory.experience.ExperienceDocument;
import com.yupi.yuaiagent.memory.experience.ExperienceStoreLayer;
import com.yupi.yuaiagent.memory.extraction.ExtractionPipeline;
import com.yupi.yuaiagent.memory.fact.FactStoreLayer;
import com.yupi.yuaiagent.memory.sliding.SlidingWindowLayer;
import com.yupi.yuaiagent.memory.summary.SummaryLayer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Memory Coordinator — 分层记忆系统的唯一入口。
 *
 * <p>职责：
 * <ul>
 *   <li>提供 {@link #assembleContext(String, String, String)} 作为统一的上下文组装入口</li>
 *   <li>查询四层记忆（L1 滑动窗口、L2 事实、L3 摘要、L4 经验）</li>
 *   <li>应用 Token 预算分配与截断</li>
 *   <li>格式化并返回组合后的 {@link SystemMessage}</li>
 *   <li>在对话完成后触发 {@link ExtractionPipeline} 异步提取</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>Task 8.1：基础骨架，顺序查询各层</li>
 *   <li>Task 8.2：将改为 CompletableFuture 并行查询 + 超时回退</li>
 *   <li>Task 8.3：完善上下文格式化和 Token 预算裁剪</li>
 *   <li>Task 8.4：实现 onTurnCompleted 触发提取管道</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "memory.coordinator.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryCoordinator {

    private final SlidingWindowLayer slidingWindow;
    private final FactStoreLayer factStore;
    private final SummaryLayer summaryLayer;
    private final ExperienceStoreLayer experienceStore;
    private final TokenBudgetAllocator budgetAllocator;
    private final ExtractionPipeline extractionPipeline;
    private final ExperienceQueryBuilder experienceQueryBuilder;
    private final Executor memoryQueryExecutor;

    private final int timeoutMs;
    private final int totalTokenBudget;

    /**
     * "Last known good" 缓存：per userId per layer。
     * Key 格式为 "{userId}:{MemoryLayer.name()}"，Value 为该层上次成功返回的非空内容。
     */
    private final ConcurrentHashMap<String, String> layerCache = new ConcurrentHashMap<>();

    /** Timestamps for each layerCache entry, used for TTL-based eviction. */
    private final ConcurrentHashMap<String, Long> layerCacheTimestamps = new ConcurrentHashMap<>();

    /** Cache entry time-to-live: 5 minutes. */
    private static final long CACHE_TTL_MS = 300_000;

    /** Maximum number of entries before eviction is triggered. */
    private static final int MAX_CACHE_SIZE = 10_000;

    public MemoryCoordinator(
            SlidingWindowLayer slidingWindow,
            FactStoreLayer factStore,
            SummaryLayer summaryLayer,
            ExperienceStoreLayer experienceStore,
            TokenBudgetAllocator budgetAllocator,
            ExtractionPipeline extractionPipeline,
            ExperienceQueryBuilder experienceQueryBuilder,
            @Qualifier("memoryQueryExecutor") Executor memoryQueryExecutor,
            @Value("${memory.coordinator.timeout-ms:2000}") int timeoutMs,
            @Value("${memory.coordinator.total-token-budget:6000}") int totalTokenBudget) {
        this.slidingWindow = slidingWindow;
        this.factStore = factStore;
        this.summaryLayer = summaryLayer;
        this.experienceStore = experienceStore;
        this.budgetAllocator = budgetAllocator;
        this.extractionPipeline = extractionPipeline;
        this.experienceQueryBuilder = experienceQueryBuilder;
        this.memoryQueryExecutor = memoryQueryExecutor;
        this.timeoutMs = timeoutMs;
        this.totalTokenBudget = totalTokenBudget;
    }

    /**
     * 组装上下文 — 并行查询四层记忆并按 Token 预算组装为 SystemMessage。
     *
     * <p>使用 {@link CompletableFuture#allOf} 并行查询各层，超时后使用缓存的
     * "last known good" 值作为回退。单层失败不影响其他层的正常贡献。
     *
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @param agentType      智能体类型（用于 L1 滑动窗口检索）
     * @return 包含各层记忆的 SystemMessage
     */
    public SystemMessage assembleContext(String userId, String conversationId, String agentType) {
        return assembleContext(userId, conversationId, agentType, null);
    }

    /**
     * 组装上下文 — 可选传入当前用户消息，用于 L4 经验层语义检索（Ch5 L2→L3 query）。
     */
    public SystemMessage assembleContext(String userId, String conversationId, String agentType,
                                         String currentUserMessage) {
        // Evict expired cache entries when size exceeds threshold
        if (layerCache.size() > MAX_CACHE_SIZE / 2) {
            evictExpiredCacheEntries();
        }

        log.info("开始组装上下文: userId={}, conversationId={}, agentType={}", userId, conversationId, agentType);

        // 1. 获取各层 Token 预算分配
        Map<MemoryLayer, Integer> budgets = budgetAllocator.allocate(totalTokenBudget);
        log.debug("Token 预算分配: {}", budgets);

        // 2. 并行查询各层（使用专用线程池，避免占用 ForkJoinPool.commonPool）
        CompletableFuture<String> slidingFuture = CompletableFuture.supplyAsync(
                () -> querySlidingWindow(conversationId, agentType, budgets.get(MemoryLayer.SLIDING_WINDOW)),
                memoryQueryExecutor
        ).exceptionally(ex -> handleLayerFailure(userId, MemoryLayer.SLIDING_WINDOW, ex));

        CompletableFuture<String> factFuture = CompletableFuture.supplyAsync(
                () -> queryFactStore(userId, budgets.get(MemoryLayer.FACT_STORE)),
                memoryQueryExecutor
        ).exceptionally(ex -> handleLayerFailure(userId, MemoryLayer.FACT_STORE, ex));

        CompletableFuture<String> summaryFuture = CompletableFuture.supplyAsync(
                () -> querySummaryLayer(userId, budgets.get(MemoryLayer.SUMMARY)),
                memoryQueryExecutor
        ).exceptionally(ex -> handleLayerFailure(userId, MemoryLayer.SUMMARY, ex));

        final String experienceQuery = experienceQueryBuilder.build(userId, currentUserMessage);
        CompletableFuture<String> experienceFuture = CompletableFuture.supplyAsync(
                () -> queryExperienceStore(userId, experienceQuery, budgets.get(MemoryLayer.EXPERIENCE)),
                memoryQueryExecutor
        ).exceptionally(ex -> handleLayerFailure(userId, MemoryLayer.EXPERIENCE, ex));

        // 3. 等待所有层完成（或超时）
        try {
            CompletableFuture.allOf(slidingFuture, factFuture, summaryFuture, experienceFuture)
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .join();
        } catch (Exception e) {
            // allOf 超时或异常 — 个别 future 可能未完成
            log.warn("并行查询超时或异常 (timeout={}ms): {}", timeoutMs, e.getMessage());
        }

        // 4. 收集各层结果（使用 getNow 避免再次阻塞，超时的 future 用 fallback）
        Map<MemoryLayer, String> layerContributions = new EnumMap<>(MemoryLayer.class);

        String slidingContent = getResultOrFallback(slidingFuture, userId, MemoryLayer.SLIDING_WINDOW);
        layerContributions.put(MemoryLayer.SLIDING_WINDOW, slidingContent);

        String factContent = getResultOrFallback(factFuture, userId, MemoryLayer.FACT_STORE);
        layerContributions.put(MemoryLayer.FACT_STORE, factContent);

        String summaryContent = getResultOrFallback(summaryFuture, userId, MemoryLayer.SUMMARY);
        layerContributions.put(MemoryLayer.SUMMARY, summaryContent);

        String experienceContent = getResultOrFallback(experienceFuture, userId, MemoryLayer.EXPERIENCE);
        layerContributions.put(MemoryLayer.EXPERIENCE, experienceContent);

        // 5. 更新缓存（非空结果）
        cacheLayerResult(userId, MemoryLayer.SLIDING_WINDOW, slidingContent);
        cacheLayerResult(userId, MemoryLayer.FACT_STORE, factContent);
        cacheLayerResult(userId, MemoryLayer.SUMMARY, summaryContent);
        cacheLayerResult(userId, MemoryLayer.EXPERIENCE, experienceContent);

        // 6. 格式化为最终上下文文本
        String formattedContext = formatContext(layerContributions);

        // 7. 计算总 Token 估算
        int totalTokens = budgetAllocator.estimateTokens(formattedContext);
        log.info("上下文组装完成: userId={}, totalTokens={}", userId, totalTokens);

        return new SystemMessage(formattedContext);
    }

    /**
     * 对话完成后的钩子 — 触发 ExtractionPipeline 异步提取（使用默认 agentType "general"）。
     *
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @param messages       本轮对话消息列表
     */
    public void onTurnCompleted(String userId, String conversationId, List<Message> messages) {
        onTurnCompleted(userId, conversationId, "general", messages);
    }

    /**
     * 对话完成后的钩子 — 触发 ExtractionPipeline 异步提取。
     *
     * <p>验证输入参数后，将消息委托给 {@link ExtractionPipeline#processAsync} 进行异步处理。
     * 此方法永不抛出异常，所有错误内部捕获并记录日志。
     *
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @param agentType      智能体类型
     * @param messages       本轮对话消息列表
     */
    public void onTurnCompleted(String userId, String conversationId, String agentType, List<Message> messages) {
        try {
            // 输入验证：任何关键参数为空则跳过提取
            if (userId == null || userId.isBlank()) {
                log.debug("onTurnCompleted skipped: userId is null or blank");
                return;
            }
            if (conversationId == null || conversationId.isBlank()) {
                log.debug("onTurnCompleted skipped: conversationId is null or blank");
                return;
            }
            if (messages == null || messages.isEmpty()) {
                log.debug("onTurnCompleted skipped: messages is null or empty, userId={}", userId);
                return;
            }

            // agentType 为空时使用默认值
            String resolvedAgentType = (agentType != null && !agentType.isBlank()) ? agentType : "general";

            log.info("onTurnCompleted triggered: userId={}, conversationId={}, agentType={}, messageCount={}",
                    userId, conversationId, resolvedAgentType, messages.size());

            // 委托给 ExtractionPipeline 异步处理
            extractionPipeline.processAsync(userId, conversationId, resolvedAgentType, messages);

        } catch (Exception e) {
            log.error("onTurnCompleted error: userId={}, conversationId={}, error={}",
                    userId, conversationId, e.getMessage(), e);
        }
    }

    // ========== 并行查询辅助方法 ==========

    /**
     * 处理层查询失败 — 返回缓存的 "last known good" 值或空字符串。
     *
     * @param userId 用户 ID
     * @param layer  失败的层
     * @param ex     异常
     * @return 缓存的上次成功值，若无缓存则返回空字符串
     */
    private String handleLayerFailure(String userId, MemoryLayer layer, Throwable ex) {
        if (ex instanceof TimeoutException || (ex.getCause() != null && ex.getCause() instanceof TimeoutException)) {
            log.warn("{} 层查询超时: userId={}", layer, userId);
        } else {
            log.error("{} 层查询失败: userId={}, error={}", layer, userId, ex.getMessage(), ex);
        }
        // 尝试使用缓存的上次成功值
        String cached = layerCache.get(buildCacheKey(userId, layer));
        if (cached != null) {
            log.info("{} 层使用缓存回退值: userId={}", layer, userId);
            return cached;
        }
        return "";
    }

    /**
     * 从 CompletableFuture 获取结果，如果 future 未完成或异常则使用缓存回退。
     */
    private String getResultOrFallback(CompletableFuture<String> future, String userId, MemoryLayer layer) {
        try {
            // getNow 不阻塞 — 如果 future 已完成则取值，否则使用 fallback
            return future.getNow(getCachedOrEmpty(userId, layer));
        } catch (Exception e) {
            log.warn("{} 层结果获取失败，使用回退: userId={}, error={}", layer, userId, e.getMessage());
            return getCachedOrEmpty(userId, layer);
        }
    }

    /**
     * 获取缓存值或空字符串。
     */
    private String getCachedOrEmpty(String userId, MemoryLayer layer) {
        String cached = layerCache.get(buildCacheKey(userId, layer));
        return cached != null ? cached : "";
    }

    /**
     * 缓存层结果（仅缓存非空值），同时记录时间戳用于 TTL 驱逐。
     */
    private void cacheLayerResult(String userId, MemoryLayer layer, String content) {
        if (content != null && !content.isBlank()) {
            String key = buildCacheKey(userId, layer);
            layerCache.put(key, content);
            layerCacheTimestamps.put(key, System.currentTimeMillis());
        }
    }

    /**
     * 构建缓存 key："{userId}:{layerName}"。
     */
    private String buildCacheKey(String userId, MemoryLayer layer) {
        return userId + ":" + layer.name();
    }

    /**
     * Evict expired cache entries based on TTL. Also removes orphaned entries
     * from layerCache that no longer have a corresponding timestamp.
     */
    private void evictExpiredCacheEntries() {
        long now = System.currentTimeMillis();
        int beforeSize = layerCache.size();

        // Remove expired timestamps
        layerCacheTimestamps.entrySet().removeIf(e -> now - e.getValue() > CACHE_TTL_MS);

        // Remove cache entries that no longer have a timestamp (expired or orphaned)
        layerCache.keySet().removeIf(k -> !layerCacheTimestamps.containsKey(k));

        int evicted = beforeSize - layerCache.size();
        if (evicted > 0) {
            log.info("Evicted {} expired layerCache entries, remaining: {}", evicted, layerCache.size());
        }
    }

    // ========== 各层查询方法 ==========

    /**
     * 查询 L1 滑动窗口层。
     */
    private String querySlidingWindow(String conversationId, String agentType, int tokenBudget) {
        try {
            return slidingWindow.formatForContext(conversationId, agentType, tokenBudget);
        } catch (Exception e) {
            log.error("L1 滑动窗口查询失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            return "";
        }
    }

    /**
     * 查询 L2 事实存储层。
     */
    private String queryFactStore(String userId, int tokenBudget) {
        try {
            return factStore.formatForContext(userId, tokenBudget);
        } catch (Exception e) {
            log.error("L2 事实存储查询失败: userId={}, error={}", userId, e.getMessage(), e);
            return "";
        }
    }

    /**
     * 查询 L3 摘要层。
     */
    private String querySummaryLayer(String userId, int tokenBudget) {
        try {
            return summaryLayer.getRecentSummaries(userId, tokenBudget);
        } catch (Exception e) {
            log.error("L3 摘要层查询失败: userId={}, error={}", userId, e.getMessage(), e);
            return "";
        }
    }

    /**
     * 查询 L4 经验存储层 — query 由 {@link ExperienceQueryBuilder} 从当前消息 + L3 摘要构造。
     */
    private String queryExperienceStore(String userId, String experienceQuery, int tokenBudget) {
        try {
            List<ExperienceDocument> experiences = experienceStore.searchSimilar(userId, experienceQuery);
            if (experiences == null || experiences.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【历史经验】\n");
            for (ExperienceDocument doc : experiences) {
                sb.append("- ");
                if (doc.outcome() != null && !doc.outcome().isEmpty()) {
                    sb.append("[").append(doc.outcome()).append("] ");
                }
                sb.append(doc.content()).append("\n");
            }

            String formatted = sb.toString();
            return budgetAllocator.truncateToTokens(formatted, tokenBudget);
        } catch (Exception e) {
            log.error("L4 经验存储查询失败: userId={}, error={}", userId, e.getMessage(), e);
            return "";
        }
    }

    // ========== 上下文格式化 ==========

    /**
     * 将各层贡献格式化为最终上下文文本（带 section headers）。
     */
    private String formatContext(Map<MemoryLayer, String> layerContributions) {
        StringBuilder sb = new StringBuilder();

        // 按优先级顺序组装：L2(事实) → L3(摘要) → L4(经验) → L1(对话)
        // L2 用户事实放最前面，因为是稳定的身份信息
        appendLayerContent(sb, layerContributions.get(MemoryLayer.FACT_STORE));

        // L3 摘要
        appendLayerContent(sb, layerContributions.get(MemoryLayer.SUMMARY));

        // L4 经验
        appendLayerContent(sb, layerContributions.get(MemoryLayer.EXPERIENCE));

        // L1 滑动窗口（最近对话放最后，距离 LLM 注意力最近）
        String slidingContent = layerContributions.get(MemoryLayer.SLIDING_WINDOW);
        if (slidingContent != null && !slidingContent.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("【近期对话】\n").append(slidingContent);
        }

        return sb.toString();
    }

    /**
     * 将非空层内容追加到 StringBuilder。
     */
    private void appendLayerContent(StringBuilder sb, String content) {
        if (content != null && !content.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(content);
        }
    }
}
