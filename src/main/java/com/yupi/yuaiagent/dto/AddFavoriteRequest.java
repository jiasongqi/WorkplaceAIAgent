package com.yupi.yuaiagent.dto;

/**
 * Add favorite request.
 *
 * @param chatId    the chat session ID
 * @param messageId the message ID to favorite
 */
public record AddFavoriteRequest(String chatId, String messageId) {}
