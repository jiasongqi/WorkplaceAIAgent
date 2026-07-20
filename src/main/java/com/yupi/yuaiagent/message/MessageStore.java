package com.yupi.yuaiagent.message;

import java.util.List;

/**
 * Pluggable message persistence (file | jdbc) selected by {@code app.storage.type}.
 */
public interface MessageStore {

    PersistentChatMessage save(String chatId, String role, String content,
                               MessageSource sourceType, String sourceId, String sourceName);

    PersistentChatMessage startStreaming(String chatId, String role,
                                         MessageSource sourceType, String sourceId, String sourceName);

    void updatePartial(String messageId, String partialContent);

    void complete(String messageId, String fullContent);

    void markPartial(String messageId);

    List<PersistentChatMessage> findByChatId(String chatId);

    PersistentChatMessage findByMessageId(String messageId);

    int countByChatId(String chatId);

    void deleteByChatId(String chatId);

    void replaceWithSummary(String chatId, String summary, int keepRecent);
}
