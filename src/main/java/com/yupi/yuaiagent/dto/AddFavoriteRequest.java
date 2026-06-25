package com.yupi.yuaiagent.dto;

/**
 * Add favorite request.
 *
 * @param chatId    the chat session ID
 * @param messageId the message ID to favorite (may be null for SSE-sourced messages)
 * @param content   message content (used when messageId is unavailable)
 * @param role      message role: "user" or "assistant"
 */
public record AddFavoriteRequest(String chatId, String messageId, String content, String role) {}
