package com.yupi.yuaiagent.guard;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Embedding 余弦相似度的循环检测器 — 核心改进二
 *
 * 滑动窗口 5 条，相似度阈值 0.88（更敏感地发现循环）。
 * 检测到循环后注入带有"上次失败原因"的针对性引导消息。
 */
@Slf4j
@Component
public class EmbeddingLoopDetector {

    private static final int WINDOW_SIZE = 5;
    private static final double SIMILARITY_THRESHOLD = 0.88;
    private static final int CONSECUTIVE_THRESHOLD = 2;

    private final EmbeddingModel embeddingModel;

    // Per-session state: sessionId -> sliding window of search records
    private final Map<String, Deque<SearchRecord>> sessionWindows = new ConcurrentHashMap<>();
    // Per-session consecutive loop counter
    private final Map<String, Integer> consecutiveLoopCounts = new ConcurrentHashMap<>();

    /**
     * 搜索记录：保存 embedding 和失败原因
     */
    private static class SearchRecord {
        final String query;
        final float[] embedding;
        String failedReason; // 回填失败原因

        SearchRecord(String query, float[] embedding) {
            this.query = query;
            this.embedding = embedding;
        }
    }

    public EmbeddingLoopDetector(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Check if the current tool call forms a loop pattern.
     * Inject guidance with failure context if consecutive loop threshold is met.
     *
     * @param sessionId   unique session identifier
     * @param toolName    name of the tool being called
     * @param arguments   tool arguments as a string
     * @param messageList the agent's message list for guidance injection
     */
    public boolean checkLoop(String sessionId, String toolName, String arguments, List<Message> messageList) {
        try {
            String signature = toolName + ":" + arguments;
            float[] embedding = computeEmbedding(signature);
            if (embedding == null) return false;

            Deque<SearchRecord> window = sessionWindows.computeIfAbsent(sessionId, k -> new ArrayDeque<>());

            // 查找最相似的历史记录
            SearchRecord matchedRecord = null;
            for (SearchRecord record : window) {
                if (cosineSimilarity(record.embedding, embedding) > SIMILARITY_THRESHOLD) {
                    matchedRecord = record;
                    break;
                }
            }

            if (matchedRecord != null) {
                int count = consecutiveLoopCounts.merge(sessionId, 1, Integer::sum);
                if (count >= CONSECUTIVE_THRESHOLD) {
                    // 构建带有上次失败原因的引导消息
                    String guidance = buildLoopGuidance(matchedRecord);
                    messageList.add(new UserMessage(guidance));
                    log.info("[LoopDetector] loop guidance injected for session {}, consecutive={}", sessionId, count);
                    // Maintain sliding window before returning
                    if (window.size() >= WINDOW_SIZE) {
                        window.pollFirst();
                    }
                    window.addLast(new SearchRecord(signature, embedding));
                    return true; // 循环检测命中
                }
            } else {
                consecutiveLoopCounts.put(sessionId, 0);
            }

            // Maintain sliding window
            if (window.size() >= WINDOW_SIZE) {
                window.pollFirst();
            }
            window.addLast(new SearchRecord(signature, embedding));
            return false; // 无循环
        } catch (Exception e) {
            log.warn("[EmbeddingLoopDetector] error during loop check, skipping: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 工具调用失败后，回填失败原因到最近的搜索记录。
     * 下次检测到循环时，可以告诉 LLM "上次为什么没找到"。
     *
     * @param sessionId unique session identifier
     * @param query     the tool call signature (toolName:arguments)
     * @param reason    failure reason description
     */
    public void recordFailure(String sessionId, String query, String reason) {
        try {
            Deque<SearchRecord> window = sessionWindows.get(sessionId);
            if (window == null) return;
            // 从后往前找，最近的匹配优先
            for (SearchRecord record : window) {
                if (record.query.equals(query)) {
                    record.failedReason = reason;
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("[EmbeddingLoopDetector] recordFailure failed: {}", e.getMessage());
        }
    }

    /**
     * Clear session state when execution ends.
     */
    public void clearSession(String sessionId) {
        sessionWindows.remove(sessionId);
        consecutiveLoopCounts.remove(sessionId);
    }

    /**
     * 构建带有失败原因的循环引导消息
     */
    private String buildLoopGuidance(SearchRecord matchedRecord) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Guard] 你已连续搜索类似内容，检测到重复调用模式。");
        if (matchedRecord.failedReason != null && !matchedRecord.failedReason.isBlank()) {
            sb.append("上次没找到的原因：").append(matchedRecord.failedReason).append("。");
        }
        sb.append("建议换个方向：试试换个搜索源、泛化关键词、或者用完全不同的工具来解决问题。");
        return sb.toString();
    }

    /**
     * Cosine similarity between two vectors. Package-private for testing.
     */
    static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    private float[] computeEmbedding(String text) {
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("[EmbeddingLoopDetector] embedding computation failed: {}", e.getMessage());
            return null;
        }
    }
}
