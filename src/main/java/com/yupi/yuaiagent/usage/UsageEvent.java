package com.yupi.yuaiagent.usage;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Usage event — records a single user action for analytics.
 *
 * @author jsq
 */
@Data
public class UsageEvent {

    private String eventId;
    private String userId;
    private UsageEventType type;
    private String agentType;      // only for CHAT events
    private long durationMs;
    private LocalDateTime timestamp;
}
