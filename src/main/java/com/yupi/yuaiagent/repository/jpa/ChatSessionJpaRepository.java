package com.yupi.yuaiagent.repository.jpa;

import com.yupi.yuaiagent.repository.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, Long> {

    List<ChatSessionEntity> findByUserId(String userId);

    Optional<ChatSessionEntity> findBySessionId(String sessionId);

    void deleteByExpiresAtBefore(OffsetDateTime now);
}
