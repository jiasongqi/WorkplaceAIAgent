package com.yupi.yuaiagent.memory.fact;

import com.yupi.yuaiagent.memory.TokenBudgetAllocator;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.profile.model.CommunicationPreference;
import com.yupi.yuaiagent.profile.model.UserProfile;
import com.yupi.yuaiagent.repository.entity.UserFactEntity;
import com.yupi.yuaiagent.repository.jpa.UserFactJpaRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fact Store Layer (L2) — JPA 持久化实现。
 *
 * <p>以 userId 为粒度存储用户长期事实（身份、偏好、目标等键值对），
 * 支持 upsert 语义（同 key 覆写）和变更日志。
 *
 * <p>已从 ConcurrentHashMap + JSON 文件迁移到 PostgreSQL。
 */
@Slf4j
@Repository
public class FactStoreLayer {

    private static final String MIGRATION_MARKER_KEY = "__migrated_from_profile__";

    private final UserFactJpaRepository jpaRepo;
    private final TokenBudgetAllocator tokenBudgetAllocator;
    private final UserProfileService userProfileService;

    public FactStoreLayer(
            UserFactJpaRepository jpaRepo,
            TokenBudgetAllocator tokenBudgetAllocator,
            @Lazy UserProfileService userProfileService) {
        this.jpaRepo = jpaRepo;
        this.tokenBudgetAllocator = tokenBudgetAllocator;
        this.userProfileService = userProfileService;
    }

    @PostConstruct
    void init() {
        log.info("FactStoreLayer 初始化完成（JPA 模式）");
    }

    /**
     * 获取指定用户的所有事实。
     */
    public List<FactEntry> getFacts(String userId) {
        if (!hasFacts(userId) && !isMigrated(userId)) {
            performLazyMigration(userId);
        }

        return jpaRepo.findByUserId(userId).stream()
                .filter(e -> !MIGRATION_MARKER_KEY.equals(e.getFactKey()))
                .map(this::toDomain)
                .toList();
    }

    /**
     * 插入或更新一条用户事实。
     */
    public void upsert(String userId, FactEntry entry) {
        if (userId == null || entry == null || entry.key() == null) {
            return;
        }

        Optional<UserFactEntity> existingOpt = jpaRepo.findByUserIdAndFactKey(userId, entry.key());

        if (existingOpt.isPresent()) {
            UserFactEntity existing = existingOpt.get();
            // Idempotent: skip if value unchanged
            if (Objects.equals(existing.getFactValue(), entry.value())) {
                log.debug("用户 {} 事实 [{}] 值未变化，跳过 upsert", userId, entry.key());
                return;
            }
            // Update
            existing.setFactValue(entry.value());
            existing.setCategory(entry.category() != null ? entry.category().name() : null);
            existing.setSource(entry.sourceConversationId());
            jpaRepo.save(existing);
            log.info("用户 {} 事实 [{}] 已更新: '{}' → '{}'",
                    userId, entry.key(), existing.getFactValue(), entry.value());
        } else {
            // Insert
            UserFactEntity entity = new UserFactEntity();
            entity.setUserId(userId);
            entity.setFactKey(entry.key());
            entity.setFactValue(entry.value());
            entity.setCategory(entry.category() != null ? entry.category().name() : null);
            entity.setSource(entry.sourceConversationId());
            jpaRepo.save(entity);
            log.info("用户 {} 新增事实 [{}] = '{}'", userId, entry.key(), entry.value());
        }
    }

    /**
     * 批量 upsert 多条事实。
     */
    public void batchUpsert(String userId, List<FactEntry> entries) {
        if (userId == null || entries == null || entries.isEmpty()) {
            return;
        }
        for (FactEntry entry : entries) {
            upsert(userId, entry);
        }
    }

    /**
     * 将现有 UserProfile 迁移为 FactEntry 记录。
     */
    public void migrateFromProfile(String userId, UserProfile profile) {
        if (userId == null || profile == null) {
            return;
        }

        Instant now = Instant.now();
        String migrationSource = "profile_migration";
        List<FactEntry> migrated = new ArrayList<>();

        CommunicationPreference commPref = profile.getCommunicationPreference();
        if (commPref != null) {
            migrated.add(new FactEntry("communication_preference",
                    commPref.getDescription(), FactCategory.PREFERENCES, migrationSource, now));
        }

        String tone = profile.getTonePreference();
        if (tone != null && !tone.isBlank()) {
            migrated.add(new FactEntry("tone_preference",
                    tone, FactCategory.PREFERENCES, migrationSource, now));
        }

        List<String> focusAreas = profile.getFocusAreas();
        if (focusAreas != null && !focusAreas.isEmpty()) {
            migrated.add(new FactEntry("focus_areas",
                    String.join(",", focusAreas), FactCategory.CAREER, migrationSource, now));
        }

        String background = profile.getKnownBackground();
        if (background != null && !background.isBlank()) {
            migrated.add(new FactEntry("background",
                    background, FactCategory.IDENTITY, migrationSource, now));
        }

        List<String> demands = profile.getHistoricalDemands();
        if (demands != null && !demands.isEmpty()) {
            migrated.add(new FactEntry("historical_demands",
                    String.join(",", demands), FactCategory.GOALS, migrationSource, now));
        }

        batchUpsert(userId, migrated);

        upsert(userId, new FactEntry(MIGRATION_MARKER_KEY,
                "true", FactCategory.IDENTITY, migrationSource, now));

        log.info("用户 {} 画像迁移完成，共迁移 {} 条事实", userId, migrated.size());
    }

    /**
     * 格式化用户事实用于上下文注入，遵循 token 预算限制。
     */
    public String formatForContext(String userId, int tokenBudget) {
        List<FactEntry> facts = getFacts(userId);
        if (facts.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【用户事实】\n");

        Map<FactCategory, List<FactEntry>> grouped = facts.stream()
                .collect(Collectors.groupingBy(FactEntry::category));

        for (FactCategory category : FactCategory.values()) {
            List<FactEntry> categoryFacts = grouped.get(category);
            if (categoryFacts == null || categoryFacts.isEmpty()) {
                continue;
            }
            sb.append(String.format("- %s: ", category.getDisplayName()));
            sb.append(categoryFacts.stream()
                    .map(f -> f.key() + "=" + f.value())
                    .collect(Collectors.joining("; ")));
            sb.append("\n");
        }

        String formatted = sb.toString();
        return tokenBudgetAllocator.truncateToTokens(formatted, tokenBudget);
    }

    /**
     * 检查用户是否已完成画像迁移。
     */
    public boolean isMigrated(String userId) {
        return jpaRepo.findByUserIdAndFactKey(userId, MIGRATION_MARKER_KEY).isPresent();
    }

    // ========== Legacy compatibility ==========

    public void cleanupStaleLocks() {
        // No-op: JPA doesn't need in-memory locks
    }

    public int getLockCount() {
        return 0; // No in-memory locks in JPA mode
    }

    // ========== Internal ==========

    private boolean hasFacts(String userId) {
        List<UserFactEntity> facts = jpaRepo.findByUserId(userId);
        return facts.stream().anyMatch(e -> !MIGRATION_MARKER_KEY.equals(e.getFactKey()));
    }

    private void performLazyMigration(String userId) {
        if (isMigrated(userId)) {
            return;
        }

        log.info("用户 {} 触发懒迁移：从 UserProfileService 获取画像", userId);
        try {
            Optional<UserProfile> profileOpt = userProfileService.get(userId);
            if (profileOpt.isPresent()) {
                migrateFromProfile(userId, profileOpt.get());
                log.info("用户 {} 懒迁移完成", userId);
            } else {
                markAsMigrated(userId);
                log.info("用户 {} 无现有画像，标记为已迁移", userId);
            }
        } catch (Exception e) {
            log.error("用户 {} 懒迁移失败", userId, e);
        }
    }

    private void markAsMigrated(String userId) {
        upsert(userId, new FactEntry(MIGRATION_MARKER_KEY,
                "true", FactCategory.IDENTITY, "lazy_migration", Instant.now()));
    }

    private FactEntry toDomain(UserFactEntity e) {
        return new FactEntry(
                e.getFactKey(),
                e.getFactValue(),
                FactCategory.fromString(e.getCategory()),
                e.getSource(),
                e.getUpdatedAt() != null ? e.getUpdatedAt().toInstant() : null
        );
    }
}
