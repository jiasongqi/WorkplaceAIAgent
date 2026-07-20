package com.yupi.yuaiagent.feedback;

import com.yupi.yuaiagent.repository.entity.FeedbackEntity;
import com.yupi.yuaiagent.repository.jpa.FeedbackJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Feedback Repository — JPA persistence for user feedback.
 *
 * @author jsq
 */
@Slf4j
@Repository
public class FeedbackRepository {

    private final FeedbackJpaRepository jpaRepo;

    public FeedbackRepository(FeedbackJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Transactional
    public void save(Feedback feedback) {
        FeedbackEntity entity = toEntity(feedback);
        jpaRepo.save(entity);
    }

    public List<Feedback> findAll() {
        return jpaRepo.findAll().stream().map(this::toDomain).toList();
    }

    public List<Feedback> findByUserId(String userId) {
        return jpaRepo.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    public List<Feedback> findByAgentType(String agentType) {
        return jpaRepo.findByAgentType(agentType).stream().map(this::toDomain).toList();
    }

    public long countByRating(Feedback.Rating rating) {
        return jpaRepo.countByRating(rating.name());
    }

    public double getApprovalRate() {
        List<Feedback> all = findAll();
        if (all.isEmpty()) return -1.0;
        long up = countByRating(Feedback.Rating.UP);
        return (double) up / all.size();
    }

    public double getAgentApprovalRate(String agentType) {
        List<Feedback> agentFeedback = findByAgentType(agentType);
        if (agentFeedback.isEmpty()) return -1.0;
        long up = agentFeedback.stream()
                .filter(f -> f.rating() == Feedback.Rating.UP).count();
        return (double) up / agentFeedback.size();
    }

    // ========== Mapping ==========

    private FeedbackEntity toEntity(Feedback f) {
        FeedbackEntity e = new FeedbackEntity();
        e.setFeedbackId(f.id() != null ? f.id() : UUID.randomUUID().toString());
        e.setUserId(f.userId());
        e.setChatId(f.chatId());
        e.setMessageId(f.messageId());
        e.setAgentType(f.agentType());
        e.setRating(f.rating().name());
        e.setComment(f.comment());
        e.setIntent(f.intent());
        return e;
    }

    private Feedback toDomain(FeedbackEntity e) {
        return new Feedback(
                e.getFeedbackId(),
                e.getUserId(),
                e.getChatId(),
                e.getMessageId(),
                Feedback.Rating.valueOf(e.getRating()),
                e.getComment(),
                e.getAgentType(),
                e.getIntent(),
                toLocalDateTime(e.getCreatedAt())
        );
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime odt) {
        return odt != null ? odt.toLocalDateTime() : null;
    }
}
