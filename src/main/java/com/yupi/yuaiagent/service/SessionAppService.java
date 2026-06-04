package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.dto.RenameRequest;
import com.yupi.yuaiagent.dto.SessionSearchResponse;
import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.favorite.FavoriteRepository;
import com.yupi.yuaiagent.message.ChatMemoryAdapter;
import com.yupi.yuaiagent.message.PersistentChatMessage;
import com.yupi.yuaiagent.search.ChatSearchService;
import com.yupi.yuaiagent.session.SessionManager;
import com.yupi.yuaiagent.session.SessionManager.SessionInfo;
import com.yupi.yuaiagent.session.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Session application service — owns all session use cases.
 * Controller is a thin HTTP adapter that only binds params and calls this service.
 *
 * @author jsq
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionAppService {

    private final SessionManager sessionManager;
    private final ChatMemoryAdapter chatMemoryAdapter;
    private final ChatSearchService chatSearchService;
    private final FavoriteRepository favoriteRepository;

    // ─── Query ───

    public List<SessionInfo> listActive(String userId) {
        return sessionManager.getUserSessions(userId);
    }

    public List<SessionInfo> listArchived(String userId) {
        return sessionManager.getSessionsByStatus(userId, SessionStatus.ARCHIVED);
    }

    public List<SessionInfo> listTrash(String userId) {
        return sessionManager.getSessionsByStatus(userId, SessionStatus.DELETED);
    }

    public List<PersistentChatMessage> getMessages(String userId, String chatId) {
        ensureOwnership(userId, chatId);
        return chatMemoryAdapter.getMessagesForDisplay(chatId);
    }

    public List<SessionSearchResponse> search(String userId, String keyword) {
        List<ChatSearchService.SessionSearchResult> results = chatSearchService.search(keyword, userId);
        return results.stream().map(r -> new SessionSearchResponse(
                r.chatId(),
                r.title(),
                r.relevance(),
                r.snippet(),
                r.bestHit() != null
                        ? new SessionSearchResponse.SearchHitResponse(r.bestHit().messageId(), r.bestHit().offset(), r.bestHit().length())
                        : null,
                r.timestamp()
        )).toList();
    }

    // ─── Create ───

    public SessionInfo create(String userId, String title) {
        return sessionManager.createSession(userId, title);
    }

    // ─── Update ───

    public void rename(String userId, String chatId, String newTitle) {
        ensureOwnership(userId, chatId);
        boolean ok = sessionManager.rename(userId, chatId, newTitle);
        if (!ok) throw BusinessException.forbidden();
    }

    public void archive(String userId, String chatId) {
        ensureOwnership(userId, chatId);
        boolean ok = sessionManager.archive(userId, chatId);
        if (!ok) throw BusinessException.forbidden();
    }

    public void unarchive(String userId, String chatId) {
        ensureOwnership(userId, chatId);
        boolean ok = sessionManager.unarchive(userId, chatId);
        if (!ok) throw BusinessException.forbidden();
    }

    public void restore(String userId, String chatId) {
        ensureOwnership(userId, chatId);
        boolean ok = sessionManager.updateStatus(userId, chatId, SessionStatus.ACTIVE);
        if (!ok) throw BusinessException.forbidden();
    }

    // ─── Delete ───

    public void softDelete(String userId, String chatId) {
        ensureOwnership(userId, chatId);
        boolean ok = sessionManager.softDelete(userId, chatId);
        if (!ok) throw BusinessException.forbidden();
        // Mark favorites from this session as orphaned
        favoriteRepository.markOrphanedByChatId(chatId);
    }

    // ─── Internal ───

    private void ensureOwnership(String userId, String chatId) {
        if (!sessionManager.isOwner(userId, chatId)) {
            throw BusinessException.forbidden();
        }
    }
}
