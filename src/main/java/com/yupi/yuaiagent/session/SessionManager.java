package com.yupi.yuaiagent.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 会话管理器：服务端管理 chatId，防止用户访问他人对话。
 *
 * <p>基于文件的持久化存储（风格与 AppointmentRepository 一致），
 * 服务重启后会话不丢失。如需多实例部署，可替换为数据库实现。
 */
@Component
@Slf4j
public class SessionManager {

    @Value("${session.storage.dir:./tmp/sessions}")
    private String storageDir;

    private final ObjectMapper objectMapper;

    // userId -> List<SessionInfo>
    private final Map<String, List<SessionInfo>> userSessions = new ConcurrentHashMap<>();
    // chatId -> userId（反向索引，用于鉴权）
    private final Map<String, String> chatOwner = new ConcurrentHashMap<>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private File storageFile;

    public SessionManager() {
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
            storageFile = new File(dir, "sessions.json");
            loadFromFile();
            log.info("会话存储初始化完成，存储路径：{}", storageFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("初始化会话存储失败", e);
        }
    }

    /**
     * 为用户创建新会话
     */
    public SessionInfo createSession(String userId, String title) {
        lock.writeLock().lock();
        try {
            String chatId = UUID.randomUUID().toString();
            SessionInfo session = new SessionInfo(chatId, title, LocalDateTime.now());
            userSessions.computeIfAbsent(userId, k -> new ArrayList<>()).add(0, session);
            chatOwner.put(chatId, userId);
            saveToFile();
            log.info("用户 {} 创建会话 {}", userId, chatId);
            return session;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取用户的所有会话列表（按时间倒序）
     */
    public List<SessionInfo> getUserSessions(String userId) {
        lock.readLock().lock();
        try {
            return userSessions.getOrDefault(userId, Collections.emptyList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 验证 chatId 是否属于该用户
     */
    public boolean isOwner(String userId, String chatId) {
        return userId.equals(chatOwner.get(chatId));
    }

    /**
     * 更新会话标题（用第一条消息作为标题）
     */
    public void updateTitle(String chatId, String title) {
        lock.writeLock().lock();
        try {
            String userId = chatOwner.get(chatId);
            if (userId == null) return;
            userSessions.getOrDefault(userId, Collections.emptyList()).stream()
                    .filter(s -> s.getChatId().equals(chatId))
                    .findFirst()
                    .ifPresent(s -> {
                        s.setTitle(title.length() > 20 ? title.substring(0, 20) + "..." : title);
                        s.setLastActiveAt(LocalDateTime.now());
                    });
            saveToFile();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 删除会话
     */
    public boolean deleteSession(String userId, String chatId) {
        lock.writeLock().lock();
        try {
            if (!isOwner(userId, chatId)) return false;
            List<SessionInfo> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.removeIf(s -> s.getChatId().equals(chatId));
            }
            chatOwner.remove(chatId);
            saveToFile();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 从文件加载会话数据
     */
    private void loadFromFile() {
        if (storageFile.exists() && storageFile.length() > 0) {
            try {
                SessionStore store = objectMapper.readValue(storageFile, new TypeReference<SessionStore>() {});
                if (store.getUserSessions() != null) {
                    userSessions.putAll(store.getUserSessions());
                }
                if (store.getChatOwner() != null) {
                    chatOwner.putAll(store.getChatOwner());
                }
                log.info("从文件加载会话：用户 {} 个，会话归属 {} 条",
                        userSessions.size(), chatOwner.size());
            } catch (IOException e) {
                log.error("加载会话文件失败", e);
            }
        }
    }

    /**
     * 保存会话数据到文件
     */
    private void saveToFile() {
        if (storageFile == null) return;
        try {
            SessionStore store = new SessionStore();
            store.setUserSessions(new HashMap<>(userSessions));
            store.setChatOwner(new HashMap<>(chatOwner));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, store);
        } catch (IOException e) {
            log.error("保存会话文件失败", e);
        }
    }

    /**
     * 持久化容器
     */
    @Data
    public static class SessionStore {
        private Map<String, List<SessionInfo>> userSessions = new HashMap<>();
        private Map<String, String> chatOwner = new HashMap<>();
    }

    @Data
    public static class SessionInfo {
        private String chatId;
        private String title;
        private LocalDateTime createdAt;
        private LocalDateTime lastActiveAt;

        /** Jackson 反序列化需要无参构造器 */
        public SessionInfo() {
        }

        public SessionInfo(String chatId, String title, LocalDateTime createdAt) {
            this.chatId = chatId;
            this.title = title;
            this.createdAt = createdAt;
            this.lastActiveAt = createdAt;
        }
    }
}
