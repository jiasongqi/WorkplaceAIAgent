package com.yupi.yuaiagent.repository.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Entity
@Table(name = "t_user_companion")
public class UserCompanionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "persona_prompt", columnDefinition = "TEXT")
    private String personaPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "style_prefs", columnDefinition = "TEXT")
    private Map<String, Object> stylePrefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enabled_skills", columnDefinition = "TEXT")
    private List<String> enabledSkills;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (displayName == null) {
            displayName = "你的职场伙伴";
        }
        if (version == null) {
            version = 1;
        }
        if (stylePrefs == null) {
            stylePrefs = new HashMap<>();
        }
        if (enabledSkills == null) {
            enabledSkills = new ArrayList<>();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
