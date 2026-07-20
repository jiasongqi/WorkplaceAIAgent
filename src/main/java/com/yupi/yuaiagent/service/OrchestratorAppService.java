package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.agent.OrchestratorAgent;
import com.yupi.yuaiagent.auth.AuthPrincipal;
import com.yupi.yuaiagent.auth.UserQuotaService;
import com.yupi.yuaiagent.auth.UserRole;
import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.message.ChatMemoryAdapter;
import com.yupi.yuaiagent.message.MessageStatus;
import com.yupi.yuaiagent.message.PersistentChatMessage;
import com.yupi.yuaiagent.session.SessionManager;
import com.yupi.yuaiagent.usage.UsageEventType;
import com.yupi.yuaiagent.usage.UsageTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorAppService {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final OrchestratorAgent orchestratorAgent;
    private final SessionManager sessionManager;
    private final UsageTracker usageTracker;
    private final UserQuotaService userQuotaService;
    private final ChatMemoryAdapter chatMemoryAdapter;

    public SseEmitter chatStream(String userId, String chatId, String message) {
        return chatStream(new AuthPrincipal(userId, "user", UserRole.GUEST), chatId, message);
    }

    public SseEmitter chatStream(AuthPrincipal principal, String chatId, String message) {
        if (message == null || message.isBlank()) {
            throw BusinessException.badRequest("消息不能为空");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw BusinessException.badRequest("消息过长，请控制在 " + MAX_MESSAGE_LENGTH + " 字以内");
        }

        String userId = principal.userId();
        if (!sessionManager.isOwner(userId, chatId)) {
            throw BusinessException.forbidden();
        }

        userQuotaService.checkAndConsumeChat(userId, principal.role());

        sessionManager.updateTitle(chatId, message);
        String requestId = UUID.randomUUID().toString().replace("-", "");
        long start = System.currentTimeMillis();
        SseEmitter emitter = orchestratorAgent.chatStream(message, chatId, userId, requestId);
        // Note: chatStream returns immediately while work is async; duration here is handoff latency.
        // Per-turn approxTokens/durationMs are on SSE event "usage".
        usageTracker.track(userId, UsageEventType.CHAT, null, Math.max(1, System.currentTimeMillis() - start));
        return emitter;
    }

    /**
     * Resume a previously streamed assistant message after SSE disconnect.
     * Does not consume daily quota (replay only).
     */
    public SseEmitter resumeStream(AuthPrincipal principal, String chatId, String messageId) {
        if (!StringUtils.hasText(messageId)) {
            throw BusinessException.badRequest("messageId 不能为空");
        }
        if (!sessionManager.isOwner(principal.userId(), chatId)) {
            throw BusinessException.forbidden();
        }
        PersistentChatMessage msg = chatMemoryAdapter.findByMessageId(messageId);
        if (msg == null || !chatId.equals(msg.getChatId())) {
            throw BusinessException.notFound("消息");
        }

        SseEmitter emitter = new SseEmitter(60_000L);
        CompletableFuture.runAsync(() -> {
            try {
                MessageStatus status = msg.getStatus() != null ? msg.getStatus() : MessageStatus.COMPLETE;
                String text = StringUtils.hasText(msg.getContent())
                        ? msg.getContent()
                        : (msg.getPartialContent() != null ? msg.getPartialContent() : "");

                emitter.send(SseEmitter.event().name("message-start")
                        .data("{\"assistantMessageId\":\"" + messageId
                                + "\",\"resume\":true,\"status\":\"" + status.name() + "\"}"));

                if (StringUtils.hasText(text)) {
                    // Chunk for smoother UI; resume is a replay
                    int chunk = 80;
                    for (int i = 0; i < text.length(); i += chunk) {
                        emitter.send(SseEmitter.event().name("message")
                                .data(text.substring(i, Math.min(i + chunk, text.length()))));
                    }
                }

                if (status == MessageStatus.STREAMING) {
                    chatMemoryAdapter.markAssistantPartial(messageId);
                    emitter.send(SseEmitter.event().name("status")
                            .data("{\"resume\":\"partial\",\"hint\":\"回答在断线时中断，已恢复已生成部分\"}"));
                } else if (status == MessageStatus.PARTIAL) {
                    emitter.send(SseEmitter.event().name("status")
                            .data("{\"resume\":\"partial\",\"hint\":\"这是中断前的部分回答\"}"));
                }

                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("[resume] failed chatId={} messageId={}", chatId, messageId, e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("续传失败：" + e.getMessage()));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });
        return emitter;
    }
}