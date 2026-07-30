package com.yupi.yuaiagent.repository.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_artifact")
public class ArtifactEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artifact_id", nullable = false, unique = true, length = 64)
    private String artifactId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(name = "agent_type", length = 64)
    private String agentType;

    @Column(length = 256)
    private String title;

    @Column(length = 32)
    private String type;

    @Column(length = 32)
    private String status;

    @Column(length = 32)
    private String scope;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private boolean reusable;

    @Column(name = "target_agents", columnDefinition = "TEXT")
    private String targetAgents;

    @Column(name = "dedup_key", length = 64)
    private String dedupKey;

    @Column(name = "schema_version")
    private Integer schemaVersion;

    @Column(name = "expires_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime expiresAt;

    @Column(name = "source_trace_id", length = 64)
    private String sourceTraceId;

    @Column(name = "file_path", length = 512)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
        if (status == null) status = "PENDING";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    }
}
