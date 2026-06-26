package com.yupi.yuaiagent.agent.reflexion;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflexion Memory — records failure trajectories to avoid repeated mistakes.
 *
 * <p>Based on the Reflexion paper: agents learn from failures by storing
 * episodic memories of what went wrong and how it was resolved.</p>
 *
 * <p>Features:</p>
 * <ul>
 *     <li>Records failure trajectories (task + error + resolution)</li>
 *     <li>Provides relevant failure memories for similar tasks</li>
 *     <li>Supports per-user and global failure tracking</li>
 *     <li>Automatic expiration of old memories</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
public class ReflexionMemory {

    /** Maximum memories per user */
    private static final int MAX_MEMORIES_PER_USER = 50;

    /** Memory expiration time (7 days) */
    private static final long EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L;

    /** userId → list of failure memories */
    private final ConcurrentHashMap<String, List<FailureMemory>> userMemories = new ConcurrentHashMap<>();

    /** Global failure memories (shared across users) */
    private final List<FailureMemory> globalMemories = Collections.synchronizedList(new ArrayList<>());

    /**
     * Record a failure memory.
     *
     * @param userId    user ID (nullable for global)
     * @param taskType  type of task that failed
     * @param error     error message
     * @param resolution how the error was resolved (nullable)
     */
    public void recordFailure(String userId, String taskType, String error, String resolution) {
        FailureMemory memory = new FailureMemory(
            UUID.randomUUID().toString(),
            taskType,
            error,
            resolution,
            Instant.now()
        );

        // Record per-user
        if (userId != null && !userId.isBlank()) {
            List<FailureMemory> memories = userMemories.computeIfAbsent(userId, k -> new ArrayList<>());
            memories.add(memory);

            // Trim to max size
            while (memories.size() > MAX_MEMORIES_PER_USER) {
                memories.remove(0);
            }

            log.debug("[Reflexion] Recorded failure for user={}: taskType={}, error={}",
                    userId, taskType, error.substring(0, Math.min(50, error.length())));
        }

        // Record global
        globalMemories.add(memory);
        while (globalMemories.size() > MAX_MEMORIES_PER_USER * 10) {
            globalMemories.remove(0);
        }
    }

    /**
     * Get relevant failure memories for a task.
     *
     * @param userId   user ID
     * @param taskType type of task
     * @return list of relevant failure memories (most recent first)
     */
    public List<FailureMemory> getRelevantMemories(String userId, String taskType) {
        List<FailureMemory> result = new ArrayList<>();

        // Get user-specific memories
        if (userId != null && !userId.isBlank()) {
            List<FailureMemory> userMem = userMemories.getOrDefault(userId, Collections.emptyList());
            for (FailureMemory mem : userMem) {
                if (isRelevant(mem, taskType)) {
                    result.add(mem);
                }
            }
        }

        // Get global memories
        for (FailureMemory mem : globalMemories) {
            if (isRelevant(mem, taskType)) {
                result.add(mem);
            }
        }

        // Sort by time (most recent first)
        result.sort(Comparator.comparing(FailureMemory::timestamp).reversed());

        // Return top 5
        return result.size() > 5 ? result.subList(0, 5) : result;
    }

    /**
     * Format failure memories for injection into prompt.
     *
     * @param memories list of failure memories
     * @return formatted string for prompt injection
     */
    public String formatForPrompt(List<FailureMemory> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【历史失败经验】\n");
        sb.append("请注意避免以下已知问题：\n");

        for (int i = 0; i < memories.size(); i++) {
            FailureMemory mem = memories.get(i);
            sb.append(String.format("%d. 任务类型: %s\n", i + 1, mem.taskType()));
            sb.append(String.format("   错误: %s\n", mem.error()));
            if (mem.resolution() != null && !mem.resolution().isBlank()) {
                sb.append(String.format("   解决方案: %s\n", mem.resolution()));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Clean up expired memories.
     */
    public void cleanupExpired() {
        Instant cutoff = Instant.now().minusMillis(EXPIRATION_MS);
        int cleaned = 0;

        // Clean user memories
        for (List<FailureMemory> memories : userMemories.values()) {
            cleaned += memories.removeIf(mem -> mem.timestamp().isBefore(cutoff)) ? 1 : 0;
        }

        // Clean global memories
        cleaned += globalMemories.removeIf(mem -> mem.timestamp().isBefore(cutoff)) ? 1 : 0;

        if (cleaned > 0) {
            log.info("[Reflexion] Cleaned up {} expired memories", cleaned);
        }
    }

    /**
     * Get memory statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("userCount", userMemories.size());
        stats.put("globalCount", globalMemories.size());

        int totalUserMemories = userMemories.values().stream()
                .mapToInt(List::size)
                .sum();
        stats.put("totalUserMemories", totalUserMemories);

        return stats;
    }

    /**
     * Check if a memory is relevant to a task type.
     */
    private boolean isRelevant(FailureMemory memory, String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return true; // Return all if no filter
        }
        return memory.taskType().equalsIgnoreCase(taskType) ||
               memory.error().contains(taskType);
    }

    /**
     * Failure memory record.
     */
    public record FailureMemory(
        String id,
        String taskType,
        String error,
        String resolution,
        Instant timestamp
    ) {}
}
