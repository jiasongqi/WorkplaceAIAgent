package com.yupi.yuaiagent.nlu;

import com.yupi.yuaiagent.agent.AgentIntent;

/**
 * NLU output record — consumed by OrchestratorAgent (Phase 1) and WorkflowMatcher (Phase 2).
 *
 * <p>Phase 1: OrchestratorAgent uses toAgentIntent() for routing.
 * <p>Phase 2: WorkflowMatcher uses specificRoute for score-based multi-source routing.
 *
 * @author jsq
 */
public record RouteHint(
    /** Generic intent category (e.g., "QUERY_DATA"). */
    String intent,
    /** Specific route hint in dot notation (e.g., "advertiser.query.roi"). May be null. */
    String specificRoute,
    /** Confidence score (Top1 - Top2 gap). */
    double confidence,
    /** Extracted entity. */
    String entity,
    /** Extracted metric. */
    String metric,
    /** Extracted time range. */
    String timeRange
) {
    /**
     * Phase 1 routing: intent → AgentIntent.
     */
    public AgentIntent toAgentIntent() {
        NluIntent nluIntent;
        try {
            nluIntent = NluIntent.valueOf(intent);
        } catch (IllegalArgumentException e) {
            nluIntent = NluIntent.UNKNOWN;
        }
        return nluIntent.toAgentIntent();
    }
}
