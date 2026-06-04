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
        // Fetch message for snapshot
        PersistentChatMessage msg = messageRepository.findByMessageId(request.messageId());
        SessionManager.SessionInfo session = sessionManager.findByChatId(request.chatId());

        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setChatId(request.chatId());
        fav.setMessageId(request.messageId());
        fav.setContentSnapshot(msg != null ? msg.getContent() : "[消息已删除]");
        fav.setRole(msg != null ? msg.getRole() : "unknown");
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
