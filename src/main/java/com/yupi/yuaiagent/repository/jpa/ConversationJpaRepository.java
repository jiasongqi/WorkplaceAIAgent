package com.yupi.yuaiagent.repository.jpa;

import java.util.List;
import java.util.Optional;
import com.yupi.yuaiagent.repository.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationJpaRepository extends JpaRepository<ConversationEntity, Long> {

    Optional<ConversationEntity> findByConversationId(String conversationId);

    List<ConversationEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
