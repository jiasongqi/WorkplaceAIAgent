package com.yupi.yuaiagent.agent.reflexion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reflexion Service — orchestrates failure recording and memory injection.
 *
 * <p>Integrates with the agent system to:</p>
 * <ul>
 *     <li>Record failures when they occur</li>
 *     <li>Inject relevant failure memories into prompts</li>
 *     <li>Learn from past mistakes to avoid repetition</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReflexionService {

    private final ReflexionMemory reflexionMemory;

    /**
     * Record a failure for future learning.
     *
     * @param userId     user ID
     * @param taskType   type of task that failed
     * @param error      error message
     * @param resolution how the error was resolved (nullable)
     */
    public void recordFailure(String userId, String taskType, String error, String resolution) {
        reflexionMemory.recordFailure(userId, taskType, error, resolution);
        log.info("[Reflexion] Recorded failure: user={}, taskType={}", userId, taskType);
    }

    /**
     * Get failure context for a task (for prompt injection).
     *
     * @param userId   user ID
     * @param taskType type of task
     * @return formatted failure context string
     */
    public String getFailureContext(String userId, String taskType) {
        List<ReflexionMemory.FailureMemory> memories = reflexionMemory.getRelevantMemories(userId, taskType);
        String context = reflexionMemory.formatForPrompt(memories);

        if (!context.isEmpty()) {
            log.debug("[Reflexion] Injecting {} failure memories for user={}, taskType={}",
                    memories.size(), userId, taskType);
        }

        return context;
    }

    /**
     * Check if there are relevant failure memories for a task.
     *
     * @param userId   user ID
     * @param taskType type of task
     * @return true if there are relevant memories
     */
    public boolean hasRelevantMemories(String userId, String taskType) {
        List<ReflexionMemory.FailureMemory> memories = reflexionMemory.getRelevantMemories(userId, taskType);
        return !memories.isEmpty();
    }

    /**
     * Get memory statistics.
     *
     * @return statistics map
     */
    public java.util.Map<String, Object> getStats() {
        return reflexionMemory.getStats();
    }

    /**
     * Clean up expired memories (call periodically).
     */
    public void cleanupExpired() {
        reflexionMemory.cleanupExpired();
    }
}
