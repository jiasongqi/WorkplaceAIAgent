package com.yupi.yuaiagent.artifact.adoption;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_artifact_adoption", uniqueConstraints = @UniqueConstraint(
        name = "uk_artifact_adoption_stage", columnNames = {"artifact_id", "turn_id", "stage"}))
public class ArtifactAdoption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artifact_id", nullable = false, length = 64)
    private String artifactId;

    @Column(name = "consumer_agent", nullable = false, length = 64)
    private String consumerAgent;

    @Column(name = "chat_id", length = 64)
    private String chatId;

    @Column(name = "turn_id", nullable = false, length = 64)
    private String turnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ArtifactAdoptionStage stage;

    private Double confidence;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        }
    }
}
