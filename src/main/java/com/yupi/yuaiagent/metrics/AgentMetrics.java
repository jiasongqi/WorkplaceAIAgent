package com.yupi.yuaiagent.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent Metrics — Micrometer-based metrics for agent performance monitoring.
 *
 * <p>Exposed metrics (available via /actuator/prometheus):</p>
 * <ul>
 *     <li>{@code agent_request_duration} — Timer: request latency histogram</li>
 *     <li>{@code agent_tool_call_duration} — Timer: tool call latency histogram</li>
 *     <li>{@code agent_token_usage} — DistributionSummary: tokens consumed per request</li>
 *     <li>{@code agent_tool_call_total} — Counter: total tool calls by agent/tool/result</li>
 *     <li>{@code agent_active_requests} — Gauge: currently in-flight requests</li>
 *     <li>{@code agent_error_total} — Counter: errors by type</li>
 *     <li>{@code agent_memory_messages} — Gauge: current message count in memory</li>
 *     <li>{@code agent_step_count} — DistributionSummary: steps per request</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
public class AgentMetrics {

    private final MeterRegistry registry;

    // Timers
    private final Timer requestDurationTimer;
    private final Timer toolCallDurationTimer;

    // Counters
    private final Counter toolCallCounter;
    private final Counter errorCounter;

    // Distribution summaries
    private final DistributionSummary tokenUsageSummary;
    private final DistributionSummary stepCountSummary;

    // Gauges
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Request duration timer
        this.requestDurationTimer = Timer.builder("agent_request_duration")
                .description("Agent request processing duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram(true)
                .register(registry);

        // Tool call duration timer
        this.toolCallDurationTimer = Timer.builder("agent_tool_call_duration")
                .description("Tool call execution duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram(true)
                .register(registry);

        // Tool call counter (tagged by agent, tool, result)
        this.toolCallCounter = Counter.builder("agent_tool_call_total")
                .description("Total tool calls")
                .register(registry);

        // Error counter
        this.errorCounter = Counter.builder("agent_error_total")
                .description("Total agent errors")
                .register(registry);

        // Token usage distribution
        this.tokenUsageSummary = DistributionSummary.builder("agent_token_usage")
                .description("Tokens consumed per request")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Step count distribution
        this.stepCountSummary = DistributionSummary.builder("agent_step_count")
                .description("Steps per request")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Active requests gauge
        Gauge.builder("agent_active_requests", activeRequests, AtomicInteger::get)
                .description("Currently active agent requests")
                .register(registry);

        log.info("[Metrics] AgentMetrics initialized with Micrometer registry: {}", registry.getClass().getSimpleName());
    }

    // ─── Request Lifecycle ─────────────────────────────────────────────

    /**
     * Record a completed agent request.
     */
    public void recordRequest(String agentType, long durationMs, int tokensUsed, int steps, boolean success) {
        // Duration timer
        requestDurationTimer.record(durationMs, TimeUnit.MILLISECONDS);

        // Token usage
        tokenUsageSummary.record(tokensUsed);

        // Step count
        stepCountSummary.record(steps);

        // Log significant requests
        if (durationMs > 5000 || tokensUsed > 10000) {
            log.warn("[Metrics] Slow/heavy request: agent={}, duration={}ms, tokens={}, steps={}",
                    agentType, durationMs, tokensUsed, steps);
        }

        log.debug("[Metrics] Request recorded: agent={}, duration={}ms, tokens={}, steps={}, success={}",
                agentType, durationMs, tokensUsed, steps, success);
    }

    /**
     * Increment active requests gauge.
     */
    public void incrementActiveRequests() {
        activeRequests.incrementAndGet();
    }

    /**
     * Decrement active requests gauge.
     */
    public void decrementActiveRequests() {
        activeRequests.decrementAndGet();
    }

    // ─── Tool Call Tracking ────────────────────────────────────────────

    /**
     * Record a tool call.
     */
    public void recordToolCall(String agentType, String toolName, long durationMs, boolean success) {
        String result = success ? "success" : "failure";

        // Counter with tags
        Counter.builder("agent_tool_call_total")
                .tag("agent", agentType)
                .tag("tool", toolName)
                .tag("result", result)
                .register(registry)
                .increment();

        // Duration timer
        toolCallDurationTimer.record(durationMs, TimeUnit.MILLISECONDS);

        log.debug("[Metrics] Tool call: agent={}, tool={}, duration={}ms, result={}",
                agentType, toolName, durationMs, result);
    }

    // ─── Error Tracking ────────────────────────────────────────────────

    /**
     * Record an error.
     */
    public void recordError(String agentType, String errorType) {
        Counter.builder("agent_error_total")
                .tag("agent", agentType)
                .tag("type", errorType)
                .register(registry)
                .increment();

        log.debug("[Metrics] Error recorded: agent={}, type={}", agentType, errorType);
    }

    // ─── Memory Metrics ────────────────────────────────────────────────

    /**
     * Update memory message count gauge.
     */
    public void updateMemoryMessageCount(String userId, int messageCount) {
        // Use a tagged gauge for per-user memory tracking
        // Note: This creates a new gauge per unique userId tag
        Gauge.builder("agent_memory_messages", () -> messageCount)
                .tag("user", userId)
                .description("Current message count in agent memory")
                .register(registry);
    }

    // ─── Custom Timer Builder ──────────────────────────────────────────

    /**
     * Create a custom timer for specific operations.
     */
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    /**
     * Stop a timer and record the duration.
     */
    public void stopTimer(Timer.Sample sample, String timerName, String... tags) {
        Timer timer = Timer.builder(timerName)
                .tags(tags)
                .register(registry);
        sample.stop(timer);
    }

    // ─── Snapshot for Reporting ────────────────────────────────────────

    /**
     * Get metrics snapshot for reporting.
     */
    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
                activeRequests.get(),
                requestDurationTimer.count(),
                requestDurationTimer.totalTime(TimeUnit.MILLISECONDS),
                toolCallDurationTimer.count(),
                tokenUsageSummary.totalAmount(),
                tokenUsageSummary.count(),
                stepCountSummary.mean()
        );
    }

    /**
     * Metrics snapshot record.
     */
    public record MetricsSnapshot(
            int activeRequests,
            long totalRequests,
            double totalRequestDurationMs,
            long totalToolCalls,
            double totalTokensUsed,
            long totalTokenSamples,
            double avgStepsPerRequest
    ) {
        public double avgRequestDurationMs() {
            return totalRequests == 0 ? 0 : totalRequestDurationMs / totalRequests;
        }

        public double avgTokensPerRequest() {
            return totalTokenSamples == 0 ? 0 : totalTokensUsed / totalTokenSamples;
        }
    }
}
