package com.yupi.yuaiagent.budget;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-workflow token usage and enforces budgets.
 *
 * @author jsq
 */
@Slf4j
@Component
public class TokenUsageTracker {

    private final Map<String, TokenUsage> workflowUsage = new ConcurrentHashMap<>();

    /**
     * Record usage after an Agent call completes.
     */
    public void recordUsage(String workflowId, TokenUsage usage) {
        workflowUsage.merge(workflowId, usage, TokenUsage::add);
    }

    /**
     * Check if the workflow has enough budget remaining for the estimated tokens.
     */
    public boolean canExecute(String workflowId, TokenBudget budget, long estimatedTokens) {
        TokenUsage used = workflowUsage.getOrDefault(workflowId, TokenUsage.ZERO);
        return budget.canExecute(estimatedTokens, used.totalTokens());
    }

    /**
     * Get total usage for a workflow.
     */
    public TokenUsage getUsage(String workflowId) {
        return workflowUsage.getOrDefault(workflowId, TokenUsage.ZERO);
    }

    /**
     * Reset usage for a workflow (new request).
     */
    public void reset(String workflowId) {
        workflowUsage.remove(workflowId);
    }
}
