package com.yupi.yuaiagent.repository.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "t_digital_employee_version")
public class DigitalEmployeeVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 64)
    private String employeeId;

    @Column(name = "config_version", nullable = false)
    private Integer configVersion;

    @Column(name = "persona", columnDefinition = "TEXT")
    private String persona;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_bindings", columnDefinition = "TEXT")
    private List<String> skillBindings;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
