package com.yupi.yuaiagent.repository.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_feedback")
public class FeedbackEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feedback_id", nullable = false, unique = true, length = 64)
    private String feedbackId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "chat_id", length = 64)
    private String chatId;

    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(name = "agent_type", length = 64)
    private String agentType;

    @Column(nullable = false, length = 8)
    private String rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(length = 64)
    private String intent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
