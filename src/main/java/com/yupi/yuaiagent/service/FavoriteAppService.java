package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.dto.AddFavoriteRequest;
import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.favorite.Favorite;
import com.yupi.yuaiagent.favorite.FavoriteRepository;
import com.yupi.yuaiagent.message.PersistentChatMessage;
import com.yupi.yuaiagent.message.PersistentMessageRepository;
import com.yupi.yuaiagent.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Favorite application service — owns all favorite use cases.
 *
 * @author jsq
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteAppService {

    private final FavoriteRepository favoriteRepository;
    private final PersistentMessageRepository messageRepository;
    private final SessionManager sessionManager;

    public Favorite add(String userId, AddFavoriteRequest request) {
        // Try to fetch message by ID first, fall back to request content
        PersistentChatMessage msg = request.messageId() != null && !request.messageId().isBlank()
                ? messageRepository.findByMessageId(request.messageId())
                : null;
        SessionManager.SessionInfo session = sessionManager.findByChatId(request.chatId());

        String content = msg != null ? msg.getContent()
                : (request.content() != null ? request.content() : "[无内容]");
        String role = msg != null ? msg.getRole()
                : (request.role() != null ? request.role() : "unknown");

        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setChatId(request.chatId());
        fav.setMessageId(request.messageId());
        fav.setContentSnapshot(content);
        fav.setRole(role);
        fav.setSessionTitleSnapshot(session != null ? session.getTitle() : "[会话已删除]");

        return favoriteRepository.add(fav);
    }

    public void remove(String userId, String favoriteId) {
        boolean ok = favoriteRepository.remove(userId, favoriteId);
        if (!ok) throw BusinessException.notFound("收藏");
    }

    public List<Favorite> list(String userId) {
        return favoriteRepository.findByUserId(userId);
    }
}
