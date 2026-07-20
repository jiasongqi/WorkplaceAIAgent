package com.yupi.yuaiagent.repository.jpa;

import java.util.List;
import java.util.Optional;
import com.yupi.yuaiagent.repository.entity.FeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackJpaRepository extends JpaRepository<FeedbackEntity, Long> {

    Optional<FeedbackEntity> findByFeedbackId(String feedbackId);

    List<FeedbackEntity> findByUserId(String userId);

    List<FeedbackEntity> findByAgentType(String agentType);

    long countByRating(String rating);
}
