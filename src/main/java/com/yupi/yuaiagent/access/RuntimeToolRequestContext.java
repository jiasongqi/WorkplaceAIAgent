package com.yupi.yuaiagent.access;

import com.yupi.yuaiagent.pack.PackPreferenceMode;

import java.util.Optional;
import java.util.Set;

/**
 * Request-scoped tool view for Negotiation/Escape. Never stored on the singleton Agent.
 */
public final class RuntimeToolRequestContext {

    public record View(Set<String> effectivePatterns, PackPreferenceMode preferenceMode, boolean filterEnabled) {
    }

    private static final ThreadLocal<View> CURRENT = new ThreadLocal<>();

    private RuntimeToolRequestContext() {
    }

    public static void install(View view) {
        CURRENT.set(view);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<View> current() {
        return Optional.ofNullable(CURRENT.get());
    }
}
