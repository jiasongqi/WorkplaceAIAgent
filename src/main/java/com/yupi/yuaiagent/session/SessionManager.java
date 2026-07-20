package com.yupi.yuaiagent.session;

import com.yupi.yuaiagent.repository.entity.ChatSessionEntity;
import com.yupi.yuaiagent.repository.jpa.ChatSessionJpaRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Session manager with three-state lifecycle: ACTIVE / ARCHIVED / DELETED.
 * <p>
 * JPA persistence. Sessions are soft-deleted (status → DELETED),
 * physically cleaned up after 30 days by SessionCleanupJob.
 *
 * @author jsq
 */
@Component
@Slf4j
public class SessionManager {

    private final ChatSessionJpaRepository jpaRepo;

    public SessionManager(ChatSessionJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    // ─── Create ───

    public SessionInfo createSession(String userId, String title) {
        String chatId = UUID.randomUUID().toString();
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setSessionId(chatId);
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setStatus("ACTIVE");
        entity.setState(new HashMap<>());
        jpaRepo.save(entity);
        log.info("User {} created session {}", userId, chatId);
        return toDomain(entity);
    }

    // ─── Read ───

    /** Returns ACTIVE sessions for a user (newest first). */
    public List<SessionInfo> getUserSessions(String userId) {
        return jpaRepo.findByUserId(userId).stream()
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .sorted(Comparator.comparing((ChatSessionEntity e) -> e.getCreatedAt()).reversed())
                .map(this::toDomain)
                .toList();
    }

    /** Returns sessions filtered by status. */
    public List<SessionInfo> getSessionsByStatus(String userId, SessionStatus status) {
        return jpaRepo.findByUserId(userId).stream()
                .filter(e -> status.name().equals(e.getStatus()))
                .map(this::toDomain)
                .toList();
    }

    /** Finds a session by chatId (any status). */
    public SessionInfo findByChatId(String chatId) {
        return jpaRepo.findBySessionId(chatId).map(this::toDomain).orElse(null);
    }

    public boolean isOwner(String userId, String chatId) {
        return jpaRepo.findBySessionId(chatId)
                .map(e -> userId.equals(e.getUserId()))
                .orElse(false);
    }

    // ─── Update ───

    /** Updates session title (auto-set from first message). */
    public void updateTitle(String chatId, String title) {
        jpaRepo.findBySessionId(chatId).ifPresent(entity -> {
            String truncatedTitle = title.length() > 20 ? title.substring(0, 20) + "..." : title;
            entity.setTitle(truncatedTitle);
            entity.setLastActiveAt(OffsetDateTime.now());
            jpaRepo.save(entity);
        });
    }

    /** Renames a session (user-initiated). */
    public boolean rename(String userId, String chatId, String newTitle) {
        return jpaRepo.findBySessionId(chatId)
                .filter(e -> userId.equals(e.getUserId()))
                .map(entity -> {
                    entity.setTitle(newTitle);
                    entity.setLastActiveAt(OffsetDateTime.now());
                    jpaRepo.save(entity);
                    return true;
                })
                .orElse(false);
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
        return jpaRepo.findBySessionId(chatId)
                .filter(e -> userId.equals(e.getUserId()))
                .map(entity -> {
                    entity.setStatus(newStatus.name());
                    OffsetDateTime now = OffsetDateTime.now();
                    entity.setLastActiveAt(now);
                    if (newStatus == SessionStatus.ARCHIVED) {
                        entity.setArchivedAt(now);
                    } else if (newStatus == SessionStatus.DELETED) {
                        entity.setDeletedAt(now);
                    }
                    jpaRepo.save(entity);
                    log.info("Session {} status updated to {}", chatId, newStatus);
                    return true;
                })
                .orElse(false);
    }

    // ─── Delete ───

    /** Soft delete: status → DELETED. */
    public boolean softDelete(String userId, String chatId) {
        return updateStatus(userId, chatId, SessionStatus.DELETED);
    }

    /** Physical delete: removes from database. */
    public boolean physicalDelete(String chatId) {
        return jpaRepo.findBySessionId(chatId)
                .map(entity -> {
                    jpaRepo.delete(entity);
                    log.info("Session {} physically deleted", chatId);
                    return true;
                })
                .orElse(false);
    }

    /** Finds all DELETED sessions older than the given cutoff. */
    public List<SessionInfo> findExpiredDeleted(LocalDateTime cutoff) {
        OffsetDateTime cutoffOdt = cutoff.atOffset(ZoneOffset.UTC);
        return jpaRepo.findAll().stream()
                .filter(e -> "DELETED".equals(e.getStatus()))
                .filter(e -> e.getDeletedAt() != null && e.getDeletedAt().isBefore(cutoffOdt))
                .map(this::toDomain)
                .toList();
    }

    // ─── Mapping ───

    private SessionInfo toDomain(ChatSessionEntity e) {
        SessionInfo info = new SessionInfo();
        info.setChatId(e.getSessionId());
        info.setTitle(e.getTitle());
        info.setStatus(SessionStatus.valueOf(e.getStatus() != null ? e.getStatus() : "ACTIVE"));
        info.setCreatedAt(toLocalDateTime(e.getCreatedAt()));
        info.setLastActiveAt(toLocalDateTime(e.getLastActiveAt()));
        info.setArchivedAt(toLocalDateTime(e.getArchivedAt()));
        info.setDeletedAt(toLocalDateTime(e.getDeletedAt()));
        return info;
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime odt) {
        return odt != null ? odt.toLocalDateTime() : null;
    }

    // ─── Inner classes ───

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
