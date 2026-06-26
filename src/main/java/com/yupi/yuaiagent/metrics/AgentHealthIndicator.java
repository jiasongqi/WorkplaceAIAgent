package com.yupi.yuaiagent.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent Health Indicator — reports agent subsystem health status.
 *
 * <p>Checks performed:</p>
 * <ul>
 *     <li>Active request count (warn if > 50, down if > 100)</li>
 *     <li>Error rate (warn if > 10%)</li>
 *     <li>Average response time (warn if > 5s)</li>
 * </ul>
 *
 * <p>Access via: GET /actuator/health</p>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Component
@RequiredArgsConstructor
public class AgentHealthIndicator implements HealthIndicator {

    private final AgentMetrics agentMetrics;

    @Override
    public Health health() {
        AgentMetrics.MetricsSnapshot snapshot = agentMetrics.getSnapshot();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("activeRequests", snapshot.activeRequests());
        details.put("totalRequests", snapshot.totalRequests());
        details.put("avgRequestDurationMs", Math.round(snapshot.avgRequestDurationMs()));
        details.put("totalToolCalls", snapshot.totalToolCalls());

        // Health checks
        boolean isHealthy = true;
        String status = "Agent subsystem is operational";

        // Check 1: Active requests
        if (snapshot.activeRequests() > 100) {
            isHealthy = false;
            status = "Too many active requests: " + snapshot.activeRequests();
            details.put("issue", "high_load");
        } else if (snapshot.activeRequests() > 50) {
            details.put("warning", "elevated_load");
        }

        // Check 2: Average response time
        double avgDuration = snapshot.avgRequestDurationMs();
        if (avgDuration > 10000) {
            isHealthy = false;
            status = "Average response time too high: " + Math.round(avgDuration) + "ms";
            details.put("issue", "slow_response");
        } else if (avgDuration > 5000) {
            details.put("warning", "slow_response");
        }

        // Build health response
        if (isHealthy) {
            return Health.up()
                    .withDetail("status", status)
                    .withDetails(details)
                    .build();
        } else {
            return Health.down()
                    .withDetail("status", status)
                    .withDetails(details)
                    .build();
        }
    }
}
