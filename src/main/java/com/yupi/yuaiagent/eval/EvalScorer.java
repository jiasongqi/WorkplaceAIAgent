package com.yupi.yuaiagent.eval;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.agent.KeywordRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Scores eval cases without requiring a live LLM when possible.
 * <ul>
 *   <li>ROUTING / EXACT_MATCH on expectedIntent → KeywordRouter accuracy</li>
 *   <li>EXACT_MATCH on expectedOutput → substring containment</li>
 *   <li>KEYWORD_OVERLAP → token overlap ratio</li>
 * </ul>
 */
@Slf4j
@Component
public class EvalScorer {

    public record ScoreResult(double score, String actualOutput, String feedback) {}

    /**
     * Score a routing case: compare KeywordRouter (and optional expectedIntent) result.
     */
    public ScoreResult scoreRouting(EvalCase evalCase) {
        String expected = StringUtils.hasText(evalCase.getExpectedIntent())
                ? evalCase.getExpectedIntent().trim().toUpperCase()
                : (evalCase.getExpectedOutput() != null
                    ? evalCase.getExpectedOutput().trim().toUpperCase() : "");

        AgentIntent actual = KeywordRouter.keywordRouteIntent(evalCase.getInput());
        // When keyword router returns null, treat as GENERAL (same as orchestrator fall-through intent before NLU)
        String actualName = actual != null ? actual.name() : "GENERAL_OR_NLU";

        // For routing suite we require a clear keyword match
        if (actual == null) {
            boolean passExpectedNeedsNlu = "NLU".equals(expected) || "GENERAL_OR_NLU".equals(expected);
            return new ScoreResult(
                    passExpectedNeedsNlu ? 1.0 : 0.0,
                    actualName,
                    passExpectedNeedsNlu
                            ? "KeywordRouter correctly deferred to NLU"
                            : "KeywordRouter returned null, expected " + expected);
        }

        double score = actual.name().equalsIgnoreCase(expected) ? 1.0 : 0.0;
        return new ScoreResult(score, actual.name(),
                score >= 1.0 ? "routing match" : "expected=" + expected + ", actual=" + actual.name());
    }

    /**
     * Score content case with lightweight heuristics (no LLM).
     */
    public ScoreResult scoreContent(EvalCase evalCase, String actualOutput) {
        String rule = evalCase.getScoringRule() != null
                ? evalCase.getScoringRule().toUpperCase() : "KEYWORD_OVERLAP";
        String expected = evalCase.getExpectedOutput() != null ? evalCase.getExpectedOutput() : "";
        String actual = actualOutput != null ? actualOutput : "";

        return switch (rule) {
            case "EXACT_MATCH" -> {
                boolean hit = actual.contains(expected) || expected.contains(actual);
                yield new ScoreResult(hit ? 1.0 : 0.0, truncate(actual),
                        hit ? "exact/substring match" : "no substring match");
            }
            case "ROUTING" -> scoreRouting(evalCase);
            default -> {
                double overlap = keywordOverlap(expected, actual);
                yield new ScoreResult(overlap, truncate(actual),
                        "keyword_overlap=" + String.format("%.2f", overlap));
            }
        };
    }

    static double keywordOverlap(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return 0.0;
        }
        String[] tokens = expected.replaceAll("[，。、；：！？\\s]+", " ").split("\\s+");
        int hit = 0;
        int total = 0;
        for (String t : tokens) {
            if (t.length() < 2) continue;
            total++;
            if (actual.contains(t)) hit++;
        }
        return total == 0 ? 0.0 : (double) hit / total;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
