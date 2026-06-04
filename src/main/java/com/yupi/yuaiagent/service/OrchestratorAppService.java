package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.agent.OrchestratorAgent;
import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Orchestrator application service — owns the chat use case.
 * Validates input, authenticates, checks ownership, then delegates to OrchestratorAgent.
 *
 * @author jsq
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorAppService {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final OrchestratorAgent orchestratorAgent;
    private final SessionManager sessionManager;

    /**
     * Starts a streaming chat via OrchestratorAgent.
     *
     * @param userId  authenticated user ID
     * @param chatId  the chat session ID
     * @param message the user message
     * @return SseEmitter for streaming response
     */
    public SseEmitter chatStream(String userId, String chatId, String message) {
        // Validate input
        if (message == null || message.isBlank()) {
            throw BusinessException.badRequest("消息不能为空");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw BusinessException.badRequest("消息过长，请控制在 " + MAX_MESSAGE_LENGTH + " 字以内");
        }

        // Check ownership
        if (!sessionManager.isOwner(userId, chatId)) {
            throw BusinessException.forbidden();
        }

        // Update session title from first message
        sessionManager.updateTitle(chatId, message);

        // Delegate to OrchestratorAgent
        String requestId = UUID.randomUUID().toString().replace("-", "");
        return orchestratorAgent.chatStream(message, chatId, userId, requestId);
    }
}
