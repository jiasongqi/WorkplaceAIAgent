package com.yupi.yuaiagent.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.profile.model.UserProfile;
import com.yupi.yuaiagent.repository.entity.UserProfileEntity;
import com.yupi.yuaiagent.repository.jpa.UserProfileJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户画像存储 — JPA 持久化实现。
 * <p>
 * Public API 保持不变，内部从 ConcurrentHashMap + JSON 文件切换到 PostgreSQL。
 * UserProfile 序列化为 JSONB 存储在 t_user_profile.profile_data 字段。
 *
 * @author jsq
 */
@Slf4j
@Repository
public class UserProfileRepository {

    private final UserProfileJpaRepository jpaRepo;
    private final ObjectMapper objectMapper;

    public UserProfileRepository(UserProfileJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 根据 userId 查找画像
     */
    public Optional<UserProfile> findByUserId(String userId) {
        return jpaRepo.findByUserId(userId).map(this::toDomain);
    }

    /**
     * 根据 userId 删除画像
     */
    @Transactional
    public boolean deleteByUserId(String userId) {
        Optional<UserProfileEntity> entity = jpaRepo.findByUserId(userId);
        if (entity.isPresent()) {
            jpaRepo.delete(entity.get());
            log.info("删除用户画像：{}", userId);
            return true;
        }
        return false;
    }

    /**
     * 将抽取结果合并到已有画像并持久化。
     */
    @Transactional
    public UserProfile merge(String userId, UserProfile extracted) {
        Optional<UserProfileEntity> existingOpt = jpaRepo.findByUserId(userId);
        LocalDateTime now = LocalDateTime.now();

        if (existingOpt.isEmpty()) {
            extracted.setUserId(userId);
            extracted.setCreatedAt(now);
            extracted.setUpdatedAt(now);
            UserProfileEntity entity = toEntity(userId, extracted);
            jpaRepo.save(entity);
            log.info("新建用户画像：{}", userId);
            return extracted;
        }

        UserProfileEntity existing = existingOpt.get();
        UserProfile base = toDomain(existing);

        // Scalar dimensions: override if new value is non-null
        if (extracted.getCommunicationPreference() != null) {
            base.setCommunicationPreference(extracted.getCommunicationPreference());
        }
        if (extracted.getTonePreference() != null) {
            base.setTonePreference(extracted.getTonePreference());
        }
        if (extracted.getKnownBackground() != null) {
            base.setKnownBackground(extracted.getKnownBackground());
        }

        // List dimensions: merge and deduplicate
        base.setFocusAreas(mergeDistinct(base.getFocusAreas(), extracted.getFocusAreas()));
        base.setHistoricalDemands(mergeDistinct(base.getHistoricalDemands(), extracted.getHistoricalDemands()));

        base.setUpdatedAt(now);

        // Save back
        UserProfileEntity updated = toEntity(userId, base);
        updated.setId(existing.getId());
        updated.setCreatedAt(existing.getCreatedAt());
        jpaRepo.save(updated);
        log.info("更新用户画像：{}", userId);
        return base;
    }

    private static List<String> mergeDistinct(List<String> existing, List<String> incoming) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (existing != null) set.addAll(existing);
        if (incoming != null) set.addAll(incoming);
        return new ArrayList<>(set);
    }

    // ========== Mapping ==========

    private UserProfileEntity toEntity(String userId, UserProfile profile) {
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        try {
            Map<String, Object> data = objectMapper.convertValue(profile, new TypeReference<>() {});
            entity.setProfileData(data);
        } catch (Exception e) {
            log.error("Failed to serialize UserProfile for userId={}", userId, e);
            entity.setProfileData(new HashMap<>());
        }
        return entity;
    }

    private UserProfile toDomain(UserProfileEntity entity) {
        try {
            UserProfile profile = objectMapper.convertValue(entity.getProfileData(), UserProfile.class);
            profile.setUserId(entity.getUserId());
            return profile;
        } catch (Exception e) {
            log.error("Failed to deserialize UserProfile for userId={}", entity.getUserId(), e);
            return UserProfile.builder().userId(entity.getUserId()).build();
        }
    }
}
