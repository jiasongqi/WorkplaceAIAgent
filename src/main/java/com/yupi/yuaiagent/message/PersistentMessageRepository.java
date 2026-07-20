package com.yupi.yuaiagent.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Public facade for message persistence. Delegates to file or JDBC {@link MessageStore}
 * based on {@code app.storage.type}.
 */
@Repository
@RequiredArgsConstructor
public class PersistentMessageRepository {

    private final MessageStore store;

    public PersistentChatMessage save(String chatId, String role, String content) {
        return store.save(chatId, role, content, null, null, null);
    }

    public PersistentChatMessage save(String chatId, String role, String content,
                                      MessageSource sourceType, String sourceId, String sourceName) {
        return store.save(chatId, role, content, sourceType, sourceId, sourceName);
    }

    public PersistentChatMessage startStreaming(String chatId, String role,
                                                MessageSource sourceType, String sourceId, String sourceName) {
        return store.startStreaming(chatId, role, sourceType, sourceId, sourceName);
    }

    public void updatePartial(String messageId, String partialContent) {
        store.updatePartial(messageId, partialContent);
    }

    public void complete(String messageId, String fullContent) {
        store.complete(messageId, fullContent);
    }

    public void markPartial(String messageId) {
        store.markPartial(messageId);
    }

    public List<PersistentChatMessage> findByChatId(String chatId) {
        return store.findByChatId(chatId);
    }

    public PersistentChatMessage findByMessageId(String messageId) {
        return store.findByMessageId(messageId);
    }

    public int countByChatId(String chatId) {
        return store.countByChatId(chatId);
    }

    public void deleteByChatId(String chatId) {
        store.deleteByChatId(chatId);
    }

    public void replaceWithSummary(String chatId, String summary, int keepRecent) {
        store.replaceWithSummary(chatId, summary, keepRecent);
    }
}
