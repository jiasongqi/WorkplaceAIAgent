package com.yupi.yuaiagent.sessionstate;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Request-scoped handoff permission downgrade (phantom-privilege guard).
 * <p>
 * When set, {@link com.yupi.yuaiagent.permission.AgentPermissionService} intersects
 * the agent profile with this scope — target agent cannot inherit the source agent's tools.
 */
public final class HandoffScopeContext {

    private static final ThreadLocal<Set<String>> SCOPE = new ThreadLocal<>();

    private HandoffScopeContext() {
    }

    public static void install(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            SCOPE.remove();
            return;
        }
        SCOPE.set(Collections.unmodifiableSet(new HashSet<>(patterns)));
    }

    public static void clear() {
        SCOPE.remove();
    }

    public static Set<String> current() {
        Set<String> s = SCOPE.get();
        return s == null ? Set.of() : s;
    }

    public static boolean isActive() {
        Set<String> s = SCOPE.get();
        return s != null && !s.isEmpty();
    }
}
