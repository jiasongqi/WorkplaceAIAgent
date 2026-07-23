package com.yupi.yuaiagent.hitl;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Human-in-the-loop approval gate for dangerous tool / side-effect operations.
 * <p>
 * Flow: requestApproval → (user approves via API) → consumeApproval → execute.
 */
@Slf4j
@Service
public class HumanApprovalService {

    public enum ActionType {
        TERMINAL_COMMAND,
        CALENDAR_CREATE,
        FILE_WRITE
    }

    public enum Status {
        PENDING, APPROVED, REJECTED, EXPIRED, CONSUMED
    }

    @Data
    @Builder
    public static class ApprovalRequest {
        private String approvalId;
        private String userId;
        private String chatId;
        private ActionType actionType;
        private String summary;
        private String payload;
        private Status status;
        private Instant createdAt;
        private Instant expiresAt;
    }

    private final HitlProperties properties;
    private final Map<String, ApprovalRequest> store = new ConcurrentHashMap<>();

    public HumanApprovalService(HitlProperties properties) {
        this.properties = properties;
    }

    public ApprovalRequest requestApproval(String userId, String chatId,
                                           ActionType actionType, String summary, String payload) {
        Instant now = Instant.now();
        ApprovalRequest req = ApprovalRequest.builder()
                .approvalId(UUID.randomUUID().toString())
                .userId(userId != null ? userId : "anonymous")
                .chatId(chatId)
                .actionType(actionType)
                .summary(summary)
                .payload(payload)
                .status(Status.PENDING)
                .createdAt(now)
                .expiresAt(now.plusSeconds(Math.max(30, properties.getApprovalTtlSeconds())))
                .build();
        store.put(req.getApprovalId(), req);
        log.info("[HITL] approval requested id={} type={} user={}", req.getApprovalId(), actionType, userId);
        return req;
    }

    public Optional<ApprovalRequest> get(String approvalId) {
        return Optional.ofNullable(store.get(approvalId)).map(this::refreshExpiry);
    }

    public ApprovalRequest approve(String approvalId, String userId) {
        ApprovalRequest req = requireOwned(approvalId, userId);
        if (req.getStatus() != Status.PENDING) {
            throw new IllegalStateException("approval not pending: " + req.getStatus());
        }
        req.setStatus(Status.APPROVED);
        return req;
    }

    public ApprovalRequest reject(String approvalId, String userId) {
        ApprovalRequest req = requireOwned(approvalId, userId);
        req.setStatus(Status.REJECTED);
        return req;
    }

    /**
     * Returns true and marks CONSUMED if this approval is valid for the action.
     * When HITL is disabled for the action type, always returns true (no token needed).
     */
    public boolean consumeIfApproved(String approvalId, ActionType actionType, String payloadHint) {
        if (!requiresApproval(actionType)) {
            return true;
        }
        if (approvalId == null || approvalId.isBlank()) {
            return false;
        }
        ApprovalRequest req = refreshExpiry(store.get(approvalId));
        if (req == null || req.getStatus() == Status.EXPIRED) {
            return false;
        }
        if (req.getStatus() != Status.APPROVED) {
            return false;
        }
        if (req.getActionType() != actionType) {
            return false;
        }
        // Optional payload binding: command must match what was approved
        if (payloadHint != null && req.getPayload() != null
                && !req.getPayload().equals(payloadHint)) {
            log.warn("[HITL] payload mismatch for approval {}", approvalId);
            return false;
        }
        req.setStatus(Status.CONSUMED);
        return true;
    }

    public boolean requiresApproval(ActionType actionType) {
        return switch (actionType) {
            case TERMINAL_COMMAND -> properties.isTerminalRequireApproval();
            case CALENDAR_CREATE -> properties.isCalendarRequireApproval();
            case FILE_WRITE -> false;
        };
    }

    public String pendingMessage(ApprovalRequest req) {
        String actionLabel = switch (req.getActionType()) {
            case CALENDAR_CREATE -> "创建日历预约";
            case TERMINAL_COMMAND -> "执行终端命令";
            case FILE_WRITE -> "写入文件";
        };
        return """
                ### 需要您确认：%s

                %s

                这是高风险操作，需您本人确认后才会执行。

                - 回复 **确认创建** 继续
                - 回复 **取消** 放弃本次操作

                <!--hitl:%s-->
                """.formatted(actionLabel, req.getSummary(), req.getApprovalId());
    }

    /** 按会话查找仍有效的待审批单（聊天二次确认用） */
    public Optional<ApprovalRequest> findPendingByChatId(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return Optional.empty();
        }
        return store.values().stream()
                .map(this::refreshExpiry)
                .filter(r -> r != null
                        && r.getStatus() == Status.PENDING
                        && chatId.equals(r.getChatId()))
                .findFirst();
    }

    private ApprovalRequest requireOwned(String approvalId, String userId) {
        ApprovalRequest req = refreshExpiry(store.get(approvalId));
        if (req == null) {
            throw new IllegalArgumentException("approval not found");
        }
        if (userId != null && req.getUserId() != null
                && !"anonymous".equals(req.getUserId())
                && !req.getUserId().equals(userId)) {
            throw new IllegalArgumentException("approval belongs to another user");
        }
        return req;
    }

    private ApprovalRequest refreshExpiry(ApprovalRequest req) {
        if (req == null) return null;
        if (req.getStatus() == Status.PENDING && Instant.now().isAfter(req.getExpiresAt())) {
            req.setStatus(Status.EXPIRED);
        }
        return req;
    }
}
