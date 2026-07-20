package com.yupi.yuaiagent.repository.jpa;

import java.util.List;
import java.util.Optional;
import com.yupi.yuaiagent.repository.entity.TraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TraceJpaRepository extends JpaRepository<TraceEntity, Long> {

    Optional<TraceEntity> findByTraceId(String traceId);

    List<TraceEntity> findByUserIdOrderByStartedAtDesc(String userId);

    List<TraceEntity> findByConversationId(String conversationId);
}
