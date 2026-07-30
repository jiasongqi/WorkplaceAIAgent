package com.yupi.yuaiagent.memory;

import com.yupi.yuaiagent.memory.context.KeyInfoExtractor;
import com.yupi.yuaiagent.memory.summary.SummaryLayer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds L4 experience retrieval queries from L1 user message + L3 summary (Ch5 Q5).
 */
@Slf4j
@Component
public class ExperienceQueryBuilder {

    private final KeyInfoExtractor keyInfoExtractor;
    private final SummaryLayer summaryLayer;

    public ExperienceQueryBuilder(KeyInfoExtractor keyInfoExtractor, SummaryLayer summaryLayer) {
        this.keyInfoExtractor = keyInfoExtractor;
        this.summaryLayer = summaryLayer;
    }

    /**
     * @param userId         user id
     * @param currentMessage latest user utterance (may be null)
     * @return semantic query for experience vector search
     */
    public String build(String userId, String currentMessage) {
        if (StringUtils.hasText(currentMessage)) {
            String fromMessage = fromMessage(currentMessage.trim());
            if (fromMessage.length() >= 4) {
                log.debug("[ExperienceQuery] from message: {}", truncate(fromMessage, 80));
                return fromMessage;
            }
        }

        if (StringUtils.hasText(userId)) {
            try {
                String summarySnippet = summaryLayer.getRecentSummaries(userId, 200);
                if (StringUtils.hasText(summarySnippet)) {
                    String cleaned = summarySnippet.replaceAll("[\\[\\]【】]", " ").trim();
                    if (cleaned.length() >= 4) {
                        log.debug("[ExperienceQuery] from L3 summary: {}", truncate(cleaned, 80));
                        return cleaned.length() > 300 ? cleaned.substring(0, 300) : cleaned;
                    }
                }
            } catch (Exception e) {
                log.debug("[ExperienceQuery] summary fallback skipped: {}", e.getMessage());
            }
        }

        return "职场咨询历史经验";
    }

    private String fromMessage(String message) {
        KeyInfoExtractor.KeyInfo info = keyInfoExtractor.extract(message);
        StringBuilder sb = new StringBuilder();
        if (message.length() <= 180) {
            sb.append(message);
        } else {
            sb.append(message, 0, 180);
        }
        info.getAllTerms().stream()
                .filter(t -> t != null && t.length() >= 2)
                .limit(8)
                .forEach(t -> {
                    if (!message.contains(t)) {
                        sb.append(' ').append(t);
                    }
                });
        return sb.toString().trim();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
