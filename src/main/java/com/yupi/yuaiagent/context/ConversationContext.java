package com.yupi.yuaiagent.context;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Immutable conversation context — shared by all Agents in a workflow.
 * Contains only "about the user and conversation" static info.
 * No execution state.
 *
 * @param userProfile         user profile injection text
 * @param conversationSummary compressed conversation summary
 * @param recentMessages      last N messages for LLM context
 */
public record ConversationContext(
    String userProfile,
    String conversationSummary,
    List<Message> recentMessages
) {}
