package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolIdempotencyStoreTest {

    @Test
    void rememberAndReplay() {
        ToolIdempotencyStore store = new ToolIdempotencyStore();
        String key = store.key("writeFile", "a.txt::123");
        store.remember(key, "File written successfully to: a.txt");
        var hit = store.findOrRemember(key, () -> "SHOULD_NOT_RUN");
        assertTrue(hit.isPresent());
        assertTrue(hit.get().contains("idempotent replay"));
        assertFalse(hit.get().contains("SHOULD_NOT_RUN"));
    }
}
