package com.yupi.yuaiagent.nlu;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Rule-based context shift detector — Phase 1.
 *
 * <p>Decision matrix:
 * <pre>
 * hasFollowUpPattern + !entityChanged → FOLLOW_UP ("昨天呢")
 * hasFollowUpPattern + entityChanged  → ENTITY_SWITCH ("百度呢")
 * noFollowUpPattern + entityChanged   → NEW_QUERY ("查百度数据")
 * noFollowUpPattern + !entityChanged  → NEW_QUERY (default, conservative)
 * </pre>
 *
 * @author jsq
 */
@Component("ruleContextShiftDetector")
public class RuleContextShiftDetector implements ContextShiftDetector {

    private static final Set<String> FOLLOW_UP_PARTICLES = Set.of(
        "呢", "吧", "了", "啊", "呀", "么", "呗"
    );

    private static final Set<String> FOLLOW_UP_PHRASES = Set.of(
        "怎么样", "表现呢", "表现如何", "啥情况", "什么情况", "看看呢"
    );

    private static final Set<String> NEW_QUERY_VERBS = Set.of(
        "查", "查询", "帮我查", "帮我看看", "分析", "帮我分析",
        "对比", "比较", "统计", "汇总", "生成", "帮我生成"
    );

    @Override
    public ShiftType detect(String message, ConversationState previousState,
                            UnifiedNluExtractor.NluExtraction extraction) {
        String trimmed = message.trim();

        // Layer 1: Explicit new-query verbs → NEW_QUERY
        for (String verb : NEW_QUERY_VERBS) {
            if (trimmed.startsWith(verb)) {
                return ShiftType.NEW_QUERY;
            }
        }

        // Layer 2: Entity changed?
        boolean entityChanged = extraction.entity() != null
            && previousState.getEntity() != null
            && !extraction.entity().equals(previousState.getEntity());

        // Layer 3: Follow-up patterns
        boolean hasFollowUpPattern = false;

        if (trimmed.length() <= 6 && endsWithParticle(trimmed)) {
            hasFollowUpPattern = true;
        }
        if (trimmed.startsWith("那") && trimmed.length() <= 8) {
            hasFollowUpPattern = true;
        }
        for (String phrase : FOLLOW_UP_PHRASES) {
            if (trimmed.contains(phrase)) {
                hasFollowUpPattern = true;
                break;
            }
        }

        // Layer 4: Decision
        if (hasFollowUpPattern && !entityChanged) {
            return ShiftType.FOLLOW_UP;
        }
        if (hasFollowUpPattern && entityChanged) {
            return ShiftType.ENTITY_SWITCH;
        }

        return ShiftType.NEW_QUERY;
    }

    private boolean endsWithParticle(String text) {
        if (text.isEmpty()) return false;
        return FOLLOW_UP_PARTICLES.contains(text.substring(text.length() - 1));
    }
}
