package com.yupi.yuaiagent.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent Diagnostics Endpoint — comprehensive agent health and performance diagnostics.
 *
 * <p>Access via: GET /actuator/agent-diagnostics</p>
 *
 * <p>Provides:</p>
 * <ul>
 *     <li>Per-agent execution metrics</li>
 *     <li>Circuit breaker status</li>
 *     <li>Active agent count</li>
 *     <li>Global success rate</li>
 *     <li>Timeout and error statistics</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Component
@Endpoint(id = "agent-diagnostics")
@RequiredArgsConstructor
public class AgentDiagnosticsEndpoint {

    private final AgentExecutionMetrics executionMetrics;
    private final AgentCircuitBreaker circuitBreaker;
    private final AgentMetrics agentMetrics;

    /**
     * GET /actuator/agent-diagnostics
     * Returns comprehensive diagnostics for all agents.
     */
    @ReadOperation
    public Map<String, Object> diagnostics() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Global overview
        AgentExecutionMetrics.GlobalMetrics global = executionMetrics.getGlobalMetrics();
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("activeAgents", global.activeAgents());
        overview.put("totalExecutions", global.totalExecutions());
        overview.put("successRate", String.format("%.1f%%", global.successRate() * 100));
        overview.put("totalFailures", global.totalFailures());
        overview.put("totalTimeouts", global.totalTimeouts());
        result.put("overview", overview);

        // Per-agent metrics
        Map<String, Object> agents = new LinkedHashMap<>();
        for (Map.Entry<String, AgentExecutionMetrics.AgentMetricsSnapshot> entry :
                executionMetrics.getAllSnapshots().entrySet()) {
            agents.put(entry.getKey(), formatAgentSnapshot(entry.getValue()));
        }
        result.put("agents", agents);

        // Circuit breaker status
        Map<String, Object> circuits = new LinkedHashMap<>();
        for (Map.Entry<String, AgentCircuitBreaker.CircuitMetrics> entry :
                circuitBreaker.getAllMetrics().entrySet()) {
            circuits.put(entry.getKey(), formatCircuitMetrics(entry.getValue()));
        }
        result.put("circuitBreakers", circuits);

        // Health assessment
        result.put("health", assessHealth(global, circuitBreaker.getAllMetrics()));

        return result;
    }

    /**
     * GET /actuator/agent-diagnostics/{agentType}
     * Returns diagnostics for a specific agent.
     */
    @ReadOperation
    public Map<String, Object> agentDiagnostics(@Selector String agentType) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Agent execution metrics
        AgentExecutionMetrics.AgentMetricsSnapshot snapshot = executionMetrics.getSnapshot(agentType);
        result.put("execution", formatAgentSnapshot(snapshot));

        // Circuit breaker
        AgentCircuitBreaker.CircuitMetrics circuit = circuitBreaker.getMetrics(agentType);
        result.put("circuitBreaker", formatCircuitMetrics(circuit));

        // Recommendation
        result.put("recommendation", getRecommendation(snapshot, circuit));

        return result;
    }

    // ─── Formatting Methods ────────────────────────────────────────────

    private Map<String, Object> formatAgentSnapshot(AgentExecutionMetrics.AgentMetricsSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalExecutions", snapshot.totalExecutions());
        map.put("successRate", String.format("%.1f%%", snapshot.successRate() * 100));
        map.put("failureRate", String.format("%.1f%%", snapshot.failureRate() * 100));
        map.put("avgDurationMs", Math.round(snapshot.avgDurationMs()));
        map.put("p50DurationMs", Math.round(snapshot.p50DurationMs()));
        map.put("p95DurationMs", Math.round(snapshot.p95DurationMs()));
        map.put("p99DurationMs", Math.round(snapshot.p99DurationMs()));
        map.put("avgTokensPerExecution", Math.round(snapshot.avgTokensPerExecution()));
        map.put("avgToolCalls", String.format("%.1f", snapshot.avgToolCalls()));
        map.put("avgSteps", String.format("%.1f", snapshot.avgSteps()));
        map.put("timeoutCount", snapshot.timeoutCount());
        return map;
    }

    private Map<String, Object> formatCircuitMetrics(AgentCircuitBreaker.CircuitMetrics metrics) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", metrics.status().name());
        map.put("totalRequests", metrics.totalRequests());
        map.put("failureRate", String.format("%.1f%%", metrics.failureRate()));
        map.put("timeoutRate", String.format("%.1f%%", metrics.timeoutRate()));
        map.put("consecutiveFailures", metrics.consecutiveFailures());
        return map;
    }

    // ─── Health Assessment ─────────────────────────────────────────────

    private Map<String, Object> assessHealth(AgentExecutionMetrics.GlobalMetrics global,
                                              Map<String, AgentCircuitBreaker.CircuitMetrics> circuits) {
        Map<String, Object> health = new LinkedHashMap<>();
        String status = "HEALTHY";
        StringBuilder issues = new StringBuilder();

        // Check global success rate
        if (global.successRate() < 0.8) {
            status = "DEGRADED";
            issues.append("Low success rate: ").append(String.format("%.1f%%", global.successRate() * 100)).append("; ");
        }

        // Check for open circuits
        long openCircuits = circuits.values().stream()
                .filter(c -> c.status() == AgentCircuitBreaker.CircuitStatus.OPEN)
                .count();
        if (openCircuits > 0) {
            status = "DEGRADED";
            issues.append(openCircuits).append(" circuit(s) OPEN; ");
        }

        // Check for high timeout rate
        if (global.totalTimeouts() > global.totalExecutions() * 0.1) {
            status = "DEGRADED";
            issues.append("High timeout rate; ");
        }

        health.put("status", status);
        health.put("issues", issues.length() > 0 ? issues.toString() : "None");
        health.put("activeAgents", global.activeAgents());

        return health;
    }

    // ─── Recommendations ───────────────────────────────────────────────

    private String getRecommendation(AgentExecutionMetrics.AgentMetricsSnapshot snapshot,
                                      AgentCircuitBreaker.CircuitMetrics circuit) {
        StringBuilder rec = new StringBuilder();

        // High failure rate
        if (snapshot.failureRate() > 0.3) {
            rec.append("High failure rate detected. Check agent logs for errors. ");
        }

        // High timeout rate
        if (circuit.timeoutRate() > 20) {
            rec.append("High timeout rate. Consider increasing timeout or optimizing agent. ");
        }

        // Circuit open
        if (circuit.status() == AgentCircuitBreaker.CircuitStatus.OPEN) {
            rec.append("Circuit is OPEN. Agent is temporarily disabled. ");
            rec.append("Will auto-recover in 30 seconds. ");
        }

        // High latency
        if (snapshot.p95DurationMs() > 10000) {
            rec.append("High P95 latency (>10s). Check for slow tool calls or LLM timeouts. ");
        }

        // High token usage
        if (snapshot.avgTokensPerExecution() > 5000) {
            rec.append("High token usage. Consider optimizing prompts or adding compression. ");
        }

        if (rec.length() == 0) {
            rec.append("Agent is performing well. No issues detected.");
        }

        return rec.toString();
    }
}
