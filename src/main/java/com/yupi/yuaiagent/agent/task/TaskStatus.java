package com.yupi.yuaiagent.agent.task;

/**
 * Task execution status.
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    RETRYING,
    SKIPPED,
    SKIPPED_BY_BUDGET,
    SKIPPED_BY_POLICY
}
