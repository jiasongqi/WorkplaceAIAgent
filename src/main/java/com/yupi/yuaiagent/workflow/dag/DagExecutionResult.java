package com.yupi.yuaiagent.workflow.dag;

import com.yupi.yuaiagent.agent.collaboration.ExpertOpinion;
import com.yupi.yuaiagent.workflow.runtime.WorkflowStatus;

import java.util.List;

/**
 * Result of a synchronous DAG execution.
 */
public record DagExecutionResult(
        String instanceId,
        String workflowId,
        WorkflowStatus status,
        String finalAnswer,
        List<ExpertOpinion> opinions,
        String errorMessage,
        List<String> artifactIds
) {
    public DagExecutionResult(String instanceId, String workflowId, WorkflowStatus status,
                              String finalAnswer, List<ExpertOpinion> opinions, String errorMessage) {
        this(instanceId, workflowId, status, finalAnswer, opinions, errorMessage, List.of());
    }

    public DagExecutionResult {
        opinions = opinions == null ? List.of() : List.copyOf(opinions);
        artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
    }

    public boolean success() {
        return status == WorkflowStatus.COMPLETED && finalAnswer != null && !finalAnswer.isBlank();
    }
}
