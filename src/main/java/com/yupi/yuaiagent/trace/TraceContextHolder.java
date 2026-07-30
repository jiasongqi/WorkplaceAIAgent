package com.yupi.yuaiagent.trace;

/**
 * Thread-local bridge so sandbox / tool events can attach spans to the active TraceContext.
 */
public final class TraceContextHolder {

    private static final ThreadLocal<TraceContext> HOLDER = new ThreadLocal<>();

    private TraceContextHolder() {}

    public static void set(TraceContext ctx) {
        HOLDER.set(ctx);
    }

    public static TraceContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
