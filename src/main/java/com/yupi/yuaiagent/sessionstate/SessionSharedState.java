package com.yupi.yuaiagent.sessionstate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-scoped shared scratchpad for multi-agent handoff.
 * <p>
 * Conversation text remains in PersistentMessage; this holds structured facts
 * (appointments, active goal, last handoff) that any specialist can read.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSharedState {

    private String chatId;
    private String userId;

    /** Current user-facing goal, e.g. "查看预约日程". */
    private String activeGoal;

    /** Last routed specialist memory type / intent name. */
    private String lastAgentType;

    /** Last explicit handoff note for the next agent (legacy one-liner). */
    private String lastHandoffNote;

    private LocalDateTime lastHandoffAt;

    /** Structured four-quadrant handoff packet (preferred over lastHandoffNote alone). */
    private HandoffPacket lastHandoffPacket;

    /** Handoff hop counter (TTL); incremented on specialist switch. */
    @Builder.Default
    private int hopCount = 0;

    /** Agents that have touched this session (dead-loop detection). */
    @Builder.Default
    private List<String> agentChain = new ArrayList<>();

    @Builder.Default
    private List<AppointmentFact> appointments = new ArrayList<>();

    /** Open questions the user still needs to answer (optional). */
    @Builder.Default
    private List<String> openQuestions = new ArrayList<>();

    /** Small key-value facts (name, contact, topic, …) — immutable across summary compression. */
    @Builder.Default
    private Map<String, String> facts = new LinkedHashMap<>();

    /**
     * Latest perception prompt block (resume/offer preprocess).
     * Kept out of URL/SSE query; injected via Shared State each turn.
     */
    private String lastPerceptionBlock;

    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppointmentFact {
        private String appointmentId;
        private String name;
        private String contact;
        private String topic;
        private String appointmentTime;
        private String status;
        private LocalDateTime recordedAt;
    }
}
