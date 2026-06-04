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
import java.util.stream.Collectors;

/**
 * Session manager with three-state lifecycle: ACTIVE / ARCHIVED / DELETED.
 * <p>
 * File-based persistence. Sessions are soft-deleted (status → DELETED),
 * physically cleaned up after 30 days by SessionCleanupJob.
 *
 * @author jsq
 */
@Component
@Slf4j
public class SessionManager {

    @Value("${session.storage.dir:./tmp/sessions}")
    private String storageDir;

    private final ObjectMapper objectMapper;

    // userId -> List<SessionInfo>
    private final Map<String, List<SessionInfo>> userSessions = new ConcurrentHashMap<>();
    // chatId -> userId (reverse index for auth)
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
            log.info("Session storage initialized, path: {}", storageFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to initialize session storage", e);
        }
    }

    // ─── Create ───

    public SessionInfo createSession(String userId, String title) {
        lock.writeLock().lock();
        try {
            String chatId = UUID.randomUUID().toString();
            SessionInfo session = new SessionInfo(chatId, title, LocalDateTime.now());
            userSessions.computeIfAbsent(userId, k -> new ArrayList<>()).add(0, session);
            chatOwner.put(chatId, userId);
            saveToFile();
            log.info("User {} created session {}", userId, chatId);
            return session;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── Read ───

    /** Returns ACTIVE sessions for a user (newest first). */
    public List<SessionInfo> getUserSessions(String userId) {
        lock.readLock().lock();
        try {
            return userSessions.getOrDefault(userId, Collections.emptyList()).stream()
                    .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Returns sessions filtered by status. */
    public List<SessionInfo> getSessionsByStatus(String userId, SessionStatus status) {
        lock.readLock().lock();
        try {
            return userSessions.getOrDefault(userId, Collections.emptyList()).stream()
                    .filter(s -> s.getStatus() == status)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Finds a session by chatId (any status). */
    public SessionInfo findByChatId(String chatId) {
        String userId = chatOwner.get(chatId);
        if (userId == null) return null;
        lock.readLock().lock();
        try {
            return userSessions.getOrDefault(userId, Collections.emptyList()).stream()
                    .filter(s -> s.getChatId().equals(chatId))
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isOwner(String userId, String chatId) {
        return userId.equals(chatOwner.get(chatId));
    }

    // ─── Update ───

    /** Updates session title (auto-set from first message). */
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

    /** Renames a session (user-initiated). */
    public boolean rename(String userId, String chatId, String newTitle) {
        lock.writeLock().lock();
        try {
            if (!isOwner(userId, chatId)) return false;
            userSessions.getOrDefault(userId, Collections.emptyList()).stream()
                    .filter(s -> s.getChatId().equals(chatId))
                    .findFirst()
                    .ifPresent(s -> {
                        s.setTitle(newTitle);
                        s.setLastActiveAt(LocalDateTime.now());
                    });
            saveToFile();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Archives a session (ACTIVE → ARCHIVED). */
    public boolean archive(String userId, String chatId) {
        return updateStatus(userId, chatId, SessionStatus.ARCHIVED);
    }

    /** Unarchives a session (ARCHIVED → ACTIVE). */
    public boolean unarchive(String userId, String chatId) {
        return updateStatus(userId, chatId, SessionStatus.ACTIVE);
    }

    /** Updates session status. */
    public boolean updateStatus(String userId, String chatId, SessionStatus newStatus) {
        lock.writeLock().lock();
        try {
            if (!isOwner(userId, chatId)) return false;
            userSessions.getOrDefault(userId, Collections.emptyList()).stream()
                    .filter(s -> s.getChatId().equals(chatId))
                    .findFirst()
                    .ifPresent(s -> {
                        s.setStatus(newStatus);
                        LocalDateTime now = LocalDateTime.now();
                        s.setLastActiveAt(now);
                        if (newStatus == SessionStatus.ARCHIVED) {
                            s.setArchivedAt(now);
                        } else if (newStatus == SessionStatus.DELETED) {
                            s.setDeletedAt(now);
                        }
                    });
            saveToFile();
            log.info("Session {} status updated to {}", chatId, newStatus);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── Delete ───

    /**
     * Soft delete: status → DELETED. Does NOT remove from memory or chatOwner.
     * Physical cleanup happens after 30 days via SessionCleanupJob.
     */
    public boolean softDelete(String userId, String chatId) {
        return updateStatus(userId, chatId, SessionStatus.DELETED);
    }

    /**
     * Physical delete: removes from memory and chatOwner. Used by cleanup job
     * or user-initiated permanent delete.
     */
    public boolean physicalDelete(String chatId) {
        lock.writeLock().lock();
        try {
            String userId = chatOwner.remove(chatId);
            if (userId == null) return false;
            List<SessionInfo> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.removeIf(s -> s.getChatId().equals(chatId));
            }
            saveToFile();
            log.info("Session {} physically deleted", chatId);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Finds all DELETED sessions older than the given cutoff. */
    public List<SessionInfo> findExpiredDeleted(LocalDateTime cutoff) {
        lock.readLock().lock();
        try {
            return userSessions.values().stream()
                    .flatMap(List::stream)
                    .filter(s -> s.getStatus() == SessionStatus.DELETED)
                    .filter(s -> s.getDeletedAt() != null && s.getDeletedAt().isBefore(cutoff))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── File I/O ───

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
                log.info("Loaded sessions: {} users, {} chat mappings",
                        userSessions.size(), chatOwner.size());
            } catch (IOException e) {
                log.error("Failed to load session file", e);
            }
        }
    }

    private void saveToFile() {
        if (storageFile == null) return;
        try {
            SessionStore store = new SessionStore();
            store.setUserSessions(new HashMap<>(userSessions));
            store.setChatOwner(new HashMap<>(chatOwner));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, store);
        } catch (IOException e) {
            log.error("Failed to save session file", e);
        }
    }

    // ─── Inner classes ───

    @Data
    public static class SessionStore {
        private Map<String, List<SessionInfo>> userSessions = new HashMap<>();
        private Map<String, String> chatOwner = new HashMap<>();
    }

    @Data
    public static class SessionInfo {
        private String chatId;
        private String title;
        private SessionStatus status = SessionStatus.ACTIVE;
        private LocalDateTime createdAt;
        private LocalDateTime lastActiveAt;
        private LocalDateTime archivedAt;
        private LocalDateTime deletedAt;

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
