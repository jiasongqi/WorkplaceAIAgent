package com.yupi.yuaiagent.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt Injection detection — guards against adversarial user inputs
 * that attempt to override system instructions or extract system prompts.
 *
 * <p>Detection categories:
 * <ul>
 *     <li>System prompt override attempts ("ignore previous instructions")</li>
 *     <li>Role hijacking ("you are now...", "act as...")</li>
 *     <li>System prompt extraction ("repeat your system prompt", "what are your instructions")</li>
 *     <li>Delimiter injection (fake system/user message boundaries)</li>
 * </ul>
 *
 * <p>This is a rule-based first line of defense. For production, consider
 * adding an ML-based classifier or a dedicated guard model.</p>
 *
 * @author jsq
 */
@Slf4j
@Component
public class PromptInjectionDetector {

    /** Patterns that indicate system prompt override attempts */
    private static final List<Pattern> OVERRIDE_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions|rules|prompts)"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|above|prior)\\s+(instructions|rules)"),
            Pattern.compile("(?i)forget\\s+(all\\s+)?(previous|above|prior)\\s+(instructions|rules)"),
            Pattern.compile("(?i)override\\s+(your|the)\\s+(system\\s+)?(prompt|instructions)"),
            Pattern.compile("(?i)new\\s+instructions?:"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are")
    );

    /** Patterns that indicate role hijacking */
    private static final List<Pattern> HIJACK_PATTERNS = List.of(
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an)\\s+"),
            Pattern.compile("(?i)act\\s+as\\s+(if\\s+)?you\\s+(are|were)"),
            Pattern.compile("(?i)pretend\\s+(to\\s+be|you\\s+are)"),
            Pattern.compile("(?i)from\\s+now\\s+on,?\\s+you\\s+(are|will|must)"),
            Pattern.compile("(?i)roleplay\\s+as")
    );

    /** Patterns that attempt to extract system prompt */
    private static final List<Pattern> EXTRACTION_PATTERNS = List.of(
            Pattern.compile("(?i)(repeat|show|print|output|reveal|tell)\\s+(me\\s+)?(your|the)\\s+(system\\s+)?(prompt|instructions|rules)"),
            Pattern.compile("(?i)what\\s+(are|is)\\s+your\\s+(system\\s+)?(prompt|instructions|rules)"),
            Pattern.compile("(?i)what\\s+(were|was)\\s+you\\s+(told|instructed|programmed)"),
            Pattern.compile("(?i)\\[SYSTEM\\]"),
            Pattern.compile("(?i)\\[INST\\]")
    );

    /**
     * Check if the user input contains prompt injection attempts.
     *
     * @param userInput raw user input to check
     * @return detection result
     */
    public DetectionResult detect(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return DetectionResult.createSafe();
        }

        String normalized = userInput.trim();

        // Check override patterns
        for (Pattern p : OVERRIDE_PATTERNS) {
            if (p.matcher(normalized).find()) {
                log.warn("[PromptInjection] Override attempt detected: pattern={}, input={}",
                        p.pattern(), truncate(normalized, 100));
                return DetectionResult.blocked("OVERRIDE", p.pattern());
            }
        }

        // Check hijack patterns
        for (Pattern p : HIJACK_PATTERNS) {
            if (p.matcher(normalized).find()) {
                log.warn("[PromptInjection] Role hijack detected: pattern={}, input={}",
                        p.pattern(), truncate(normalized, 100));
                return DetectionResult.blocked("HIJACK", p.pattern());
            }
        }

        // Check extraction patterns
        for (Pattern p : EXTRACTION_PATTERNS) {
            if (p.matcher(normalized).find()) {
                log.warn("[PromptInjection] Extraction attempt detected: pattern={}, input={}",
                        p.pattern(), truncate(normalized, 100));
                return DetectionResult.blocked("EXTRACTION", p.pattern());
            }
        }

        return DetectionResult.createSafe();
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /**
     * Detection result.
     *
     * @param safe    true if no injection detected
     * @param type    detection type (OVERRIDE / HIJACK / EXTRACTION), null if safe
     * @param pattern matched pattern, null if safe
     */
    public record DetectionResult(boolean safe, String type, String pattern) {
        public static DetectionResult createSafe() {
            return new DetectionResult(true, null, null);
        }

        public static DetectionResult blocked(String type, String pattern) {
            return new DetectionResult(false, type, pattern);
        }
    }
}
