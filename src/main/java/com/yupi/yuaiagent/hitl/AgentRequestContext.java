package com.yupi.yuaiagent.hitl;

/**
 * Request-scoped context so tools can access userId/chatId/approvalId without
 * polluting every Agent method signature.
 */
public final class AgentRequestContext {

    private static final ThreadLocal<Holder> HOLDER = new ThreadLocal<>();

    private AgentRequestContext() {}

    public record Holder(String userId, String chatId, String approvalId) {}

    public static void set(String userId, String chatId, String approvalId) {
        HOLDER.set(new Holder(userId, chatId, approvalId));
    }

    public static Holder get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static String userId() {
        Holder h = HOLDER.get();
        return h != null ? h.userId() : null;
    }

    public static String chatId() {
        Holder h = HOLDER.get();
        return h != null ? h.chatId() : null;
    }

    public static String approvalId() {
        Holder h = HOLDER.get();
        return h != null ? h.approvalId() : null;
    }
}
