package com.yupi.yuaiagent.sessionstate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-backed store for {@link SessionSharedState}, keyed by chatId.
 */
@Slf4j
@Repository
public class SessionSharedStateStore {

    @Value("${session.shared-state.storage.dir:./tmp/session-shared-state}")
    private String storageDir;

    private final ObjectMapper objectMapper;
    private final Map<String, SessionSharedState> byChatId = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private File storageFile;

    public SessionSharedStateStore() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            File dir = new File(storageDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            storageFile = new File(dir, "shared-state.json");
            loadFromFile();
            log.info("SessionSharedState 存储初始化完成: {}", storageFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("初始化 SessionSharedState 存储失败", e);
        }
    }

    public Optional<SessionSharedState> findByChatId(String chatId) {
        if (!StringUtils.hasText(chatId)) {
            return Optional.empty();
        }
        lock.readLock().lock();
        try {
            return Optional.ofNullable(byChatId.get(chatId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public SessionSharedState save(SessionSharedState state) {
        if (state == null || !StringUtils.hasText(state.getChatId())) {
            throw new IllegalArgumentException("chatId required");
        }
        lock.writeLock().lock();
        try {
            byChatId.put(state.getChatId(), state);
            persist();
            return state;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void loadFromFile() {
        if (storageFile == null || !storageFile.exists()) {
            return;
        }
        try {
            Map<String, SessionSharedState> loaded = objectMapper.readValue(
                    storageFile, new TypeReference<>() {});
            if (loaded != null) {
                byChatId.putAll(loaded);
            }
        } catch (Exception e) {
            log.error("加载 SessionSharedState 失败", e);
        }
    }

    private void persist() {
        if (storageFile == null) {
            return;
        }
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storageFile, byChatId);
        } catch (Exception e) {
            log.error("持久化 SessionSharedState 失败", e);
        }
    }
}
