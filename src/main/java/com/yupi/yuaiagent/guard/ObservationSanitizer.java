package com.yupi.yuaiagent.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Middleware that cleans tool Observation before it enters the LLM context (Ch3 Sanitizer Layer).
 * <p>Order: strip noise → length check → hard truncate with system note.</p>
 */
@Slf4j
@Component
public class ObservationSanitizer {

    private static final int DEFAULT_MAX_CHARS = 3000;
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern BASE64_BLOB = Pattern.compile(
            "(?:data:[^;]+;base64,)?[A-Za-z0-9+/]{200,}={0,2}");
    private static final Pattern MULTI_BLANK = Pattern.compile("[ \\t]+");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

    /**
     * Sanitize tool observation text for context injection.
     *
     * @param raw      raw tool output (may be null)
     * @param maxChars max characters to keep (≤0 uses default 3000)
     */
    public String sanitize(String raw, int maxChars) {
        if (raw == null) {
            return null;
        }
        if (raw.isBlank()) {
            return raw;
        }
        int limit = maxChars > 0 ? maxChars : DEFAULT_MAX_CHARS;
        String cleaned = raw;
        try {
            cleaned = HTML_TAG.matcher(cleaned).replaceAll(" ");
            cleaned = BASE64_BLOB.matcher(cleaned).replaceAll("[base64 omitted]");
            cleaned = MULTI_BLANK.matcher(cleaned).replaceAll(" ");
            cleaned = MULTI_NEWLINE.matcher(cleaned).replaceAll("\n\n");
            cleaned = cleaned.strip();
        } catch (Exception e) {
            log.warn("[ObservationSanitizer] clean failed: {}", e.getMessage());
            cleaned = raw;
        }

        if (cleaned.length() <= limit) {
            return cleaned;
        }
        String truncated = cleaned.substring(0, limit);
        log.info("[ObservationSanitizer] truncated {} → {} chars", cleaned.length(), limit);
        return truncated + "\n[System Note: Output was sanitized/truncated due to length; totalChars="
                + cleaned.length() + ". Use readFileChunk or ask for a narrower query if details are missing.]";
    }

    public String sanitize(String raw) {
        return sanitize(raw, DEFAULT_MAX_CHARS);
    }
}
