package com.yupi.yuaiagent.nlu;

import com.yupi.yuaiagent.nlu.UnifiedNluExtractor.IntentScore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects whether the top intents are ambiguous (belong to different categories).
 *
 * <p>Logic:
 * <ul>
 *   <li>Top1 and Top2 SAME category → not ambiguous</li>
 *   <li>Top1 and Top2 DIFFERENT categories + gap < threshold → ambiguous</li>
 *   <li>Top1 dominant (gap > 0.4) → not ambiguous regardless</li>
 * </ul>
 *
 * @author jsq
 */
@Component
public class IntentAmbiguityDetector {

    private static final Map<String, Set<String>> CATEGORIES = Map.of(
        "DATA",    Set.of("QUERY_DATA"),
        "RESUME",  Set.of("RESUME_OPTIMIZE", "INTERVIEW_PREP", "JOB_CHANGE", "OFFER_EVALUATE"),
        "SALARY",  Set.of("SALARY_ANALYZE", "SALARY_NEGOTIATE", "PERFORMANCE"),
        "LEAVE",   Set.of("LEAVE_PLAN", "LABOR_DISPUTE", "HANDOVER"),
        "CONSULT", Set.of("CONSULTATION"),
        "CAREER",  Set.of("CAREER_GENERAL", "EMOTIONAL_SUPPORT")
    );

    private static final double DOMINANT_GAP = 0.4;

    public AmbiguityResult check(List<IntentScore> intents) {
        if (intents.isEmpty()) {
            return new AmbiguityResult(true, "no_intent", "No intent detected");
        }

        IntentScore top1 = intents.get(0);

        if (intents.size() < 2) {
            return new AmbiguityResult(false, null, null);
        }

        IntentScore top2 = intents.get(1);
        double gap = top1.score() - top2.score();

        // Dominant gap → not ambiguous
        if (gap > DOMINANT_GAP) {
            return new AmbiguityResult(false, null, null);
        }

        // Same category → not ambiguous
        String cat1 = getCategory(top1.intent());
        String cat2 = getCategory(top2.intent());
        if (cat1 != null && cat1.equals(cat2)) {
            return new AmbiguityResult(false, null, null);
        }

        // Different categories + small gap → ambiguous
        return new AmbiguityResult(true, "category_conflict",
            "Top1=" + top1.intent() + "(" + cat1 + ") vs Top2=" + top2.intent() + "(" + cat2 + ")");
    }

    private String getCategory(String intent) {
        for (var entry : CATEGORIES.entrySet()) {
            if (entry.getValue().contains(intent)) return entry.getKey();
        }
        return null;
    }

    public record AmbiguityResult(boolean isAmbiguous, String reason, String detail) {}
}
