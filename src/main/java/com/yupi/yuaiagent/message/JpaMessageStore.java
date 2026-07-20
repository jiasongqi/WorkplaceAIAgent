package com.yupi.yuaiagent.message;

import cn.hutool.core.util.IdUtil;
import com.yupi.yuaiagent.repository.entity.MessageEntity;
import com.yupi.yuaiagent.repository.jpa.MessageJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JPA-backed message store ({@code app.storage.type=jdbc}).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.type", havingValue = "jdbc")
public class JpaMessageStore implements MessageStore {

    private final MessageJpaRepository jpaRepo;

    @Override
    @Transactional
    public PersistentChatMessage save(String chatId, String role, String content,
                                      MessageSource sourceType, String sourceId, String sourceName) {
        MessageEntity entity = newEntity(chatId, role, content, sourceType, sourceId, sourceName);
        entity.setStatus(MessageStatus.COMPLETE.name());
        return toDomain(jpaRepo.save(entity));
    }

    @Override
    @Transactional
    public PersistentChatMessage startStreaming(String chatId, String role,
                                                MessageSource sourceType, String sourceId, String sourceName) {
        MessageEntity entity = newEntity(chatId, role, "", sourceType, sourceId, sourceName);
        entity.setStatus(MessageStatus.STREAMING.name());
        entity.setPartialContent("");
        return toDomain(jpaRepo.save(entity));
    }

    @Override
    @Transactional
    public void updatePartial(String messageId, String partialContent) {
        jpaRepo.findByMessageId(messageId).ifPresent(e -> {
            e.setPartialContent(partialContent);
            if (!MessageStatus.COMPLETE.name().equals(e.getStatus())) {
                e.setStatus(MessageStatus.STREAMING.name());
            }
            jpaRepo.save(e);
        });
    }

    @Override
    @Transactional
    public void complete(String messageId, String fullContent) {
        jpaRepo.findByMessageId(messageId).ifPresent(e -> {
            e.setContent(fullContent != null ? fullContent : "");
            e.setPartialContent(null);
            e.setStatus(MessageStatus.COMPLETE.name());
            jpaRepo.save(e);
        });
    }

    @Override
    @Transactional
    public void markPartial(String messageId) {
        jpaRepo.findByMessageId(messageId).ifPresent(e -> {
            if (MessageStatus.COMPLETE.name().equals(e.getStatus())) {
                return;
            }
            if ((e.getContent() == null || e.getContent().isEmpty())
                    && e.getPartialContent() != null && !e.getPartialContent().isEmpty()) {
                e.setContent(e.getPartialContent());
            }
            e.setStatus(MessageStatus.PARTIAL.name());
            jpaRepo.save(e);
        });
    }

    @Override
    public List<PersistentChatMessage> findByChatId(String chatId) {
        return jpaRepo.findByConversationIdOrderByCreatedAtAsc(chatId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public PersistentChatMessage findByMessageId(String messageId) {
        return jpaRepo.findByMessageId(messageId).map(this::toDomain).orElse(null);
    }

    @Override
    public int countByChatId(String chatId) {
        return jpaRepo.findByConversationIdOrderByCreatedAtAsc(chatId).size();
    }

    @Override
    @Transactional
    public void deleteByChatId(String chatId) {
        List<MessageEntity> list = jpaRepo.findByConversationIdOrderByCreatedAtAsc(chatId);
        jpaRepo.deleteAll(list);
    }

    @Override
    @Transactional
    public void replaceWithSummary(String chatId, String summary, int keepRecent) {
        List<MessageEntity> all = jpaRepo.findByConversationIdOrderByCreatedAtAsc(chatId);
        if (all.isEmpty()) {
            return;
        }
        int from = Math.max(0, all.size() - keepRecent);
        List<MessageEntity> recent = new ArrayList<>(all.subList(from, all.size()));
        jpaRepo.deleteAll(all);

        MessageEntity summaryMsg = newEntity(chatId, "system", "[记忆压缩摘要] " + summary,
                MessageSource.SYSTEM, null, null);
        summaryMsg.setStatus(MessageStatus.COMPLETE.name());
        jpaRepo.save(summaryMsg);
        for (MessageEntity r : recent) {
            r.setId(null);
            jpaRepo.save(r);
        }
        log.info("[message:jdbc] compressed chatId={}, keepRecent={}", chatId, keepRecent);
    }

    private MessageEntity newEntity(String chatId, String role, String content,
                                    MessageSource sourceType, String sourceId, String sourceName) {
        MessageEntity e = new MessageEntity();
        e.setMessageId(IdUtil.fastSimpleUUID());
        e.setConversationId(chatId);
        e.setRole(role);
        e.setContent(content != null ? content : "");
        Map<String, Object> meta = new HashMap<>();
        if (sourceType != null) {
            meta.put("sourceType", sourceType.name());
        }
        if (sourceId != null) {
            meta.put("sourceId", sourceId);
        }
        if (sourceName != null) {
            meta.put("sourceName", sourceName);
        }
        if (!meta.isEmpty()) {
            e.setMetadata(meta);
        }
        return e;
    }

    private PersistentChatMessage toDomain(MessageEntity e) {
        PersistentChatMessage m = new PersistentChatMessage();
        m.setMessageId(e.getMessageId());
        m.setChatId(e.getConversationId());
        m.setRole(e.getRole());
        m.setContent(e.getContent());
        m.setPartialContent(e.getPartialContent());
        m.setStatus(MessageStatus.from(e.getStatus()));
        m.setTimestamp(e.getCreatedAt() != null ? e.getCreatedAt().toInstant().toEpochMilli() : System.currentTimeMillis());
        Map<String, Object> meta = e.getMetadata();
        if (meta != null) {
            Object st = meta.get("sourceType");
            if (st != null) {
                try {
                    m.setSourceType(MessageSource.valueOf(String.valueOf(st)));
                } catch (Exception ignored) {
                    // ignore
                }
            }
            m.setSourceId(meta.get("sourceId") != null ? String.valueOf(meta.get("sourceId")) : null);
            m.setSourceName(meta.get("sourceName") != null ? String.valueOf(meta.get("sourceName")) : null);
        }
        return m;
    }
}
