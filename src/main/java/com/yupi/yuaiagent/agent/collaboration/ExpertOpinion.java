package com.yupi.yuaiagent.agent.collaboration;

import com.yupi.yuaiagent.agent.AgentIntent;

/**
 * One expert agent's opinion in a parallel collaboration round.
 *
 * @param intent   which specialist produced this opinion
 * @param answer   raw answer text (may be empty on failure)
 * @param success  whether the agent completed without exception
 * @param error    failure message if unsuccessful
 * @param durationMs wall-clock time for this expert
 */
public record ExpertOpinion(
        AgentIntent intent,
        String answer,
        boolean success,
        String error,
        long durationMs
) {
    public static ExpertOpinion ok(AgentIntent intent, String answer, long durationMs) {
        return new ExpertOpinion(intent, answer == null ? "" : answer, true, null, durationMs);
    }

    public static ExpertOpinion failed(AgentIntent intent, String error, long durationMs) {
        return new ExpertOpinion(intent, "", false, error, durationMs);
    }
}
