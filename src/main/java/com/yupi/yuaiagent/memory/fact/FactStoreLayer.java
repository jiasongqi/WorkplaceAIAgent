package com.yupi.yuaiagent.memory.fact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.memory.TokenBudgetAllocator;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.profile.model.CommunicationPreference;
import com.yupi.yuaiagent.profile.model.UserProfile;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fact Store Layer (L2) — 结构化用户事实存储。
 *
 * <p>以 userId 为粒度存储用户长期事实（身份、偏好、目标等键值对），
 * 支持 upsert 语义（同 key 覆写）和变更日志。
 *
 * <p>持久化策略：每个 userId 对应一个 JSON 文件，通过 ReadWriteLock 保证线程安全。
 */
@Slf4j
@Repository
public class FactStoreLayer {

    private static final String MIGRATION_MARKER_KEY = "__migrated_from_profile__";

    private final Path storageDir;
    private final ObjectMapper objectMapper;
    private final TokenBudgetAllocator tokenBudgetAllocator;
    private final UserProfileService userProfileService;

    /**
     * 内存索引：userId → 事实列表
     */
    private final ConcurrentHashMap<String, List<FactEntry>> factsIndex = new ConcurrentHashMap<>();

    /**
     * 每个 userId 的读写锁
     */
    private final ConcurrentHashMap<String, ReadWriteLock> locks = new ConcurrentHashMap<>();

    public FactStoreLayer(
            @Value("${memory.layers.fact-store.storage-dir:./tmp/memory/facts}") String storageDir,
            TokenBudgetAllocator tokenBudgetAllocator,
            @Lazy UserProfileService userProfileService) {
        this.storageDir = Path.of(storageDir);
        this.tokenBudgetAllocator = tokenBudgetAllocator;
        this.userProfileService = userProfileService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 初始化：创建存储目录，加载已有用户事实文件到内存。
     */
    @PostConstruct
    void init() {
        try {
            Files.createDirectories(storageDir);
            loadExistingFiles();
            log.info("FactStoreLayer 初始化完成，存储目录: {}, 已加载 {} 个用户事实",
                    storageDir.toAbsolutePath(), factsIndex.size());
        } catch (IOException e) {
            log.error("FactStoreLayer 初始化失败，无法创建存储目录: {}", storageDir, e);
        }
    }

    /**
     * 获取指定用户的所有事实。
     *
     * <p>首次访问时，若用户尚无事实且未完成画像迁移，则触发懒迁移：
     * 从 UserProfileService 获取已有画像并转换为 FactEntry 记录。
     *
     * @param userId 用户 ID
     * @return 用户的事实列表（不含内部标记），不存在时返回空列表
     */
    public List<FactEntry> getFacts(String userId) {
        // Lazy migration: check if user needs profile migration before reading facts
        if (!hasFacts(userId) && !isMigrated(userId)) {
            performLazyMigration(userId);
        }

        ReadWriteLock lock = getLock(userId);
        lock.readLock().lock();
        try {
            List<FactEntry> facts = factsIndex.getOrDefault(userId, Collections.emptyList());
            return facts.stream()
                    .filter(e -> !MIGRATION_MARKER_KEY.equals(e.key()))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 插入或更新一条用户事实。
     *
     * <p>如果同 key 已存在：
     * <ul>
     *   <li>值不同 → 覆写并记录变更日志</li>
     *   <li>值相同 → 跳过（幂等）</li>
     * </ul>
     *
     * @param userId 用户 ID
     * @param entry  事实条目
     */
    public void upsert(String userId, FactEntry entry) {
        if (userId == null || entry == null || entry.key() == null) {
            return;
        }

        ReadWriteLock lock = getLock(userId);
        lock.writeLock().lock();
        try {
            List<FactEntry> facts = factsIndex.computeIfAbsent(userId, k -> new ArrayList<>());
            Optional<FactEntry> existing = facts.stream()
                    .filter(f -> f.key().equals(entry.key()))
                    .findFirst();

            if (existing.isPresent()) {
                FactEntry old = existing.get();
                // 值相同则跳过（幂等）
                if (Objects.equals(old.value(), entry.value())) {
                    log.debug("用户 {} 事实 [{}] 值未变化，跳过 upsert", userId, entry.key());
                    return;
                }
                // 覆写并记录变更日志
                facts.remove(old);
                facts.add(entry);
                log.info("用户 {} 事实 [{}] 已更新: '{}' → '{}'",
                        userId, entry.key(), old.value(), entry.value());
            } else {
                // 新增
                facts.add(entry);
                log.info("用户 {} 新增事实 [{}] = '{}'", userId, entry.key(), entry.value());
            }

            persistToFile(userId, facts);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 批量 upsert 多条事实。
     *
     * @param userId  用户 ID
     * @param entries 事实条目列表
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
     *
     * <p>迁移映射：
     * <ul>
     *   <li>communicationPreference → key="communication_preference", category=PREFERENCES</li>
     *   <li>tonePreference → key="tone_preference", category=PREFERENCES</li>
     *   <li>focusAreas → key="focus_areas", value=逗号分隔, category=CAREER</li>
     *   <li>knownBackground → key="background", category=IDENTITY</li>
     *   <li>historicalDemands → key="historical_demands", value=逗号分隔, category=GOALS</li>
     * </ul>
     *
     * @param userId  用户 ID
     * @param profile 现有用户画像
     */
    public void migrateFromProfile(String userId, UserProfile profile) {
        if (userId == null || profile == null) {
            return;
        }

        Instant now = Instant.now();
        String migrationSource = "profile_migration";
        List<FactEntry> migrated = new ArrayList<>();

        // communicationPreference → PREFERENCES
        CommunicationPreference commPref = profile.getCommunicationPreference();
        if (commPref != null) {
            migrated.add(new FactEntry(
                    "communication_preference",
                    commPref.getDescription(),
                    FactCategory.PREFERENCES,
                    migrationSource,
                    now
            ));
        }

        // tonePreference → PREFERENCES
        String tone = profile.getTonePreference();
        if (tone != null && !tone.isBlank()) {
            migrated.add(new FactEntry(
                    "tone_preference",
                    tone,
                    FactCategory.PREFERENCES,
                    migrationSource,
                    now
            ));
        }

        // focusAreas → CAREER (comma-joined)
        List<String> focusAreas = profile.getFocusAreas();
        if (focusAreas != null && !focusAreas.isEmpty()) {
            migrated.add(new FactEntry(
                    "focus_areas",
                    String.join(",", focusAreas),
                    FactCategory.CAREER,
                    migrationSource,
                    now
            ));
        }

        // knownBackground → IDENTITY
        String background = profile.getKnownBackground();
        if (background != null && !background.isBlank()) {
            migrated.add(new FactEntry(
                    "background",
                    background,
                    FactCategory.IDENTITY,
                    migrationSource,
                    now
            ));
        }

        // historicalDemands → GOALS (comma-joined)
        List<String> demands = profile.getHistoricalDemands();
        if (demands != null && !demands.isEmpty()) {
            migrated.add(new FactEntry(
                    "historical_demands",
                    String.join(",", demands),
                    FactCategory.GOALS,
                    migrationSource,
                    now
            ));
        }

        // 执行批量 upsert
        batchUpsert(userId, migrated);

        // 写入迁移标记
        upsert(userId, new FactEntry(
                MIGRATION_MARKER_KEY,
                "true",
                FactCategory.IDENTITY,
                migrationSource,
                now
        ));

        log.info("用户 {} 画像迁移完成，共迁移 {} 条事实", userId, migrated.size());
    }

    /**
     * 格式化用户事实用于上下文注入，遵循 token 预算限制。
     *
     * @param userId      用户 ID
     * @param tokenBudget 最大 token 数
     * @return 格式化后的事实文本
     */
    public String formatForContext(String userId, int tokenBudget) {
        List<FactEntry> facts = getFacts(userId);
        if (facts.isEmpty()) {
            return "";
        }

        // 按类别分组格式化
        StringBuilder sb = new StringBuilder();
        sb.append("【用户事实】\n");

        Map<FactCategory, List<FactEntry>> grouped = facts.stream()
                .collect(Collectors.groupingBy(FactEntry::category));

        // 按类别优先级排列
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
     *
     * @param userId 用户 ID
     * @return 是否已迁移
     */
    public boolean isMigrated(String userId) {
        ReadWriteLock lock = getLock(userId);
        lock.readLock().lock();
        try {
            List<FactEntry> facts = factsIndex.getOrDefault(userId, Collections.emptyList());
            return facts.stream().anyMatch(f -> MIGRATION_MARKER_KEY.equals(f.key()));
        } finally {
            lock.readLock().unlock();
        }
    }

    // ========== Lazy Migration ==========

    /**
     * 检查用户是否已有事实记录（不含迁移标记）。
     *
     * @param userId 用户 ID
     * @return 是否存在至少一条用户事实
     */
    private boolean hasFacts(String userId) {
        ReadWriteLock lock = getLock(userId);
        lock.readLock().lock();
        try {
            List<FactEntry> facts = factsIndex.getOrDefault(userId, Collections.emptyList());
            return facts.stream().anyMatch(f -> !MIGRATION_MARKER_KEY.equals(f.key()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 执行懒迁移：从 UserProfileService 获取画像并转换为 FactEntry 记录。
     *
     * <p>线程安全：使用 double-check 模式避免重复迁移。
     * migrateFromProfile 和 markAsMigrated 内部已持有写锁。
     *
     * @param userId 用户 ID
     */
    private void performLazyMigration(String userId) {
        // Double-check: another thread may have completed migration in the meantime
        if (isMigrated(userId)) {
            return;
        }

        log.info("用户 {} 触发懒迁移：从 UserProfileService 获取画像", userId);
        try {
            Optional<UserProfile> profileOpt = userProfileService.get(userId);
            if (profileOpt.isPresent()) {
                migrateFromProfile(userId, profileOpt.get());
                log.info("用户 {} 懒迁移完成，已从 UserProfile 迁移事实", userId);
            } else {
                // No profile exists — mark as migrated to avoid re-checking on every access
                markAsMigrated(userId);
                log.info("用户 {} 无现有画像，标记为已迁移（跳过）", userId);
            }
        } catch (Exception e) {
            log.error("用户 {} 懒迁移失败，后续访问将重试", userId, e);
        }
    }

    /**
     * 标记用户已完成迁移（无画像数据时使用，避免每次 getFacts 都重试）。
     *
     * @param userId 用户 ID
     */
    private void markAsMigrated(String userId) {
        upsert(userId, new FactEntry(
                MIGRATION_MARKER_KEY,
                "true",
                FactCategory.IDENTITY,
                "lazy_migration",
                Instant.now()
        ));
    }

    // ========== 内部方法 ==========

    private ReadWriteLock getLock(String userId) {
        return locks.computeIfAbsent(userId, k -> new ReentrantReadWriteLock());
    }

    private void persistToFile(String userId, List<FactEntry> facts) {
        Path filePath = storageDir.resolve(userId + ".json");
        try {
            objectMapper.writeValue(filePath.toFile(), facts);
        } catch (IOException e) {
            log.error("用户 {} 事实持久化失败: {}", userId, filePath, e);
        }
    }

    private void loadExistingFiles() {
        if (!Files.exists(storageDir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(storageDir)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadFile);
        } catch (IOException e) {
            log.error("加载事实存储文件失败", e);
        }
    }

    private void loadFile(Path filePath) {
        try {
            String fileName = filePath.getFileName().toString();
            String userId = fileName.substring(0, fileName.length() - ".json".length());
            List<FactEntry> facts = objectMapper.readValue(
                    filePath.toFile(),
                    new TypeReference<List<FactEntry>>() {}
            );
            factsIndex.put(userId, new ArrayList<>(facts));
            log.debug("已加载用户 {} 的 {} 条事实", userId, facts.size());
        } catch (IOException e) {
            log.error("加载事实文件失败: {}", filePath, e);
        }
    }
}
