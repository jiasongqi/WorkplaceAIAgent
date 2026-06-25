package com.yupi.yuaiagent.context;

import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.message.PersistentMessageRepository;
import com.yupi.yuaiagent.profile.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Builds an immutable ConversationContext from existing infrastructure.
 * Called once per workflow, shared by all Agents.
 *
 * @author jsq
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationContextBuilder {

    private final ChatMemoryManager chatMemoryManager;
    private final UserProfileService userProfileService;
    private final PersistentMessageRepository messageRepository;

    public ConversationContext build(String chatId, String userId) {
        // 1. User profile
        String profile = StringUtils.hasText(userId)
            ? userProfileService.buildPromptInjection(userId) : "";

        // 2. Conversation summary (reuse ChatMemoryManager compression)
        String summary = chatMemoryManager.getCompressedSummary(chatId);

        // 3. Recent messages (max 20, avoid token explosion)
        List<Message> recent = messageRepository.findByChatId(chatId).stream()
            .skip(Math.max(0, messageRepository.countByChatId(chatId) - 20))
            .map(this::toLlmMessage)
            .toList();

        return new ConversationContext(profile, summary, recent);
    }

    private Message toLlmMessage(com.yupi.yuaiagent.message.PersistentChatMessage pm) {
        return switch (pm.getRole()) {
            case "user" -> new org.springframework.ai.chat.messages.UserMessage(pm.getContent());
            case "assistant" -> new org.springframework.ai.chat.messages.AssistantMessage(pm.getContent());
            case "system" -> new org.springframework.ai.chat.messages.SystemMessage(pm.getContent());
            default -> new org.springframework.ai.chat.messages.UserMessage(pm.getContent());
        };
    }
}
