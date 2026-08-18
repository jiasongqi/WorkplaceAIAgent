package com.yupi.yuaiagent.history;

import java.time.Instant;
import java.util.Map;

/**
 * Dual-write envelope for SSE / Trace / Tool / Workflow / Subagent events.
 */
public record ActionHistoryEvent(
        String eventId,
        Instant occurredAt,
        String source,
        String type,
        String chatId,
        Map<String, Object> payload
) {
    public ActionHistoryEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
