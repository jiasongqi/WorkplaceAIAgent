package com.yupi.yuaiagent.hitl;

import com.yupi.yuaiagent.sessionstate.HandoffPacket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Conversation-level human handoff ticket — parks a Handoff Packet without blocking SSE.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HumanHandoffTicket {

    public enum Status {
        WAITING_FOR_HUMAN,
        RESUMED,
        CANCELLED,
        EXPIRED
    }

    private String handoffId;
    private String chatId;
    private String userId;
    private Status status;
    /** Why the machine escalated (hop_ttl / ping_pong / quality_exhausted / explicit). */
    private String parkReason;
    private String parkSummary;
    private HandoffPacket packet;
    /** Filled when human resumes (API or next chat turn). */
    private String humanInput;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant resumedAt;
}
