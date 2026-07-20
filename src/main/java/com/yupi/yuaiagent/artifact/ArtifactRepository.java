package com.yupi.yuaiagent.artifact;

import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactScope;
import com.yupi.yuaiagent.artifact.model.ArtifactStatus;
import com.yupi.yuaiagent.repository.entity.ArtifactEntity;
import com.yupi.yuaiagent.repository.jpa.ArtifactJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        LocalDateTime now = LocalDateTime.now();

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
    public Optional<Artifact> findById(String artifactId) {
        return jpaRepo.findByArtifactId(artifactId).map(this::toDomain);
    }

    /**
     * 查找所有交付物
     */
    public List<Artifact> findAll() {
        return jpaRepo.findAll().stream().map(this::toDomain).toList();
    }

    /**
     * 更新交付物状态
     */
    @Transactional
    public Optional<Artifact> updateStatus(String artifactId, ArtifactStatus status) {
        return jpaRepo.findByArtifactId(artifactId).map(entity -> {
            entity.setStatus(status.name());
            entity.setUpdatedAt(OffsetDateTime.now());
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
                .status(e.getStatus() != null ? ArtifactStatus.valueOf(e.getStatus()) : null)
                .scope(e.getScope() != null ? ArtifactScope.valueOf(e.getScope()) : null)
                .createdAt(toLocalDateTime(e.getCreatedAt()))
                .updatedAt(toLocalDateTime(e.getUpdatedAt()))
                .build();
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime odt) {
        return odt != null ? odt.toLocalDateTime() : null;
    }
}
