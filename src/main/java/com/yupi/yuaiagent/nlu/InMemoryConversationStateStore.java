package com.yupi.yuaiagent.nlu;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory conversation state with file-based persistence — dev/test default.
 * State survives restarts by writing to {@code ./tmp/nlu/state/{chatId}.json}.
 * For production, override with RedisConversationStateStore.
 *
 * @author jsq
 */
@Slf4j
public class InMemoryConversationStateStore implements ConversationStateStore {

    private final Map<String, ConversationState> store = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final File stateDir;

    public InMemoryConversationStateStore() {
        stateDir = new File("./tmp/nlu/state");
        if (!stateDir.exists()) {
            stateDir.mkdirs();
        }
    }

    @Override
    public ConversationState get(String chatId) {
        rwLock.readLock().lock();
        try {
            ConversationState state = store.get(chatId);
            if (state != null) {
                return state;
            }
        } finally {
            rwLock.readLock().unlock();
        }

        // In-memory miss — try loading from file
        rwLock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            ConversationState state = store.get(chatId);
            if (state != null) {
                return state;
            }
            state = loadFromFile(chatId);
            if (state != null) {
                store.put(chatId, state);
                log.debug("[NLU] loaded conversation state from file for chatId={}", chatId);
            } else {
                state = new ConversationState();
                store.put(chatId, state);
            }
            return state;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void save(String chatId, ConversationState state) {
        rwLock.writeLock().lock();
        try {
            store.merge(chatId, state, (existing, fresh) -> {
                if (existing.getVersion() >= fresh.getVersion()) {
                    return existing; // Keep existing (newer)
                }
                return fresh;
            });
            saveToFile(chatId, store.get(chatId));
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void delete(String chatId) {
        rwLock.writeLock().lock();
        try {
            store.remove(chatId);
            deleteFile(chatId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ─── File I/O ───

    private void saveToFile(String chatId, ConversationState state) {
        if (state == null) return;
        File file = new File(stateDir, chatId + ".json");
        try {
            objectMapper.writeValue(file, state);
        } catch (IOException e) {
            log.error("[NLU] failed to save conversation state to file for chatId={}", chatId, e);
        }
    }

    private ConversationState loadFromFile(String chatId) {
        File file = new File(stateDir, chatId + ".json");
        if (!file.exists()) {
            return null;
        }
        try {
            return objectMapper.readValue(file, ConversationState.class);
        } catch (IOException e) {
            log.error("[NLU] failed to load conversation state from file for chatId={}", chatId, e);
            return null;
        }
    }

    private void deleteFile(String chatId) {
        File file = new File(stateDir, chatId + ".json");
        if (file.exists()) {
            file.delete();
        }
    }
}
