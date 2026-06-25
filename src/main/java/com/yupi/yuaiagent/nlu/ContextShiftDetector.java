package com.yupi.yuaiagent.nlu;

/**
 * Determines whether the current message is a follow-up, entity switch, or new query.
 *
 * <p>Phase 1: {@link RuleContextShiftDetector} (heuristic rules)
 * <p>Phase 2: EmbeddingContextShiftDetector (embedding similarity)
 *
 * @author jsq
 */
public interface ContextShiftDetector {

    ShiftType detect(String message, ConversationState previousState,
                     UnifiedNluExtractor.NluExtraction extraction);

    /**
     * Three-way shift classification.
     *
     * <ul>
     *   <li>FOLLOW_UP: same topic, same entity — "昨天呢", "ROI呢"</li>
     *   <li>ENTITY_SWITCH: same topic, different entity — "百度呢", "那快手呢"</li>
     *   <li>NEW_QUERY: new topic — "查百度数据", "帮我分析快手"</li>
     * </ul>
     */
    enum ShiftType {
        FOLLOW_UP,
        ENTITY_SWITCH,
        NEW_QUERY
    }
}
