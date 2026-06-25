package com.yupi.yuaiagent.agent.task;

import com.yupi.yuaiagent.agent.output.AgentOutput;
import com.yupi.yuaiagent.budget.TokenUsage;

/**
 * Unified execution result for a single Agent step within a workflow.
 */
public record ExecutionResult(
    String taskId,
    String agentId,
    TaskStatus status,
    AgentOutput output,
    TokenUsage tokenUsage,
    long durationMs,
    int retryCount,
    Throwable error
) {
    public boolean isSuccess() { return status == TaskStatus.SUCCESS; }
    public boolean isFailed()  { return status == TaskStatus.FAILED; }
    public boolean isSkipped() {
        return status == TaskStatus.SKIPPED
            || status == TaskStatus.SKIPPED_BY_BUDGET
            || status == TaskStatus.SKIPPED_BY_POLICY;
    }

    public static ExecutionResult success(String taskId, String agentId,
            AgentOutput output, TokenUsage usage, long duration, int retryCount) {
        return new ExecutionResult(taskId, agentId,
            TaskStatus.SUCCESS, output, usage, duration, retryCount, null);
    }

    public static ExecutionResult failed(String taskId, String agentId,
            Throwable error, long duration) {
        return new ExecutionResult(taskId, agentId,
            TaskStatus.FAILED, null, null, duration, 0, error);
    }

    public static ExecutionResult skipped(String taskId, String agentId, TaskStatus reason) {
        return new ExecutionResult(taskId, agentId,
            reason, null, null, 0, 0, null);
    }
}
