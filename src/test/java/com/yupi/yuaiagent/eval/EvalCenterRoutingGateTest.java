package com.yupi.yuaiagent.eval;

import com.yupi.yuaiagent.metrics.AgentExecutionMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvalCenter gate test — loads classpath:eval/routing-suite.yaml and asserts passRate.
 */
class EvalCenterRoutingGateTest {

    private EvalCenter evalCenter;

    @BeforeEach
    void setUp() {
        EvalScorer scorer = new EvalScorer();
        AgentExecutionMetrics metrics = new AgentExecutionMetrics(new SimpleMeterRegistry());
        evalCenter = new EvalCenter(scorer, metrics);
    }

    @Test
    void routingSuite_passRateAboveGate() {
        assertTrue(evalCenter.getSuiteNames().contains("routing-suite"),
                "routing-suite.yaml should be on classpath");
        EvalReport report = evalCenter.runAndAssertGate("routing-suite");
        assertFalse(report.isRegression());
        assertTrue(report.getPassRate() >= 0.8, "passRate=" + report.getPassRate());
        assertEquals(report.getTotalCases(), report.getPassedCases());
    }
}
