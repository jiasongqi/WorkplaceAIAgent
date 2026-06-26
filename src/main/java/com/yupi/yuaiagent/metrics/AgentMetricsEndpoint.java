package com.yupi.yuaiagent.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom Actuator Endpoint for Agent performance metrics.
 *
 * <p>Access via: GET /actuator/agent-metrics</p>
 *
 * <p>Returns a comprehensive snapshot of agent runtime metrics including:</p>
 * <ul>
 *     <li>Active requests count</li>
 *     <li>Total requests and average duration</li>
 *     <li>Tool call statistics</li>
 *     <li>Token usage statistics</li>
 *     <li>Average steps per request</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Component
@Endpoint(id = "agent-metrics")
@RequiredArgsConstructor
public class AgentMetricsEndpoint {

    private final AgentMetrics agentMetrics;

    /**
     * GET /actuator/agent-metrics
     * Returns current agent metrics snapshot.
     */
    @ReadOperation
    public Map<String, Object> metrics() {
        AgentMetrics.MetricsSnapshot snapshot = agentMetrics.getSnapshot();

        Map<String, Object> result = new LinkedHashMap<>();

        // Status
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());

        // Active requests
        result.put("activeRequests", snapshot.activeRequests());

        // Request metrics
        Map<String, Object> requests = new LinkedHashMap<>();
        requests.put("total", snapshot.totalRequests());
        requests.put("avgDurationMs", Math.round(snapshot.avgRequestDurationMs() * 100.0) / 100.0);
        requests.put("totalDurationMs", Math.round(snapshot.totalRequestDurationMs() * 100.0) / 100.0);
        result.put("requests", requests);

        // Token metrics
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("totalUsed", (long) snapshot.totalTokensUsed());
        tokens.put("avgPerRequest", Math.round(snapshot.avgTokensPerRequest() * 100.0) / 100.0);
        tokens.put("samples", snapshot.totalTokenSamples());
        result.put("tokens", tokens);

        // Tool call metrics
        Map<String, Object> toolCalls = new LinkedHashMap<>();
        toolCalls.put("total", snapshot.totalToolCalls());
        result.put("toolCalls", toolCalls);

        // Step metrics
        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("avgPerRequest", Math.round(snapshot.avgStepsPerRequest() * 100.0) / 100.0);
        result.put("steps", steps);

        // Health indicators
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("isHealthy", snapshot.activeRequests() < 100);
        health.put("highLoad", snapshot.activeRequests() > 50);
        result.put("health", health);

        return result;
    }

    /**
     * POST /actuator/agent-metrics/reset
     * Reset metrics (for testing/debugging).
     */
    @WriteOperation
    public Map<String, String> reset() {
        // Note: Micrometer doesn't support resetting counters directly
        // This endpoint is for future use or custom reset logic
        Map<String, String> result = new LinkedHashMap<>();
        result.put("status", "acknowledged");
        result.put("message", "Metrics reset requested. Note: Micrometer counters are cumulative by design.");
        return result;
    }
}
