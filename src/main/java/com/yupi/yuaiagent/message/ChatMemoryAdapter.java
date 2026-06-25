package com.yupi.yuaiagent.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * Adapter bridging PersistentMessageRepository (Source of Truth) and Spring AI ChatMemory (runtime cache).
 * <p>
 * <b>Write path</b>: saves to PersistentMessage first, then syncs to ChatMemory (best-effort).
 * If ChatMemory sync fails, data integrity is preserved — the next read will rebuild from Truth.
 * <p>
 * <b>Read path for LLM</b>: tries ChatMemory cache first, validates consistency via messageCount,
 * falls back to PersistentMessage rebuild on miss or inconsistency.
 * <p>
 * <b>Read path for frontend</b>: reads directly from PersistentMessage (always consistent).
 *
 * @author jsq
 */
@Slf4j
@Service
public class ChatMemoryAdapter {

    @Resource
    private PersistentMessageRepository persistentRepo;

    @Resource
    private com.yupi.yuaiagent.chatmemory.ChatMemoryManager chatMemoryManager;

    // ─── Write ───

    /**
     * Adds a user message. Persists to Truth first, then syncs to ChatMemory.
     */
    public PersistentChatMessage addUserMessage(String chatId, String content) {
        return addMessage(chatId, "user", content, MessageSource.USER, null, null);
    }

    /**
     * Adds a user message with source tracking.
     */
    public PersistentChatMessage addUserMessage(String chatId, String content,
                                                 MessageSource sourceType, String sourceId, String sourceName) {
        return addMessage(chatId, "user", content, sourceType, sourceId, sourceName);
    }

    /**
     * Adds an assistant message. Persists to Truth first, then syncs to ChatMemory.
     */
    public PersistentChatMessage addAssistantMessage(String chatId, String content) {
        return addMessage(chatId, "assistant", content, MessageSource.AGENT, null, null);
    }

    /**
     * Adds an assistant message with source tracking.
     */
    public PersistentChatMessage addAssistantMessage(String chatId, String content,
                                                      MessageSource sourceType, String sourceId, String sourceName) {
        return addMessage(chatId, "assistant", content, sourceType, sourceId, sourceName);
    }

    /**
     * Adds a system message. Persists to Truth first, then syncs to ChatMemory.
     */
    public PersistentChatMessage addSystemMessage(String chatId, String content) {
        return addMessage(chatId, "system", content, MessageSource.SYSTEM, null, null);
    }

    private PersistentChatMessage addMessage(String chatId, String role, String content,
                                              MessageSource sourceType, String sourceId, String sourceName) {
        // 1. Persist to Truth (Source of Truth — must succeed)
        PersistentChatMessage pm = persistentRepo.save(chatId, role, content,
            sourceType, sourceId, sourceName);

        // 2. Sync to ChatMemory (best-effort — failure is tolerable)
        try {
            syncToChatMemory(chatId, pm);
        } catch (Exception e) {
            log.warn("[ChatMemoryAdapter] ChatMemory sync failed for chatId={}, will rebuild on next read: {}",
                    chatId, e.getMessage());
        }

        return pm;
    }

    // ─── Read for LLM ───

    /**
     * Returns messages for LLM context. Tries ChatMemory cache first,
     * validates consistency via messageCount, falls back to rebuild.
     *
     * @param chatId the chat session ID
     * @return list of Spring AI Messages for LLM consumption
     */
    public List<Message> getMessagesForLlm(String chatId) {
        int persistentCount = persistentRepo.countByChatId(chatId);

        // Try ChatMemory cache
        List<Message> cached = readFromChatMemory(chatId);
        if (cached != null && !cached.isEmpty()) {
            // Consistency check: cached count == persistent count
            if (cached.size() == persistentCount) {
                return cached;
            }
            // Count mismatch → stale cache, rebuild
            log.warn("[ChatMemoryAdapter] Cache inconsistency for chatId={}, cached={}, persistent={}, rebuilding",
                    chatId, cached.size(), persistentCount);
        }

        // Cache miss or inconsistent → rebuild from Truth
        return rebuildFromTruth(chatId);
    }

    // ─── Read for frontend ───

    /**
     * Returns messages for frontend display (always reads from Truth).
     */
    public List<PersistentChatMessage> getMessagesForDisplay(String chatId) {
        return persistentRepo.findByChatId(chatId);
    }

    // ─── Utility ───

    /**
     * Forces a rebuild of ChatMemory from Truth (used after compression).
     */
    public void rebuildChatMemory(String chatId) {
        rebuildFromTruth(chatId);
    }

    /**
     * Returns the count of messages in Truth for a chat.
     */
    public int getMessageCount(String chatId) {
        return persistentRepo.countByChatId(chatId);
    }

    /**
     * Deletes all messages for a chat (used by session deletion).
     */
    public void deleteMessages(String chatId) {
        persistentRepo.deleteByChatId(chatId);
        // ChatMemory cache will naturally expire or be cleared
    }

    // ─── Private helpers ───

    private void syncToChatMemory(String chatId, PersistentChatMessage pm) {
        Message msg = toLlmMessage(pm);
        String agentType = resolveAgentType(chatId);
        chatMemoryManager.getMemory(agentType).add(chatId, List.of(msg));
    }

    private List<Message> readFromChatMemory(String chatId) {
        try {
            String agentType = resolveAgentType(chatId);
            return chatMemoryManager.getMemory(agentType).get(chatId);
        } catch (Exception e) {
            log.debug("[ChatMemoryAdapter] ChatMemory read failed for chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }

    private List<Message> rebuildFromTruth(String chatId) {
        List<Message> rebuilt = persistentRepo.findByChatId(chatId).stream()
                .map(this::toLlmMessage)
                .toList();

        // Write back to ChatMemory cache
        try {
            String agentType = resolveAgentType(chatId);
            chatMemoryManager.getMemory(agentType).add(chatId, rebuilt);
        } catch (Exception e) {
            log.warn("[ChatMemoryAdapter] Failed to rebuild ChatMemory cache for chatId={}: {}",
                    chatId, e.getMessage());
        }

        return rebuilt;
    }

    private Message toLlmMessage(PersistentChatMessage pm) {
        return switch (pm.getRole()) {
            case "user" -> new UserMessage(pm.getContent());
            case "assistant" -> new AssistantMessage(pm.getContent());
            case "system" -> new SystemMessage(pm.getContent());
            default -> new UserMessage(pm.getContent());
        };
    }

    /**
     * Resolves the agent type from chatId. Defaults to "general" if not resolvable.
     * TODO: enhance with actual agent type tracking per chat session.
     */
    private String resolveAgentType(String chatId) {
        // For now, use "general" as default. Future: track agent type per session.
        return "general";
    }
}
