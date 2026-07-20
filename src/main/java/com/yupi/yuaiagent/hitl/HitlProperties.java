package com.yupi.yuaiagent.hitl;

import lombok.Builder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Human-in-the-loop switches for high-risk side effects.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.hitl")
public class HitlProperties {

    /** Terminal commands require prior approval token when true. */
    private boolean terminalRequireApproval = true;

    /** Calendar event creation requires explicit confirmation artifact when true. */
    private boolean calendarRequireApproval = true;

    /** Pending approvals expire after this many seconds. */
    private int approvalTtlSeconds = 300;
}
