package com.yupi.yuaiagent.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Score-based workflow matcher with RouteHint prefix matching.
 *
 * <p>Layer 0: RouteHint prefix match (from NLU Pipeline). Highest priority.
 * <p>Layer 1: Rule match (keyword scoring). 80% hit rate.
 * <p>Layer 2: LLM match. 15%.
 * <p>Layer 3: GENERIC_FALLBACK. 5%.
 *
 * @author jsq
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowMatcher {

    private final WorkflowRegistry registry;

    /**
     * Match by message keywords (V1 approach).
     */
    public WorkflowMatchResult match(String message) {
        // Layer 1: Score-based rule match
        WorkflowMatchResult ruleResult = scoreBasedMatch(message);
        if (ruleResult != null && ruleResult.confidence() >= 0.6) {
            return ruleResult;
        }

        // Layer 2: LLM match (placeholder)

        // Layer 3: GENERIC_FALLBACK
        return new WorkflowMatchResult("GENERIC_CAREER", MatchType.FALLBACK, 1.0);
    }

    /**
     * Match by RouteHint prefix (V2 approach — NLU Pipeline produces route hint).
     *
     * <p>Prefix matching: "advertiser.query.roi" matches workflow with routePrefix "advertiser.query".
     * Longer prefix = more specific = higher priority.
     *
     * @param routeHint dotted route hint from NLU (e.g., "advertiser.query.roi")
     * @return matched workflow, or null if no prefix match
     */
    public WorkflowMatchResult matchByRouteHint(String routeHint) {
        if (routeHint == null || routeHint.isBlank()) return null;

        WorkflowTemplate bestMatch = null;
        int bestPrefixLen = 0;

        for (WorkflowTemplate template : registry.getAll()) {
            String prefix = template.routePrefix();
            if (prefix == null || prefix.isBlank()) continue;

            if (routeHint.startsWith(prefix) && prefix.length() > bestPrefixLen) {
                bestMatch = template;
                bestPrefixLen = prefix.length();
            }
        }

        if (bestMatch != null) {
            double confidence = (double) bestPrefixLen / routeHint.length();
            return new WorkflowMatchResult(bestMatch.id(), MatchType.RULE, Math.max(confidence, 0.8));
        }

        return null;
    }

    /**
     * Combined match: try RouteHint first, then keyword match, then fallback.
     */
    public WorkflowMatchResult match(String message, String routeHint) {
        // Layer 0: RouteHint prefix match
        WorkflowMatchResult routeResult = matchByRouteHint(routeHint);
        if (routeResult != null) {
            return routeResult;
        }

        // Layer 1-3: existing logic
        return match(message);
    }

    /**
     * Score-based match: score each workflow by keyword hits, pick the highest.
     */
    private WorkflowMatchResult scoreBasedMatch(String message) {
        Map<String, Integer> scores = new HashMap<>();

        for (WorkflowTemplate template : registry.getAll()) {
            if (template.keywords().isEmpty()) continue;

            int score = 0;
            for (String keyword : template.keywords()) {
                if (message.contains(keyword)) {
                    score++;
                }
            }
            if (score > 0) {
                scores.put(template.id(), score);
            }
        }

        if (scores.isEmpty()) return null;

        Map.Entry<String, Integer> best = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);

        if (best == null || best.getValue() == 0) return null;

        WorkflowTemplate template = registry.get(best.getKey());
        double confidence = (double) best.getValue() / template.keywords().size();

        return new WorkflowMatchResult(best.getKey(), MatchType.RULE, Math.min(confidence, 1.0));
    }
}
