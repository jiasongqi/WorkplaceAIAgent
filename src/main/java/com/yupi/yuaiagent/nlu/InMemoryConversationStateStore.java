package com.yupi.yuaiagent.nlu;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory conversation state — dev/test default.
 * For production, override with RedisConversationStateStore.
 *
 * @author jsq
 */
@Slf4j
public class InMemoryConversationStateStore implements ConversationStateStore {

    private final Map<String, ConversationState> store = new ConcurrentHashMap<>();

    @Override
    public ConversationState get(String chatId) {
        return store.computeIfAbsent(chatId, id -> new ConversationState());
    }

    @Override
    public void save(String chatId, ConversationState state) {
        store.merge(chatId, state, (existing, fresh) -> {
            if (existing.getVersion() >= fresh.getVersion()) {
                return existing; // Keep existing (newer)
            }
            return fresh;
        });
    }

    @Override
    public void delete(String chatId) {
        store.remove(chatId);
    }
}
