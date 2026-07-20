package com.yupi.yuaiagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Add favorite request.
 *
 * @param chatId    the chat session ID
 * @param messageId the message ID to favorite (may be null for SSE-sourced messages)
 * @param content   message content (used when messageId is unavailable)
 * @param role      message role: "user" or "assistant"
 */
public record AddFavoriteRequest(
        @NotBlank(message = "chatId不能为空") String chatId,
        String messageId,
        @Size(max = 10000, message = "内容长度不能超过10000字符") String content,
        @NotBlank(message = "角色不能为空") String role) {}
