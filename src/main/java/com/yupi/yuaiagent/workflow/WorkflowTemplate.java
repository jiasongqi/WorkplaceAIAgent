package com.yupi.yuaiagent.workflow;

import com.yupi.yuaiagent.agent.task.FailurePolicy;
import com.yupi.yuaiagent.budget.TokenBudget;

import java.util.List;

/**
 * Workflow template — defines a named sequence of Agent steps.
 * V2: hardcoded. V3: config-driven.
 */
public record WorkflowTemplate(
    String id,
    String version,
    String name,
    String routePrefix,             // dotted route prefix for RouteHint matching (e.g., "advertiser.query")
    List<String> keywords,          // for rule-based matching
    List<PlanStep> steps,
    FailurePolicy failurePolicy,
    TokenBudget tokenBudget,
    boolean requiresPlanner         // true = PlannerAgent refines steps
) {
    public String fullId() { return id + ":" + version; }
}
