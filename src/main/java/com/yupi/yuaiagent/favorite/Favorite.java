package com.yupi.yuaiagent.favorite;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Favorite — bookmarked message with content snapshot.
 * <p>
 * Even if the original message or session is deleted, the favorite retains
 * a contentSnapshot + sessionTitleSnapshot so it's never lost.
 *
 * @author jsq
 */
@Data
public class Favorite {

    private String favoriteId;
    private String userId;
    private String chatId;
    private String messageId;

    /** Snapshot of message content at favorite time. Survives message deletion. */
    private String contentSnapshot;

    /** Snapshot of session title at favorite time. Survives session deletion. */
    private String sessionTitleSnapshot;

    /** Message role: "user" or "assistant". */
    private String role;

    /** True if the source session/message has been deleted. */
    private boolean orphaned;

    private LocalDateTime createdAt;
}
