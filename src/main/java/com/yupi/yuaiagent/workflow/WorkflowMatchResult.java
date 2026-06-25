package com.yupi.yuaiagent.workflow;

/**
 * Result of workflow matching.
 */
public record WorkflowMatchResult(
    String workflowId,
    MatchType matchType,
    double confidence
) {}
