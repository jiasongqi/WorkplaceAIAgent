package com.yupi.yuaiagent.nlu;

/**
 * Conversation state storage abstraction.
 *
 * <p>Dev (default): {@link InMemoryConversationStateStore} — pure ConcurrentHashMap.
 * <p>Prod: {@code RedisConversationStateStore} — CAS + TTL.
 *
 * @author jsq
 */
public interface ConversationStateStore {

    ConversationState get(String chatId);

    void save(String chatId, ConversationState state);

    void delete(String chatId);
}
