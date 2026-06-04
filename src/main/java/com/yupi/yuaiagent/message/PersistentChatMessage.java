package com.yupi.yuaiagent.message;

import lombok.Data;

/**
 * Persistent chat message — the Source of Truth for all conversation data.
 * <p>
 * Unlike Spring AI's {@code Message} interface (which has no identity/timestamp),
 * this model carries a stable {@code messageId} (ULID), chat association, and
 * timestamp. All downstream features (history, search, favorites, export) are
 * built on this model.
 * <p>
 * ChatMemory is treated as a runtime cache that can be rebuilt from this store.
 *
 * @author jsq
 */
@Data
public class PersistentChatMessage {

    /**
     * Stable unique identifier (ULID format: time-ordered, readable).
     * Generated once at write time, never changes.
     */
    private String messageId;

    /** Chat session this message belongs to. */
    private String chatId;

    /** Message role: "user", "assistant", or "system". */
    private String role;

    /** Message content (plain text or markdown). */
    private String content;

    /** Epoch millis when the message was created. */
    private long timestamp;
}
