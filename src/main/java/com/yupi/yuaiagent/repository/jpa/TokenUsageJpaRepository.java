package com.yupi.yuaiagent.repository.jpa;

import java.util.List;
import java.time.OffsetDateTime;
import com.yupi.yuaiagent.repository.entity.TokenUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenUsageJpaRepository extends JpaRepository<TokenUsageEntity, Long> {

    List<TokenUsageEntity> findByWorkflowId(String workflowId);

    List<TokenUsageEntity> findByUserIdAndCreatedAtAfter(String userId, OffsetDateTime since);
}
