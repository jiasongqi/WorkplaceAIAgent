package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolSideEffectPolicyTest {

    @Test
    void readOnlyRetryable() {
        assertTrue(ToolSideEffectPolicy.isRetryableOnTimeout("searchWeb"));
        assertTrue(ToolSideEffectPolicy.isRetryableOnTimeout("readFileChunk"));
        assertTrue(ToolSideEffectPolicy.isRetryableOnTimeout("checkAsyncToolTask"));
    }

    @Test
    void sideEffectsNotRetryable() {
        assertFalse(ToolSideEffectPolicy.isRetryableOnTimeout("writeFile"));
        assertFalse(ToolSideEffectPolicy.isRetryableOnTimeout("downloadResource"));
        assertFalse(ToolSideEffectPolicy.isRetryableOnTimeout("executeTerminalCommand"));
        assertTrue(ToolSideEffectPolicy.isSideEffect("generatePDF"));
    }
}
