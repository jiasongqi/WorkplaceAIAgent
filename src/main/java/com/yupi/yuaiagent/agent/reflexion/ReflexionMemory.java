package com.yupi.yuaiagent.agent.reflexion;

import com.yupi.yuaiagent.repository.entity.ReflexionMemoryEntity;
import com.yupi.yuaiagent.repository.jpa.ReflexionMemoryJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Reflexion Memory — JPA persistence for failure trajectories.
 *
 * <p>Based on the Reflexion paper: agents learn from failures by storing
 * episodic memories of what went wrong and how it was resolved.</p>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
public class ReflexionMemory {

    private static final int MAX_MEMORIES_PER_USER = 50;

    private final ReflexionMemoryJpaRepository jpaRepo;

    @Value("${reflexion.expiration-days:7}")
    private int expirationDays;

    public ReflexionMemory(ReflexionMemoryJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    /**
     * Record a failure memory.
     */
    public void recordFailure(String userId, String taskType, String error, String resolution) {
        ReflexionMemoryEntity entity = new ReflexionMemoryEntity();
        entity.setUserId(userId);
        entity.setFailureType(taskType);
        entity.setError(error);
        entity.setResolution(resolution);
        entity.setExpiresAt(OffsetDateTime.now().plusDays(expirationDays));
        jpaRepo.save(entity);

        // Trim per-user to max size
        if (userId != null && !userId.isBlank()) {
            List<ReflexionMemoryEntity> userMemories = jpaRepo.findByUserId(userId);
            if (userMemories.size() > MAX_MEMORIES_PER_USER) {
                // Delete oldest
                List<ReflexionMemoryEntity> toDelete = userMemories.subList(0, userMemories.size() - MAX_MEMORIES_PER_USER);
                jpaRepo.deleteAll(toDelete);
            }
        }

        log.debug("[Reflexion] Recorded failure for user={}: taskType={}", userId, taskType);
    }

    /**
     * Get relevant failure memories for a task.
     */
    public List<FailureMemory> getRelevantMemories(String userId, String taskType) {
        List<ReflexionMemoryEntity> entities;

        if (userId != null && !userId.isBlank()) {
            entities = jpaRepo.findByUserIdOrUserIdIsNull(userId);
        } else {
            entities = jpaRepo.findByUserIdIsNull();
        }

        List<FailureMemory> result = entities.stream()
                .filter(e -> isRelevant(e, taskType))
                .sorted(Comparator.comparing((ReflexionMemoryEntity e) -> e.getCreatedAt()).reversed())
                .map(this::toDomain)
                .toList();

        return result.size() > 5 ? result.subList(0, 5) : result;
    }

    /**
     * Format failure memories for injection into prompt.
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
        jpaRepo.deleteByExpiresAtBefore(OffsetDateTime.now());
        log.info("[Reflexion] Cleaned up expired memories");
    }

    /**
     * Get memory statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = jpaRepo.count();
        stats.put("totalMemories", total);
        return stats;
    }

    private boolean isRelevant(ReflexionMemoryEntity entity, String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return true;
        }
        return (entity.getFailureType() != null && entity.getFailureType().equalsIgnoreCase(taskType)) ||
               (entity.getError() != null && entity.getError().contains(taskType));
    }

    private FailureMemory toDomain(ReflexionMemoryEntity e) {
        return new FailureMemory(
                e.getId() != null ? e.getId().toString() : null,
                e.getFailureType(),
                e.getError(),
                e.getResolution(),
                e.getCreatedAt() != null ? e.getCreatedAt().toInstant() : null
        );
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
