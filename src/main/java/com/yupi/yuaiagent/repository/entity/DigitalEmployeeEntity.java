package com.yupi.yuaiagent.repository.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "t_digital_employee")
public class DigitalEmployeeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, unique = true, length = 64)
    private String employeeId;

    @Column(name = "owner_user_id", nullable = false, length = 64)
    private String ownerUserId;

    @Column(name = "template_code", nullable = false, length = 128)
    private String templateCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "persona", columnDefinition = "TEXT")
    private String persona;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_bindings", columnDefinition = "TEXT")
    private List<String> skillBindings;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "config_version", nullable = false)
    private Integer configVersion = 1;

    @Column(name = "active", nullable = false)
    private Boolean active = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = "ACTIVE";
        }
        if (configVersion == null) {
            configVersion = 1;
        }
        if (active == null) {
            active = false;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
