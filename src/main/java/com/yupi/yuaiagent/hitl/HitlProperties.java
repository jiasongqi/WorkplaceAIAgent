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

    /** File write requires human approval when true. */
    private boolean fileWriteRequireApproval = true;

    /** Pending approvals expire after this many seconds. */
    private int approvalTtlSeconds = 300;

    /** Conversation-level human handoff tickets expire after this many seconds. */
    private int humanHandoffTtlSeconds = 86400;

    /**
     * Stop / escalate to HITL after this many consecutive non-NORMAL tool results
     * (or think failures) inside one Agent run.
     */
    private int maxConsecutiveToolErrors = 3;

    /** Optional Feishu/DingTalk bot webhook for remote HITL notify. */
    private String notifyWebhook = "";
}
