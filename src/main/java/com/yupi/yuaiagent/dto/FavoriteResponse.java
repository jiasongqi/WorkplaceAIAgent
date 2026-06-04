package com.yupi.yuaiagent.dto;

import java.time.LocalDateTime;

/**
 * Favorite detail response.
 */
public record FavoriteResponse(
    String favoriteId,
    String userId,
    String chatId,
    String messageId,
    String contentSnapshot,
    String sessionTitleSnapshot,
    String role,
    boolean orphaned,
    LocalDateTime createdAt
) {}
