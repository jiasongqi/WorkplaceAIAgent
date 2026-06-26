package com.yupi.yuaiagent.memory.procedural;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Procedural Memory — records tool call patterns and success/failure rates.
 *
 * <p>Inspired by human procedural memory (skills and habits), this component
 * tracks which tools the agent uses, how often they succeed, and what patterns
 * emerge. Over time, the agent can learn which tools work best for which intents.</p>
 *
 * <p>Data collected per tool call:</p>
 * <ul>
 *     <li>Tool name and intent context</li>
 *     <li>Success/failure and latency</li>
 *     <li>Argument patterns (common parameter values)</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Component
public class ProceduralMemory {

    /** userId → toolName → ToolUsageStats */
    private final Map<String, Map<String, ToolUsageStats>> userToolStats = new ConcurrentHashMap<>();

    /** Global tool usage (no user context) */
    private final Map<String, ToolUsageStats> globalToolStats = new ConcurrentHashMap<>();

    /**
     * Record a tool call event.
     *
     * @param userId    user ID (nullable for anonymous)
     * @param toolName  tool name
     * @param intent    intent context (e.g., "RESUME", "NEGOTIATION")
     * @param success   whether the call succeeded
     * @param latencyMs latency in milliseconds
     */
    public void record(String userId, String toolName, String intent, boolean success, long latencyMs) {
        ToolUsageStats stats = getOrCreateStats(userId, toolName);
        stats.totalCalls++;
        if (success) stats.successCalls++;
        else stats.failureCalls++;
        stats.totalLatencyMs += latencyMs;
        stats.lastUsedAt = LocalDateTime.now();

        // Track intent context
        stats.intentCounts.merge(intent, 1, Integer::sum);

        // Track global stats
        ToolUsageStats global = globalToolStats.computeIfAbsent(toolName, k -> new ToolUsageStats());
        global.totalCalls++;
        if (success) global.successCalls++;
        else global.failureCalls++;
        global.totalLatencyMs += latencyMs;
        global.lastUsedAt = LocalDateTime.now();

        log.debug("[ProceduralMemory] Recorded: user={}, tool={}, intent={}, success={}, latency={}ms",
                userId, toolName, intent, success, latencyMs);
    }

    /**
     * Get success rate for a tool (user-specific or global).
     */
    public double getSuccessRate(String userId, String toolName) {
        ToolUsageStats stats = userId != null
                ? userToolStats.getOrDefault(userId, Map.of()).get(toolName)
                : globalToolStats.get(toolName);
        if (stats == null || stats.totalCalls == 0) return -1.0;
        return (double) stats.successCalls / stats.totalCalls;
    }

    /**
     * Get average latency for a tool.
     */
    public long getAvgLatency(String userId, String toolName) {
        ToolUsageStats stats = userId != null
                ? userToolStats.getOrDefault(userId, Map.of()).get(toolName)
                : globalToolStats.get(toolName);
        if (stats == null || stats.totalCalls == 0) return -1;
        return stats.totalLatencyMs / stats.totalCalls;
    }

    /**
     * Get top N most used tools for a user.
     */
    public List<ToolRank> getTopTools(String userId, int n) {
        Map<String, ToolUsageStats> stats = userToolStats.getOrDefault(userId, Map.of());
        return stats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().totalCalls, a.getValue().totalCalls))
                .limit(n)
                .map(e -> new ToolRank(e.getKey(), e.getValue().totalCalls, e.getValue().getSuccessRate()))
                .collect(Collectors.toList());
    }

    /**
     * Get recommended tool for an intent based on historical success rate.
     */
    public Optional<String> recommendTool(String userId, String intent) {
        Map<String, ToolUsageStats> stats = userToolStats.getOrDefault(userId, Map.of());
        return stats.entrySet().stream()
                .filter(e -> e.getValue().intentCounts.containsKey(intent))
                .filter(e -> e.getValue().getSuccessRate() > 0.7)
                .max(Comparator.comparingDouble(e -> e.getValue().getSuccessRate()))
                .map(Map.Entry::getKey);
    }

    /**
     * Get total tool call count for a user.
     */
    public long getTotalCalls(String userId) {
        Map<String, ToolUsageStats> stats = userToolStats.getOrDefault(userId, Map.of());
        return stats.values().stream().mapToLong(s -> s.totalCalls).sum();
    }

    private ToolUsageStats getOrCreateStats(String userId, String toolName) {
        if (userId == null) userId = "_anonymous";
        return userToolStats
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(toolName, k -> new ToolUsageStats());
    }

    /**
     * Tool usage statistics.
     */
    public static class ToolUsageStats {
        public long totalCalls;
        public long successCalls;
        public long failureCalls;
        public long totalLatencyMs;
        public LocalDateTime lastUsedAt;
        public Map<String, Integer> intentCounts = new ConcurrentHashMap<>();

        public double getSuccessRate() {
            return totalCalls == 0 ? 0.0 : (double) successCalls / totalCalls;
        }

        public long getAvgLatencyMs() {
            return totalCalls == 0 ? 0 : totalLatencyMs / totalCalls;
        }
    }

    /**
     * Tool ranking record.
     */
    public record ToolRank(String toolName, long totalCalls, double successRate) {}
}
