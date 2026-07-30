package com.yupi.yuaiagent.controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the most recent sandbox execution summary for UX / diagnostics.
 */
public final class LastSandboxExecHolder {

    private static final Map<String, Object> LAST = new ConcurrentHashMap<>();

    private LastSandboxExecHolder() {}

    public static void put(String userId, String command, boolean success, long durationMs) {
        LAST.clear();
        if (userId != null) {
            LAST.put("userId", userId);
        }
        LAST.put("command", command);
        LAST.put("success", success);
        LAST.put("durationMs", durationMs);
    }

    public static Map<String, Object> get() {
        return Map.copyOf(LAST);
    }
}
