package com.yupi.yuaiagent.agent.loop;

/**
 * Nested Agent depth limit (Ch4 Depth Limit rule of thumb: ≤3).
 */
public final class AgentDepthContext {

    public static final int DEFAULT_MAX_DEPTH = 3;

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private AgentDepthContext() {
    }

    public static int current() {
        return DEPTH.get();
    }

    public static boolean canEnter() {
        return canEnter(DEFAULT_MAX_DEPTH);
    }

    public static boolean canEnter(int maxDepth) {
        return DEPTH.get() < maxDepth;
    }

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int d = DEPTH.get() - 1;
        if (d <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(d);
        }
    }

    public static String denyMessage(int maxDepth) {
        return "Agent Depth Limit 触顶（max=" + maxDepth
                + "）：子任务嵌套过深，请合并目标或改为 Plan-and-Execute 单层执行。";
    }

    public static <T> T runWithDepth(java.util.function.Supplier<T> work,
                                     java.util.function.Supplier<T> onDeny) {
        return runWithDepth(DEFAULT_MAX_DEPTH, work, onDeny);
    }

    public static <T> T runWithDepth(int maxDepth,
                                     java.util.function.Supplier<T> work,
                                     java.util.function.Supplier<T> onDeny) {
        if (!canEnter(maxDepth)) {
            return onDeny.get();
        }
        enter();
        try {
            return work.get();
        } finally {
            exit();
        }
    }
}
