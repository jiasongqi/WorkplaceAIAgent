package com.yupi.yuaiagent.context;

import com.yupi.yuaiagent.agent.task.ExecutionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable runtime context — tracks workflow execution state.
 * NOT passed to Agents (they only see ConversationContext).
 *
 * @author jsq
 */
public class RuntimeContext {

    private final List<ExecutionResult> results = new ArrayList<>();
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private int currentStepIndex = 0;

    public void addResult(ExecutionResult result) {
        results.add(result);
    }

    public List<ExecutionResult> getResults() {
        return List.copyOf(results);
    }

    public List<ExecutionResult> getSuccessfulResults() {
        return results.stream().filter(ExecutionResult::isSuccess).toList();
    }

    public boolean hasFailures() {
        return results.stream().anyMatch(ExecutionResult::isFailed);
    }

    /** Summary of the last successful Agent output (for next Agent reference). */
    public String previousAgentSummary() {
        return results.stream()
            .filter(ExecutionResult::isSuccess)
            .reduce((a, b) -> b)
            .map(r -> r.output().summary())
            .orElse("");
    }

    // Variable access (for DAG V3)
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) variables.get(key);
    }
}
