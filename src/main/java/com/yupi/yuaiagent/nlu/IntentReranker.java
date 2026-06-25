package com.yupi.yuaiagent.nlu;

import com.yupi.yuaiagent.nlu.NluContext.AliasMatch;
import com.yupi.yuaiagent.nlu.UnifiedNluExtractor.IntentScore;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Re-ranks intent scores using alias domain signals.
 *
 * <p>When an alias is detected (e.g., TX→腾讯资方, type=ADVERTISER),
 * intents that align with that domain get a bonus, mismatched intents get penalized.
 *
 * <p>Runs BEFORE IntentAmbiguityDetector — changes the scores that the ambiguity check uses.
 *
 * @author jsq
 */
@Component
public class IntentReranker {

    /** Domain-intent alignment weights. Positive = bonus, negative = penalty. */
    private static final Map<String, Map<String, Double>> DOMAIN_INTENT_WEIGHTS = Map.of(
        "ADVERTISER", Map.of(
            "QUERY_DATA",      +0.15,
            "SALARY_ANALYZE",  +0.05,
            "CAREER_GENERAL",  -0.15,
            "RESUME_OPTIMIZE", -0.20
        ),
        "RESUME", Map.of(
            "RESUME_OPTIMIZE", +0.15,
            "INTERVIEW_PREP",  +0.10,
            "JOB_CHANGE",      +0.10,
            "QUERY_DATA",      -0.10
        ),
        "SALARY", Map.of(
            "SALARY_ANALYZE",  +0.15,
            "SALARY_NEGOTIATE", +0.10,
            "QUERY_DATA",      -0.05
        )
    );

    /**
     * Re-rank intent scores based on detected alias domains.
     *
     * @param intents      original LLM intent scores
     * @param aliasMatches detected aliases (with entityType)
     * @return re-ranked scores (same intents, adjusted scores, re-sorted)
     */
    public List<IntentScore> rerank(List<IntentScore> intents, List<AliasMatch> aliasMatches) {
        if (aliasMatches == null || aliasMatches.isEmpty() || intents.isEmpty()) {
            return intents;
        }

        Set<String> domains = new HashSet<>();
        for (AliasMatch m : aliasMatches) {
            if (m.entityType() != null) {
                domains.add(m.entityType());
            }
        }
        if (domains.isEmpty()) return intents;

        List<IntentScore> reranked = new ArrayList<>();
        for (IntentScore score : intents) {
            double adjustment = 0.0;
            for (String domain : domains) {
                Map<String, Double> weights = DOMAIN_INTENT_WEIGHTS.get(domain);
                if (weights != null) {
                    adjustment += weights.getOrDefault(score.intent(), 0.0);
                }
            }
            double newScore = Math.max(0.01, Math.min(1.0, score.score() + adjustment));
            reranked.add(new IntentScore(score.intent(), newScore));
        }

        reranked.sort((a, b) -> Double.compare(b.score(), a.score()));
        return reranked;
    }
}
