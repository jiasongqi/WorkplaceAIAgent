package com.yupi.yuaiagent.agent.task;

/**
 * Failure handling strategy per workflow.
 */
public enum FailurePolicy {
    /** Abort entire workflow immediately (critical paths: booking, payment). */
    FAIL_FAST,

    /** Retry once, then skip and continue with next Agent. */
    RETRY_THEN_SKIP,

    /** Retry once, then fail the entire workflow. */
    RETRY_THEN_FAIL,

    /** Skip directly (disabled Agent, e.g., salary-agent.enabled=false). */
    SKIP
}
