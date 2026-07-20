package com.yupi.yuaiagent.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent Execution Metrics — per-agent execution monitoring.
 *
 * <p>Tracks each agent's:</p>
 * <ul>
 *     <li>Execution count and success rate</li>
 *     <li>Execution duration (P50/P95/P99)</li>
 *     <li>Token consumption</li>
 *     <li>Tool call count</li>
 *     <li>Timeout count</li>
 *     <li>Error count by type</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
public class AgentExecutionMetrics {

    private final MeterRegistry registry;

    /** Per-agent metrics cache */
    private final Map<String, AgentMetricsBundle> agentMetrics = new ConcurrentHashMap<>();

    /** Global active agent count */
    private final AtomicInteger activeAgentCount = new AtomicInteger(0);

    public AgentExecutionMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Register global gauge
        Gauge.builder("agent_active_count", activeAgentCount, AtomicInteger::get)
                .description("Number of currently executing agents")
                .register(registry);

        log.info("[AgentExecutionMetrics] Initialized");
    }

    /**
     * Get or create metrics bundle for an agent.
     */
    private AgentMetricsBundle getBundle(String agentType) {
        return agentMetrics.computeIfAbsent(agentType, type -> {
            Timer executionTimer = Timer.builder("agent_execution_duration")
                    .tag("agent", type)
                    .description("Agent execution duration")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .publishPercentileHistogram(true)
                    .register(registry);

            Counter successCounter = Counter.builder("agent_execution_success")
                    .tag("agent", type)
                    .description("Successful agent executions")
                    .register(registry);

            Counter failureCounter = Counter.builder("agent_execution_failure")
                    .tag("agent", type)
                    .description("Failed agent executions")
                    .register(registry);

            Counter timeoutCounter = Counter.builder("agent_execution_timeout")
                    .tag("agent", type)
                    .description("Agent execution timeouts")
                    .register(registry);

            DistributionSummary tokenUsage = DistributionSummary.builder("agent_token_consumption")
                    .tag("agent", type)
                    .description("Token consumption per execution")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry);

            DistributionSummary toolCallCount = DistributionSummary.builder("agent_tool_calls")
                    .tag("agent", type)
                    .description("Tool calls per execution")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry);

            DistributionSummary stepCount = DistributionSummary.builder("agent_step_count")
                    .tag("agent", type)
                    .description("Steps per execution")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry);

            return new AgentMetricsBundle(executionTimer, successCounter, failureCounter,
                    timeoutCounter, tokenUsage, toolCallCount, stepCount);
        });
    }

    // ─── Recording Methods ─────────────────────────────────────────────

    /**
     * Record agent execution start.
     *
     * @param agentType agent type
     * @return execution ID for tracking
     */
    public String recordExecutionStart(String agentType) {
        activeAgentCount.incrementAndGet();
        String executionId = agentType + "_" + System.currentTimeMillis();
        log.debug("[Metrics] Agent execution started: agent={}, id={}", agentType, executionId);
        return executionId;
    }

    /**
     * Record agent execution completion.
     *
     * @param agentType  agent type
     * @param durationMs execution duration in ms
     * @param tokensUsed tokens consumed
     * @param toolCalls  number of tool calls
     * @param steps      number of steps
     * @param success    whether execution succeeded
     */
    public void recordExecutionEnd(String agentType, long durationMs, int tokensUsed,
                                    int toolCalls, int steps, boolean success) {
        activeAgentCount.decrementAndGet();
        AgentMetricsBundle bundle = getBundle(agentType);

        // Record duration
        bundle.executionTimer.record(durationMs, TimeUnit.MILLISECONDS);

        // Record success/failure
        if (success) {
            bundle.successCounter.increment();
        } else {
            bundle.failureCounter.increment();
        }

        // Record token usage
        bundle.tokenUsage.record(tokensUsed);

        // Record tool calls
        bundle.toolCallCount.record(toolCalls);

        // Record steps
        bundle.stepCount.record(steps);

        log.debug("[Metrics] Agent execution completed: agent={}, duration={}ms, tokens={}, tools={}, steps={}, success={}",
                agentType, durationMs, tokensUsed, toolCalls, steps, success);
    }

    /**
     * Record agent execution timeout.
     *
     * @param agentType agent type
     */
    public void recordTimeout(String agentType) {
        activeAgentCount.decrementAndGet();
        AgentMetricsBundle bundle = getBundle(agentType);
        bundle.timeoutCounter.increment();

        log.warn("[Metrics] Agent execution timeout: agent={}", agentType);
    }

    // ─── Query Methods ─────────────────────────────────────────────────

    /**
     * Get metrics snapshot for an agent.
     */
    @SuppressWarnings("deprecation")
    public AgentMetricsSnapshot getSnapshot(String agentType) {
        AgentMetricsBundle bundle = getBundle(agentType);

        return new AgentMetricsSnapshot(
                agentType,
                bundle.executionTimer.count(),
                bundle.executionTimer.totalTime(TimeUnit.MILLISECONDS),
                bundle.executionTimer.percentile(0.5, TimeUnit.MILLISECONDS),
                bundle.executionTimer.percentile(0.95, TimeUnit.MILLISECONDS),
                bundle.executionTimer.percentile(0.99, TimeUnit.MILLISECONDS),
                bundle.successCounter.count(),
                bundle.failureCounter.count(),
                bundle.timeoutCounter.count(),
                bundle.tokenUsage.totalAmount(),
                bundle.tokenUsage.count(),
                bundle.toolCallCount.mean(),
                bundle.stepCount.mean()
        );
    }

    /**
     * Get all agent metrics snapshots.
     */
    public Map<String, AgentMetricsSnapshot> getAllSnapshots() {
        Map<String, AgentMetricsSnapshot> snapshots = new ConcurrentHashMap<>();
        for (String agentType : agentMetrics.keySet()) {
            snapshots.put(agentType, getSnapshot(agentType));
        }
        return snapshots;
    }

    /**
     * Get global metrics.
     */
    public GlobalMetrics getGlobalMetrics() {
        long totalExecutions = 0;
        long totalSuccess = 0;
        long totalFailures = 0;
        long totalTimeouts = 0;

        for (AgentMetricsBundle bundle : agentMetrics.values()) {
            totalExecutions += bundle.executionTimer.count();
            totalSuccess += bundle.successCounter.count();
            totalFailures += bundle.failureCounter.count();
            totalTimeouts += bundle.timeoutCounter.count();
        }

        double successRate = totalExecutions > 0 ? (double) totalSuccess / totalExecutions : 0;

        return new GlobalMetrics(
                activeAgentCount.get(),
                totalExecutions,
                totalSuccess,
                totalFailures,
                totalTimeouts,
                successRate
        );
    }

    // ─── Inner Classes ─────────────────────────────────────────────────

    /**
     * Per-agent metrics bundle.
     */
    private record AgentMetricsBundle(
            Timer executionTimer,
            Counter successCounter,
            Counter failureCounter,
            Counter timeoutCounter,
            DistributionSummary tokenUsage,
            DistributionSummary toolCallCount,
            DistributionSummary stepCount
    ) {}

    /**
     * Agent metrics snapshot.
     */
    public record AgentMetricsSnapshot(
            String agentType,
            long totalExecutions,
            double totalDurationMs,
            double p50DurationMs,
            double p95DurationMs,
            double p99DurationMs,
            double successCount,
            double failureCount,
            double timeoutCount,
            double totalTokens,
            long tokenSamples,
            double avgToolCalls,
            double avgSteps
    ) {
        public double successRate() {
            return totalExecutions > 0 ? successCount / totalExecutions : 0;
        }

        public double failureRate() {
            return totalExecutions > 0 ? failureCount / totalExecutions : 0;
        }

        public double avgDurationMs() {
            return totalExecutions > 0 ? totalDurationMs / totalExecutions : 0;
        }

        public double avgTokensPerExecution() {
            return tokenSamples > 0 ? totalTokens / tokenSamples : 0;
        }
    }

    /**
     * Global metrics.
     */
    public record GlobalMetrics(
            int activeAgents,
            long totalExecutions,
            long totalSuccess,
            long totalFailures,
            long totalTimeouts,
            double successRate
    ) {}
}
