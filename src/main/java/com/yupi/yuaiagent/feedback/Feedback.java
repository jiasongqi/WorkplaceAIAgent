package com.yupi.yuaiagent.feedback;

import java.time.LocalDateTime;

/**
 * User Feedback — captures thumbs up/down ratings on agent responses.
 *
 * <p>TODO(five-star): extend with optional 1-5 star score (schema + API + UI),
 * map score &lt;= 2 to the same Reflexion writeback path as DOWN. Not in current release.
 *
 * @param id         feedback ID
 * @param userId     user who gave feedback
 * @param chatId     chat session ID
 * @param messageId  message ID that was rated
 * @param rating     UP or DOWN
 * @param comment    optional text feedback
 * @param agentType  which agent produced the response
 * @param intent     what intent was detected
 * @param createdAt  when feedback was given
 * @author jsq
 */
public record Feedback(
        String id,
        String userId,
        String chatId,
        String messageId,
        Rating rating,
        String comment,
        String agentType,
        String intent,
        LocalDateTime createdAt
) {
    public enum Rating { UP, DOWN }
}
