package com.yupi.yuaiagent.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.profile.model.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 用户画像存储
 * 基于文件的持久化存储，复用 AppointmentRepository 范式
 * （ObjectMapper + JavaTimeModule、ConcurrentHashMap 内存索引、读写锁、@PostConstruct 加载、
 * writerWithDefaultPrettyPrinter 写盘）。
 * <p>
 * 以 userId 作为唯一键，保证每个 userId 至多对应一份画像。
 *
 * @author jsq
 */
@Slf4j
@Repository
public class UserProfileRepository {

    @Value("${user-profile.storage.dir:./tmp/user-profiles}")
    private String storageDir;

    private final ObjectMapper objectMapper;
    /** userId -> UserProfile，保证每个 userId 唯一一份画像 */
    private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private File storageFile;

    public UserProfileRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            File dir = new File(storageDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            storageFile = new File(dir, "user-profiles.json");
            loadFromFile();
            log.info("用户画像存储初始化完成，存储路径：{}", storageFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("初始化用户画像存储失败", e);
        }
    }

    /**
     * 根据 userId 查找画像
     *
     * @param userId 用户唯一标识
     * @return 对应画像，不存在时返回 Optional.empty()
     */
    public Optional<UserProfile> findByUserId(String userId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(profiles.get(userId));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 根据 userId 删除画像并持久化
     *
     * @param userId 用户唯一标识
     * @return 存在并删除返回 true，不存在返回 false
     */
    public boolean deleteByUserId(String userId) {
        lock.writeLock().lock();
        try {
            UserProfile removed = profiles.remove(userId);
            if (removed != null) {
                saveToFile();
                log.info("删除用户画像：{}", userId);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 将抽取结果合并到已有画像并持久化。
     * <ul>
     *     <li>base（已有画像）为空：新建，设置 userId、createdAt=now、updatedAt=now，直接采用 extracted 的各维度</li>
     *     <li>base 非空：标量维度（沟通偏好、语气偏好、已知背景）当 extracted 新值非空时覆盖、否则保留旧值；
     *     列表维度（关注领域、历史诉求）将 extracted 新条目追加到旧列表并去重（保序）；刷新 updatedAt=now</li>
     * </ul>
     * 始终保证 updatedAt &gt;= createdAt。
     *
     * @param userId    用户唯一标识
     * @param extracted 本次抽取得到的画像
     * @return 合并后的画像
     */
    public UserProfile merge(String userId, UserProfile extracted) {
        lock.writeLock().lock();
        try {
            UserProfile base = profiles.get(userId);
            LocalDateTime now = LocalDateTime.now();
            if (base == null) {
                extracted.setUserId(userId);
                extracted.setCreatedAt(now);
                extracted.setUpdatedAt(now);
                profiles.put(userId, extracted);
                saveToFile();
                log.info("新建用户画像：{}", userId);
                return extracted;
            }

            // 标量维度：新值非空则覆盖，否则保留旧值（较新值优先）
            if (extracted.getCommunicationPreference() != null) {
                base.setCommunicationPreference(extracted.getCommunicationPreference());
            }
            if (extracted.getTonePreference() != null) {
                base.setTonePreference(extracted.getTonePreference());
            }
            if (extracted.getKnownBackground() != null) {
                base.setKnownBackground(extracted.getKnownBackground());
            }

            // 列表维度：追加去重（保持插入顺序）
            base.setFocusAreas(mergeDistinct(base.getFocusAreas(), extracted.getFocusAreas()));
            base.setHistoricalDemands(mergeDistinct(base.getHistoricalDemands(), extracted.getHistoricalDemands()));

            // 刷新 updatedAt，保证 updatedAt >= createdAt
            base.setUpdatedAt(now);
            saveToFile();
            log.info("更新用户画像：{}", userId);
            return base;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 合并两个列表并去重，保持插入顺序（旧列表在前，新条目追加在后）。
     */
    private static List<String> mergeDistinct(List<String> existing, List<String> incoming) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (existing != null) {
            set.addAll(existing);
        }
        if (incoming != null) {
            set.addAll(incoming);
        }
        return new ArrayList<>(set);
    }

    /**
     * 从文件加载画像
     */
    private void loadFromFile() {
        if (storageFile.exists() && storageFile.length() > 0) {
            try {
                Map<String, UserProfile> loaded = objectMapper.readValue(
                        storageFile,
                        new TypeReference<Map<String, UserProfile>>() {}
                );
                profiles.putAll(loaded);
                log.info("从文件加载用户画像：{} 条", loaded.size());
            } catch (IOException e) {
                log.error("加载用户画像文件失败", e);
            }
        }
    }

    /**
     * 保存到文件
     */
    private void saveToFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, profiles);
        } catch (IOException e) {
            log.error("保存用户画像文件失败", e);
        }
    }
}
