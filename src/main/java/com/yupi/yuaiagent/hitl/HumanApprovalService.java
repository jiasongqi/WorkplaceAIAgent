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
        return """
                【需要人工确认】操作类型：%s
                说明：%s
                审批 ID：%s
                请调用 POST /api/hitl/approve?approvalId=%s 确认后再执行。
                """.formatted(req.getActionType(), req.getSummary(), req.getApprovalId(), req.getApprovalId());
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
