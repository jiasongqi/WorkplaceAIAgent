package com.yupi.yuaiagent.artifact;

import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactQuery;
import com.yupi.yuaiagent.artifact.model.ArtifactScope;
import com.yupi.yuaiagent.artifact.model.ArtifactStatus;
import com.yupi.yuaiagent.repository.entity.ArtifactEntity;
import com.yupi.yuaiagent.repository.jpa.ArtifactJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 交付物存储 — JPA 持久化实现。
 * <p>
 * Public API 保持不变，内部从 ConcurrentHashMap + JSON 文件切换到 PostgreSQL。
 *
 * @author jsq
 */
@Slf4j
@Repository
public class ArtifactRepository {

    private final ArtifactJpaRepository jpaRepo;

    public ArtifactRepository(ArtifactJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    /**
     * 保存或更新交付物
     */
    @Transactional
    public Artifact save(Artifact artifact) {
        if (artifact.getArtifactId() == null || artifact.getArtifactId().isEmpty()) {
            artifact.setArtifactId(UUID.randomUUID().toString());
        }
        LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC());

        // Check existing
        Optional<ArtifactEntity> existingOpt = jpaRepo.findByArtifactId(artifact.getArtifactId());
        ArtifactEntity entity = null;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            // Preserve original createdAt
            artifact.setCreatedAt(toLocalDateTime(entity.getCreatedAt()));
        } else if (artifact.getCreatedAt() == null) {
            artifact.setCreatedAt(now);
        }
        artifact.setUpdatedAt(now);

        entity = toEntity(artifact, entity);
        ArtifactEntity saved = jpaRepo.save(entity);
        log.info("保存交付物：{}", saved.getArtifactId());
        return toDomain(saved);
    }

    /**
     * 根据 ID 查找交付物
     */
    @Transactional(readOnly = true)
    public Optional<Artifact> findById(String artifactId) {
        return jpaRepo.findByArtifactId(artifactId).map(this::toDomain);
    }

    /**
     * 查找所有交付物
     */
    @Transactional(readOnly = true)
    public List<Artifact> findAll() {
        return jpaRepo.findAll().stream().map(this::toDomain).toList();
    }

    /**
     * 使用 JPA Specification 在数据库中完成可选条件过滤。
     */
    @Transactional(readOnly = true)
    public List<Artifact> find(ArtifactQuery query) {
        ArtifactQuery q = query != null ? query : ArtifactQuery.builder().build();
        Specification<ArtifactEntity> specification = (root, ignored, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q.getUserId() != null) predicates.add(cb.equal(root.get("userId"), q.getUserId()));
            if (q.getChatId() != null) predicates.add(cb.equal(root.get("conversationId"), q.getChatId()));
            if (q.getType() != null) predicates.add(cb.equal(root.get("type"), q.getType()));
            if (q.getScope() != null) predicates.add(cb.equal(root.get("scope"), q.getScope().name()));
            if (q.getStatus() != null) predicates.add(cb.equal(root.get("status"), q.getStatus().name()));
            if (q.getReusable() != null) predicates.add(cb.equal(root.get("reusable"), q.getReusable()));
            if (q.getTargetAgent() != null && !q.getTargetAgent().isBlank()) {
                String pattern = "%," + q.getTargetAgent().trim().toUpperCase() + ",%";
                predicates.add(cb.like(cb.upper(cb.concat(cb.concat(",", root.get("targetAgents")), ",")), pattern));
            }
            if (q.getActiveAt() != null) {
                OffsetDateTime activeAt = q.getActiveAt().atOffset(ZoneOffset.UTC);
                predicates.add(cb.or(cb.isNull(root.get("expiresAt")),
                        cb.greaterThan(root.get("expiresAt"), activeAt)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        int limit = q.getLimit() != null ? Math.max(1, q.getLimit()) : 100;
        return jpaRepo.findAll(specification,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Artifact> findByDedupKey(String dedupKey) {
        if (dedupKey == null || dedupKey.isBlank()) {
            return Optional.empty();
        }
        return jpaRepo.findByDedupKey(dedupKey).map(this::toDomain);
    }

    /**
     * 更新交付物状态
     */
    @Transactional
    public Optional<Artifact> updateStatus(String artifactId, ArtifactStatus status) {
        return jpaRepo.findByArtifactId(artifactId).map(entity -> {
            entity.setStatus(status.name());
            entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            ArtifactEntity saved = jpaRepo.save(entity);
            return toDomain(saved);
        });
    }

    // ========== Mapping ==========

    private ArtifactEntity toEntity(Artifact a, ArtifactEntity existing) {
        ArtifactEntity e = existing != null ? existing : new ArtifactEntity();
        e.setArtifactId(a.getArtifactId());
        e.setUserId(a.getUserId());
        e.setConversationId(a.getChatId());
        e.setAgentType(a.getProducer());
        e.setTitle(a.getTitle());
        e.setType(a.getType());
        e.setContent(a.getContent());
        e.setSummary(a.getSummary());
        e.setReusable(a.isReusable());
        e.setTargetAgents(encodeTargetAgents(a.getTargetAgents()));
        e.setDedupKey(a.getDedupKey());
        e.setSchemaVersion(a.getSchemaVersion());
        e.setExpiresAt(toOffsetDateTime(a.getExpiresAt()));
        e.setSourceTraceId(a.getSourceTraceId());
        e.setStatus(a.getStatus() != null ? a.getStatus().name() : null);
        e.setScope(a.getScope() != null ? a.getScope().name() : null);
        if (a.getCreatedAt() != null) {
            e.setCreatedAt(a.getCreatedAt().atOffset(ZoneOffset.UTC));
        }
        if (a.getUpdatedAt() != null) {
            e.setUpdatedAt(a.getUpdatedAt().atOffset(ZoneOffset.UTC));
        }
        return e;
    }

    private Artifact toDomain(ArtifactEntity e) {
        return Artifact.builder()
                .artifactId(e.getArtifactId())
                .userId(e.getUserId())
                .chatId(e.getConversationId())
                .producer(e.getAgentType())
                .title(e.getTitle())
                .type(e.getType())
                .content(e.getContent())
                .summary(e.getSummary())
                .reusable(e.isReusable())
                .targetAgents(decodeTargetAgents(e.getTargetAgents()))
                .dedupKey(e.getDedupKey())
                .schemaVersion(e.getSchemaVersion())
                .expiresAt(toLocalDateTime(e.getExpiresAt()))
                .sourceTraceId(e.getSourceTraceId())
                .status(e.getStatus() != null ? ArtifactStatus.valueOf(e.getStatus()) : null)
                .scope(e.getScope() != null ? ArtifactScope.valueOf(e.getScope()) : null)
                .createdAt(toLocalDateTime(e.getCreatedAt()))
                .updatedAt(toLocalDateTime(e.getUpdatedAt()))
                .build();
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime odt) {
        return odt != null ? odt.toLocalDateTime() : null;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value != null ? value.atOffset(ZoneOffset.UTC) : null;
    }

    private String encodeTargetAgents(List<String> agents) {
        if (agents == null || agents.isEmpty()) {
            return "";
        }
        return agents.stream()
                .filter(a -> a != null && !a.isBlank())
                .map(a -> a.trim().toUpperCase())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private List<String> decodeTargetAgents(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        return Arrays.stream(encoded.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
