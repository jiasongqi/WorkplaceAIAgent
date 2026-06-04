package com.yupi.yuaiagent.dto;

import java.time.LocalDateTime;

/**
 * Session search result with weighted relevance score.
 */
public record SessionSearchResponse(
    String chatId,
    String title,
    int relevance,
    String snippet,
    SearchHitResponse bestHit,
    Long timestamp
) {
    /**
     * Best hit location for scroll-to-highlight.
     */
    public record SearchHitResponse(
        String messageId,
        int offset,
        int length
    ) {}
}
