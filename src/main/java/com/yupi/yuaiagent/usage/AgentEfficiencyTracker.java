package com.yupi.yuaiagent.usage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Efficiency Tracker — tracks reasoning efficiency metrics per agent per user.
 *
 * <p>Metrics tracked:</p>
 * <ul>
 *     <li>Average steps per task (lower is better)</li>
 *     <li>Average tokens per task</li>
 *     <li>Average tool calls per task</li>
 *     <li>Task completion rate</li>
 *     <li>Average latency per task</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Component
public class AgentEfficiencyTracker {

    /** agentType → EfficiencyStats */
    private final Map<String, EfficiencyStats> globalStats = new ConcurrentHashMap<>();

    /** userId → agentType → EfficiencyStats */
    private final Map<String, Map<String, EfficiencyStats>> userStats = new ConcurrentHashMap<>();

    /**
     * Record a completed task execution.
     */
    public void recordTask(String userId, String agentType, int steps, int tokenCount,
                           int toolCallCount, long latencyMs, boolean completed) {
        // Global stats
        EfficiencyStats global = globalStats.computeIfAbsent(agentType, k -> new EfficiencyStats());
        global.record(steps, tokenCount, toolCallCount, latencyMs, completed);

        // User stats
        if (userId != null) {
            EfficiencyStats user = userStats
                    .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(agentType, k -> new EfficiencyStats());
            user.record(steps, tokenCount, toolCallCount, latencyMs, completed);
        }

        log.debug("[Efficiency] Recorded: user={}, agent={}, steps={}, tokens={}, tools={}, latency={}ms, completed={}",
                userId, agentType, steps, tokenCount, toolCallCount, latencyMs, completed);
    }

    /**
     * Get efficiency summary for an agent type.
     */
    public EfficiencySummary getSummary(String agentType) {
        EfficiencyStats stats = globalStats.get(agentType);
        if (stats == null) return EfficiencySummary.empty(agentType);
        return stats.toSummary(agentType);
    }

    /**
     * Get efficiency summary for a user + agent type.
     */
    public EfficiencySummary getUserSummary(String userId, String agentType) {
        Map<String, EfficiencyStats> userMap = userStats.get(userId);
        if (userMap == null) return EfficiencySummary.empty(agentType);
        EfficiencyStats stats = userMap.get(agentType);
        if (stats == null) return EfficiencySummary.empty(agentType);
        return stats.toSummary(agentType);
    }

    /**
     * Get all agent efficiency summaries.
     */
    public List<EfficiencySummary> getAllSummaries() {
        return globalStats.entrySet().stream()
                .map(e -> e.getValue().toSummary(e.getKey()))
                .toList();
    }

    /**
     * Efficiency statistics accumulator.
     */
    public static class EfficiencyStats {
        public long totalTasks;
        public long completedTasks;
        public long totalSteps;
        public long totalTokens;
        public long totalToolCalls;
        public long totalLatencyMs;

        public synchronized void record(int steps, int tokenCount, int toolCallCount,
                                        long latencyMs, boolean completed) {
            totalTasks++;
            if (completed) completedTasks++;
            totalSteps += steps;
            totalTokens += tokenCount;
            totalToolCalls += toolCallCount;
            totalLatencyMs += latencyMs;
        }

        public EfficiencySummary toSummary(String agentType) {
            return new EfficiencySummary(
                    agentType,
                    totalTasks,
                    completedTasks,
                    totalTasks == 0 ? 0.0 : (double) totalSteps / totalTasks,
                    totalTasks == 0 ? 0.0 : (double) totalTokens / totalTasks,
                    totalTasks == 0 ? 0.0 : (double) totalToolCalls / totalTasks,
                    totalTasks == 0 ? 0 : totalLatencyMs / totalTasks,
                    totalTasks == 0 ? 0.0 : (double) completedTasks / totalTasks
            );
        }
    }

    /**
     * Efficiency summary record.
     */
    public record EfficiencySummary(
            String agentType,
            long totalTasks,
            long completedTasks,
            double avgSteps,
            double avgTokens,
            double avgToolCalls,
            long avgLatencyMs,
            double completionRate
    ) {
        static EfficiencySummary empty(String agentType) {
            return new EfficiencySummary(agentType, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
