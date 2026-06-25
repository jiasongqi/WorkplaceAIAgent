package com.yupi.yuaiagent.nlu;

import lombok.Data;

/**
 * Per-chat structured slot state for multi-turn NLU.
 * Supports incremental merge with context-shift awareness.
 *
 * @author jsq
 */
@Data
public class ConversationState {

    /** Main entity (e.g., "腾讯资方"). */
    private String entity;

    /** Metric (e.g., "ROI", "CVR"). */
    private String metric;

    /** Time range (e.g., "7d", "1d", "this_month"). */
    private String timeRange;

    /** Dimension (e.g., "by_channel", "by_city"). */
    private String dimension;

    /** Last resolved intent. */
    private NluIntent resolvedIntent;

    /** Last confidence score (Top1 - Top2 gap). */
    private double confidence;

    /** Last update timestamp. */
    private long lastUpdateTime;

    /** Optimistic concurrency version — incremented on each merge. */
    private long version;

    /**
     * Smart merge V4.2 — three-way ShiftType.
     *
     * <ul>
     *   <li>FOLLOW_UP: inherit everything, overlay mentioned fields</li>
     *   <li>ENTITY_SWITCH: inherit metric/timeRange/dimension, switch entity</li>
     *   <li>NEW_QUERY: use fresh values, discard stale context</li>
     * </ul>
     */
    public ConversationState smartMerge(ConversationState fresh, NluIntent newIntent,
                                         ContextShiftDetector.ShiftType shiftType) {
        ConversationState result = new ConversationState();
        result.lastUpdateTime = System.currentTimeMillis();
        result.resolvedIntent = newIntent;
        result.confidence = fresh.confidence;
        result.version = this.version + 1;

        switch (shiftType) {
            case FOLLOW_UP -> {
                result.entity = fresh.entity != null ? fresh.entity : this.entity;
                result.metric = fresh.metric != null ? fresh.metric : this.metric;
                result.timeRange = fresh.timeRange != null ? fresh.timeRange : this.timeRange;
                result.dimension = fresh.dimension != null ? fresh.dimension : this.dimension;
            }
            case ENTITY_SWITCH -> {
                result.entity = fresh.entity != null ? fresh.entity : this.entity;
                result.metric = fresh.metric != null ? fresh.metric : this.metric;
                result.timeRange = fresh.timeRange != null ? fresh.timeRange : this.timeRange;
                result.dimension = fresh.dimension != null ? fresh.dimension : this.dimension;
            }
            case NEW_QUERY -> {
                result.entity = fresh.entity;
                result.metric = fresh.metric;
                result.timeRange = fresh.timeRange;
                result.dimension = fresh.dimension;
            }
        }

        return result;
    }
}
