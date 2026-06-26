package com.yupi.yuaiagent.agent.paradigm;

import com.yupi.yuaiagent.nlu.NluIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Paradigm Selector — selects the optimal reasoning paradigm based on task characteristics.
 *
 * <p>Selection strategy (rule-based, no LLM call):</p>
 * <ul>
 *     <li>COMPLEX_ANALYSIS / RESEARCH → PLAN_AND_SOLVE (structured approach)</li>
 *     <li>CREATIVE_WRITING / CODE_REVIEW → REFLECTION (quality-focused)</li>
 *     <li>Default / TOOL_HEAVY / INTERACTIVE → REACT (dynamic adjustment)</li>
 * </ul>
 *
 * <p>The selector also considers:</p>
 * <ul>
 *     <li>User preference (if explicitly set)</li>
 *     <li>Message complexity (keyword heuristics)</li>
 *     <li>Historical performance (future: feedback loop)</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
public class ParadigmSelector {

    // Keywords that suggest complex planning tasks
    private static final String[] PLANNING_KEYWORDS = {
        "分析", "研究", "对比", "评估", "规划", "方案", "策略", "步骤",
        "analyze", "research", "compare", "evaluate", "plan", "strategy", "steps"
    };

    // Keywords that suggest reflection/quality tasks
    private static final String[] REFLECTION_KEYWORDS = {
        "优化", "改进", "审查", "检查", "完善", "润色", "总结", "review", "optimize",
        "improve", "refine", "polish", "summarize", "quality"
    };

    /**
     * Select paradigm based on NLU result and message content.
     *
     * @param intent    resolved NLU intent
     * @param message   original user message
     * @param confidence NLU confidence score
     * @return selected paradigm
     */
    public AgentParadigm select(NluIntent intent, String message, double confidence) {
        // 1. Intent-based selection (primary)
        AgentParadigm intentBased = selectByIntent(intent);
        if (intentBased != null) {
            log.debug("[ParadigmSelector] Intent-based selection: {} for intent={}", intentBased, intent);
            return intentBased;
        }

        // 2. Keyword-based heuristics (secondary)
        AgentParadigm keywordBased = selectByKeywords(message);
        if (keywordBased != null) {
            log.debug("[ParadigmSelector] Keyword-based selection: {} for message={}", keywordBased,
                    message.substring(0, Math.min(50, message.length())));
            return keywordBased;
        }

        // 3. Default: REACT (most versatile)
        log.debug("[ParadigmSelector] Default selection: REACT");
        return AgentParadigm.REACT;
    }

    /**
     * Select paradigm based on NLU intent.
     *
     * @param intent resolved intent
     * @return paradigm or null if no specific mapping
     */
    private AgentParadigm selectByIntent(NluIntent intent) {
        if (intent == null) {
            return null;
        }

        return switch (intent) {
            // Complex analysis tasks → Plan-and-Solve
            case DATA_QUERY, CAREER_ADVICE -> AgentParadigm.PLAN_AND_SOLVE;

            // Creative/quality tasks → Reflection
            case CONTENT_GENERATION, SKILL_ASSESSMENT -> AgentParadigm.REFLECTION;

            // Interactive/tool tasks → ReAct
            case TOOL_CALL, GENERAL_CHAT, UNKNOWN -> AgentParadigm.REACT;
        };
    }

    /**
     * Select paradigm based on message keywords.
     *
     * @param message user message
     * @return paradigm or null if no clear signal
     */
    private AgentParadigm selectByKeywords(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String lowerMessage = message.toLowerCase();

        // Check planning keywords
        for (String keyword : PLANNING_KEYWORDS) {
            if (lowerMessage.contains(keyword)) {
                return AgentParadigm.PLAN_AND_SOLVE;
            }
        }

        // Check reflection keywords
        for (String keyword : REFLECTION_KEYWORDS) {
            if (lowerMessage.contains(keyword)) {
                return AgentParadigm.REFLECTION;
            }
        }

        return null;
    }

    /**
     * Select paradigm with user override.
     *
     * @param userPreference user-specified paradigm (nullable)
     * @param intent         NLU intent
     * @param message        user message
     * @param confidence     NLU confidence
     * @return selected paradigm
     */
    public AgentParadigm selectWithOverride(String userPreference, NluIntent intent,
                                             String message, double confidence) {
        // User override takes highest priority
        if (userPreference != null && !userPreference.isBlank()) {
            AgentParadigm override = AgentParadigm.fromCode(userPreference);
            log.info("[ParadigmSelector] User override: {}", override);
            return override;
        }

        // Fall back to automatic selection
        return select(intent, message, confidence);
    }

    /**
     * Get paradigm selection explanation (for debugging/transparency).
     *
     * @param paradigm  selected paradigm
     * @param intent    NLU intent
     * @param message   user message
     * @return human-readable explanation
     */
    public String explain(AgentParadigm paradigm, NluIntent intent, String message) {
        return String.format(
                "Selected paradigm: %s (%s)\n" +
                "Reason: intent=%s, message_keywords=%s",
                paradigm.name(), paradigm.getDescription(),
                intent != null ? intent.name() : "null",
                extractKeywords(message)
        );
    }

    /**
     * Extract key terms from message for explanation.
     */
    private String extractKeywords(String message) {
        if (message == null || message.isBlank()) {
            return "none";
        }

        // Simple keyword extraction (could be enhanced with NLP)
        String[] words = message.split("\\s+");
        StringBuilder keywords = new StringBuilder();
        for (String word : words) {
            if (word.length() > 2) { // Skip short words
                if (keywords.length() > 0) {
                    keywords.append(", ");
                }
                keywords.append(word);
                if (keywords.length() > 50) { // Limit length
                    keywords.append("...");
                    break;
                }
            }
        }
        return keywords.toString();
    }
}
