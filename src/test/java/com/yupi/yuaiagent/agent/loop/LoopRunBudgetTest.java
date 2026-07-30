package com.yupi.yuaiagent.agent.loop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoopRunBudgetTest {

    @Test
    void exhaustsOnMaxRunTokens() {
        LoopRunBudget budget = new LoopRunBudget(100, -1);
        budget.record(60);
        assertFalse(budget.isExhausted());
        budget.record(50);
        assertTrue(budget.isExhausted());
        assertTrue(budget.getExhaustReason().contains("单次运行"));
    }

    @Test
    void exhaustsOnDailyRemainingSnapshot() {
        LoopRunBudget budget = new LoopRunBudget(0, 200);
        budget.record(150);
        assertFalse(budget.isExhausted());
        budget.record(60);
        assertTrue(budget.isExhausted());
        assertTrue(budget.getExhaustReason().contains("今日"));
    }

    @Test
    void ignoresNonPositiveRecords() {
        LoopRunBudget budget = new LoopRunBudget(100, -1);
        budget.record(0);
        budget.record(-5);
        assertEquals(0, budget.getTokensUsed());
        assertFalse(budget.isExhausted());
    }
}
