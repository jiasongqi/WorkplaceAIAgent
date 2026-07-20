package com.yupi.yuaiagent.agent.collaboration;

import com.yupi.yuaiagent.agent.AgentIntent;

import java.util.List;

/**
 * Result of a multi-agent collaboration round (parallel debate + synthesis, or failover).
 *
 * @param mode            PARALLEL_DEBATE | FAILOVER | SERIAL_FALLBACK
 * @param finalAnswer     synthesized or failover answer presented to the user
 * @param opinions        per-expert opinions (empty for pure failover without prior debate)
 * @param primaryIntent   primary routing intent
 * @param failoverIntent  fallback agent used when primary failed (nullable)
 * @param failoverReason  human-readable reason for failover (nullable)
 * @param handoffArtifactId blackboard artifact id for handoff / debate record (nullable)
 */
public record CollaborationResult(
        Mode mode,
        String finalAnswer,
        List<ExpertOpinion> opinions,
        AgentIntent primaryIntent,
        AgentIntent failoverIntent,
        String failoverReason,
        String handoffArtifactId
) {
    public enum Mode {
        /** Multiple experts ran in parallel; answers synthesized */
        PARALLEL_DEBATE,
        /** Primary failed or quality rejected; another agent retried with reason */
        FAILOVER,
        /** Single expert path (no collaboration needed) */
        SINGLE
    }

    public boolean usedFailover() {
        return mode == Mode.FAILOVER && failoverIntent != null;
    }
}
