package com.yupi.yuaiagent.message;

/**
 * Lifecycle of a chat message for SSE resume.
 */
public enum MessageStatus {
    STREAMING,
    PARTIAL,
    COMPLETE;

    public static MessageStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return COMPLETE;
        }
        try {
            return MessageStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMPLETE;
        }
    }
}
