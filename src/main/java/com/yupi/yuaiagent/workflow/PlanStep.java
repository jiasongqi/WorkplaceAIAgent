package com.yupi.yuaiagent.workflow;

/**
 * A single step in a workflow — identifies which Agent to call and what to do.
 */
public record PlanStep(
    String agentId,         // "RESUME", "NEGOTIATION", etc.
    String taskDescription  // "简历优化" — context for the Agent
) {
    public static PlanStep of(String agentId, String desc) {
        return new PlanStep(agentId, desc);
    }

    public static PlanStep of(String agentId) {
        return new PlanStep(agentId, "");
    }
}
