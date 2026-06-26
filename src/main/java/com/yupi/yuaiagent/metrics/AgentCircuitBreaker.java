package com.yupi.yuaiagent.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent Circuit Breaker — prevents cascading failures when agents are stuck.
 *
 * <p>States:</p>
 * <ul>
 *     <li>CLOSED — normal operation, requests pass through</li>
 *     <li>OPEN — too many failures, requests are rejected immediately</li>
 *     <li>HALF_OPEN — testing if service recovered</li>
 * </ul>
 *
 * <p>Triggers:</p>
 * <ul>
 *     <li>Failure rate exceeds threshold (default 50%)</li>
 *     <li>Timeout rate exceeds threshold (default 30%)</li>
 *     <li>Consecutive failures exceed limit (default 5)</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
public class AgentCircuitBreaker {

    /** Failure rate threshold to open circuit (0-100) */
    private static final double FAILURE_RATE_THRESHOLD = 50.0;

    /** Timeout rate threshold to open circuit (0-100) */
    private static final double TIMEOUT_RATE_THRESHOLD = 30.0;

    /** Consecutive failures to open circuit */
    private static final int CONSECUTIVE_FAILURES_THRESHOLD = 5;

    /** Reset timeout in ms (30 seconds) */
    private static final long RESET_TIMEOUT_MS = 30_000;

    /** Minimum requests before evaluating circuit */
    private static final int MIN_REQUESTS = 10;

    /** Per-agent circuit state */
    private final Map<String, AgentCircuitState> circuits = new ConcurrentHashMap<>();

    /**
     * Check if a request is allowed for an agent.
     *
     * @param agentType agent type
     * @return true if request is allowed
     */
    public boolean isAllowed(String agentType) {
        AgentCircuitState state = getState(agentType);

        if (state.status == CircuitStatus.CLOSED) {
            return true;
        }

        if (state.status == CircuitStatus.OPEN) {
            // Check if reset timeout has passed
            if (System.currentTimeMillis() - state.lastFailureTime.get() > RESET_TIMEOUT_MS) {
                state.status = CircuitStatus.HALF_OPEN;
                log.info("[CircuitBreaker] Agent {} transitioning to HALF_OPEN", agentType);
                return true;
            }
            return false;
        }

        // HALF_OPEN — allow one request to test
        return true;
    }

    /**
     * Record a successful execution.
     *
     * @param agentType agent type
     */
    public void recordSuccess(String agentType) {
        AgentCircuitState state = getState(agentType);
        state.totalRequests.incrementAndGet();
        state.successCount.incrementAndGet();
        state.consecutiveFailures.set(0);

        if (state.status == CircuitStatus.HALF_OPEN) {
            state.status = CircuitStatus.CLOSED;
            log.info("[CircuitBreaker] Agent {} recovered, circuit CLOSED", agentType);
        }
    }

    /**
     * Record a failed execution.
     *
     * @param agentType agent type
     * @param isTimeout whether the failure was a timeout
     */
    public void recordFailure(String agentType, boolean isTimeout) {
        AgentCircuitState state = getState(agentType);
        state.totalRequests.incrementAndGet();
        state.failureCount.incrementAndGet();
        state.lastFailureTime.set(System.currentTimeMillis());

        if (isTimeout) {
            state.timeoutCount.incrementAndGet();
        }

        int consecutive = state.consecutiveFailures.incrementAndGet();

        // Check if circuit should open
        if (shouldOpen(state, consecutive)) {
            state.status = CircuitStatus.OPEN;
            log.warn("[CircuitBreaker] Agent {} circuit OPENED: consecutiveFailures={}, failureRate={}%",
                    agentType, consecutive, getFailureRate(state));
        }
    }

    /**
     * Get circuit status for an agent.
     */
    public CircuitStatus getStatus(String agentType) {
        return getState(agentType).status;
    }

    /**
     * Get circuit metrics for an agent.
     */
    public CircuitMetrics getMetrics(String agentType) {
        AgentCircuitState state = getState(agentType);
        return new CircuitMetrics(
                agentType,
                state.status,
                state.totalRequests.get(),
                state.successCount.get(),
                state.failureCount.get(),
                state.timeoutCount.get(),
                state.consecutiveFailures.get(),
                getFailureRate(state),
                getTimeoutRate(state)
        );
    }

    /**
     * Get all circuit metrics.
     */
    public Map<String, CircuitMetrics> getAllMetrics() {
        Map<String, CircuitMetrics> metrics = new ConcurrentHashMap<>();
        for (String agentType : circuits.keySet()) {
            metrics.put(agentType, getMetrics(agentType));
        }
        return metrics;
    }

    /**
     * Manually reset a circuit.
     */
    public void reset(String agentType) {
        AgentCircuitState state = getState(agentType);
        state.status = CircuitStatus.CLOSED;
        state.consecutiveFailures.set(0);
        log.info("[CircuitBreaker] Agent {} circuit manually reset to CLOSED", agentType);
    }

    // ─── Private Methods ───────────────────────────────────────────────

    private AgentCircuitState getState(String agentType) {
        return circuits.computeIfAbsent(agentType, k -> new AgentCircuitState());
    }

    private boolean shouldOpen(AgentCircuitState state, int consecutiveFailures) {
        // Consecutive failures threshold
        if (consecutiveFailures >= CONSECUTIVE_FAILURES_THRESHOLD) {
            return true;
        }

        // Need minimum requests to evaluate rate
        long total = state.totalRequests.get();
        if (total < MIN_REQUESTS) {
            return false;
        }

        // Failure rate threshold
        if (getFailureRate(state) >= FAILURE_RATE_THRESHOLD) {
            return true;
        }

        // Timeout rate threshold
        if (getTimeoutRate(state) >= TIMEOUT_RATE_THRESHOLD) {
            return true;
        }

        return false;
    }

    private double getFailureRate(AgentCircuitState state) {
        long total = state.totalRequests.get();
        if (total == 0) return 0;
        return (double) state.failureCount.get() / total * 100;
    }

    private double getTimeoutRate(AgentCircuitState state) {
        long total = state.totalRequests.get();
        if (total == 0) return 0;
        return (double) state.timeoutCount.get() / total * 100;
    }

    // ─── Inner Classes ─────────────────────────────────────────────────

    /**
     * Circuit status.
     */
    public enum CircuitStatus {
        CLOSED,     // Normal operation
        OPEN,       // Rejecting requests
        HALF_OPEN   // Testing recovery
    }

    /**
     * Per-agent circuit state.
     */
    private static class AgentCircuitState {
        volatile CircuitStatus status = CircuitStatus.CLOSED;
        final AtomicInteger totalRequests = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);
        final AtomicInteger timeoutCount = new AtomicInteger(0);
        final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        final AtomicLong lastFailureTime = new AtomicLong(0);
    }

    /**
     * Circuit metrics.
     */
    public record CircuitMetrics(
            String agentType,
            CircuitStatus status,
            long totalRequests,
            long successCount,
            long failureCount,
            long timeoutCount,
            int consecutiveFailures,
            double failureRate,
            double timeoutRate
    ) {}
}
