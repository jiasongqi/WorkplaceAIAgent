package com.yupi.yuaiagent.session;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器：服务端管理 chatId，防止用户访问他人对话
 */
@Component
@Slf4j
public class SessionManager {

    // userId -> List<SessionInfo>
    private final Map<String, List<SessionInfo>> userSessions = new ConcurrentHashMap<>();
    // chatId -> userId（反向索引，用于鉴权）
    private final Map<String, String> chatOwner = new ConcurrentHashMap<>();

    /**
     * 为用户创建新会话
     */
    public SessionInfo createSession(String userId, String title) {
        String chatId = UUID.randomUUID().toString();
        SessionInfo session = new SessionInfo(chatId, title, LocalDateTime.now());
        userSessions.computeIfAbsent(userId, k -> new ArrayList<>()).add(0, session);
        chatOwner.put(chatId, userId);
        log.info("用户 {} 创建会话 {}", userId, chatId);
        return session;
    }

    /**
     * 获取用户的所有会话列表（按时间倒序）
     */
    public List<SessionInfo> getUserSessions(String userId) {
        return userSessions.getOrDefault(userId, Collections.emptyList());
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
        String userId = chatOwner.get(chatId);
        if (userId == null) return;
        userSessions.getOrDefault(userId, Collections.emptyList()).stream()
                .filter(s -> s.getChatId().equals(chatId))
                .findFirst()
                .ifPresent(s -> s.setTitle(title.length() > 20 ? title.substring(0, 20) + "..." : title));
    }

    /**
     * 删除会话
     */
    public boolean deleteSession(String userId, String chatId) {
        if (!isOwner(userId, chatId)) return false;
        List<SessionInfo> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.removeIf(s -> s.getChatId().equals(chatId));
        }
        chatOwner.remove(chatId);
        return true;
    }

    @Data
    public static class SessionInfo {
        private String chatId;
        private String title;
        private LocalDateTime createdAt;
        private LocalDateTime lastActiveAt;

        public SessionInfo(String chatId, String title, LocalDateTime createdAt) {
            this.chatId = chatId;
            this.title = title;
            this.createdAt = createdAt;
            this.lastActiveAt = createdAt;
        }
    }
}
