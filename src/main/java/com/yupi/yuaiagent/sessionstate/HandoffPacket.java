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
 * Structured Agent Handoff Packet — Meta / Mission / Context / Artifacts.
 * <p>
 * Inspired by defensive multi-agent handoff: compress prior work into conclusions
 * and references; never dump full chat history into the next specialist.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoffPacket {

    public static final int DEFAULT_MAX_HOPS = 5;

    private Meta meta;
    private Mission mission;
    private ContextBlock context;
    private Artifacts artifacts;

    /** Minimum tool patterns allowed after handoff (permission downgrade). */
    @Builder.Default
    private List<String> scope = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private String handoffId;
        private String sourceAgent;
        private String targetAgent;
        private LocalDateTime timestamp;
        /** Increments on every specialist switch; used as TTL against ping-pong loops. */
        @Builder.Default
        private int hopCount = 1;
        /** Agents that already touched this session chain (dead-loop detection). */
        @Builder.Default
        private List<String> chain = new ArrayList<>();
        private String traceId;
        @Builder.Default
        private String priority = "normal";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mission {
        /** What the next agent must do. */
        private String objective;
        /** Definition of done. */
        private String definitionOfDone;
        /** Hard constraints / bans. */
        @Builder.Default
        private List<String> constraints = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextBlock {
        private String summarySoFar;
        private String userOriginalIntent;
        /** Immutable key facts — must survive summary truncation. */
        @Builder.Default
        private Map<String, String> keyFacts = new LinkedHashMap<>();
        /** Optional back-link to full trace / prior agent log. */
        private String backLinkTraceId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Artifacts {
        @Builder.Default
        private List<String> appointmentIds = new ArrayList<>();
        @Builder.Default
        private List<String> artifactIds = new ArrayList<>();
        @Builder.Default
        private List<String> fileUris = new ArrayList<>();
        /** Validated = true only after system-layer existence check. */
        @Builder.Default
        private boolean validated = false;
    }
}
