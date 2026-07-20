package com.yupi.yuaiagent.repository.jpa;

import java.util.List;
import java.util.Optional;
import com.yupi.yuaiagent.repository.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageJpaRepository extends JpaRepository<MessageEntity, Long> {

    Optional<MessageEntity> findByMessageId(String messageId);

    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    Optional<MessageEntity> findTopByConversationIdAndRoleOrderByCreatedAtDesc(String conversationId, String role);
}
