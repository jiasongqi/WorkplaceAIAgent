package com.yupi.yuaiagent.sessionstate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Non-AI middleware that cleans model-emitted "JSON" before handoff / artifact publish.
 * <p>
 * Guards against Schema Promise Break: markdown fences, leading prose, trailing commentary.
 */
@Slf4j
public final class HandoffPacketParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FENCE = Pattern.compile(
            "```(?:json|JSON)?\\s*([\\s\\S]*?)```", Pattern.MULTILINE);

    private HandoffPacketParser() {
    }

    /**
     * Strip markdown fences / surrounding prose and return the JSON object text.
     *
     * @return cleaned JSON string, or empty if extraction fails
     */
    public static Optional<String> extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String candidate = raw.trim();

        Matcher fence = FENCE.matcher(candidate);
        if (fence.find()) {
            candidate = fence.group(1).trim();
        }

        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        candidate = candidate.substring(start, end + 1).trim();

        try {
            JsonNode node = MAPPER.readTree(candidate);
            if (node == null || !node.isObject()) {
                return Optional.empty();
            }
            // Re-serialize to canonical clean JSON (no markdown)
            return Optional.of(MAPPER.writeValueAsString(node));
        } catch (Exception e) {
            log.debug("[HandoffPacketParser] extract failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parse into JsonNode after cleaning; empty if invalid.
     */
    public static Optional<JsonNode> parseObject(String raw) {
        return extractJsonObject(raw).flatMap(json -> {
            try {
                return Optional.of(MAPPER.readTree(json));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Require clean JSON or throw — use at handoff boundary so dirty data never reaches Agent B.
     */
    public static String requireCleanJson(String raw) {
        return extractJsonObject(raw).orElseThrow(() ->
                new IllegalArgumentException("Handoff payload is not a valid JSON object after cleaning"));
    }

    /**
     * Best-effort clean: returns cleaned JSON if possible, otherwise original trimmed text.
     */
    public static String cleanOrPassthrough(String raw) {
        if (raw == null) {
            return "";
        }
        return extractJsonObject(raw).orElse(raw.trim());
    }
}
