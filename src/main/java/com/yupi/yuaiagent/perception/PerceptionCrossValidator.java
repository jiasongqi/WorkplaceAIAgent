package com.yupi.yuaiagent.perception;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Cross-check a VLM / perception hypothesis against a tool-observed ground truth
 * (mm_agent_tutorial Ch1 Ex 1.4: perceptual hallucination vs tool verification).
 */
@Component
public class PerceptionCrossValidator {

    public record CrossCheckResult(
            boolean consistent,
            String hypothesis,
            String observed,
            String guidance
    ) {
    }

    /**
     * Normalize and compare numeric / string hypotheses.
     */
    public CrossCheckResult check(String hypothesis, String observed) {
        String h = normalize(hypothesis);
        String o = normalize(observed);
        if (!StringUtils.hasText(h) || !StringUtils.hasText(o)) {
            return new CrossCheckResult(false, hypothesis, observed,
                    "假设或观测为空，标记低置信度，请重新提取或换工具读取。");
        }
        if (h.equals(o) || h.contains(o) || o.contains(h)) {
            return new CrossCheckResult(true, hypothesis, observed,
                    "感知假设与工具观测一致，可采信。");
        }
        // Numeric tolerance
        Double hn = tryParseNumber(h);
        Double on = tryParseNumber(o);
        if (hn != null && on != null) {
            double rel = Math.abs(hn - on) / Math.max(Math.abs(on), 1e-9);
            if (rel <= 0.02) {
                return new CrossCheckResult(true, hypothesis, observed,
                        "数值在 2% 容差内一致。");
            }
            return new CrossCheckResult(false, hypothesis, observed,
                    "数值差异过大（相对误差 " + String.format("%.1f%%", rel * 100)
                            + "）。以工具观测为准，勿采信感知猜测。");
        }
        return new CrossCheckResult(false, hypothesis, observed,
                "感知与工具结果不一致。以工具观测为准，并将感知结果标为低置信度。");
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().replace(",", "").replace(" ", "").toLowerCase();
    }

    private static Double tryParseNumber(String s) {
        try {
            String cleaned = s.replaceAll("[^0-9.\\-]", "");
            if (cleaned.isBlank()) {
                return null;
            }
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return null;
        }
    }
}
