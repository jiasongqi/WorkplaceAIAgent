package com.yupi.yuaiagent.message;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent message repository — the Source of Truth for all chat messages.
 * <p>
 * Maintains two indexes:
 * <ul>
 *   <li>{@code chatIndex}: chatId → List of messages (ordered by insertion)</li>
 *   <li>{@code messageIdIndex}: messageId → message (O(1) lookup for favorites, search hits)</li>
 * </ul>
 * <p>
 * Storage: one JSON file per chat session under {@code {session.storage.dir}/messages/}.
 *
 * @author jsq
 */
@Slf4j
@Repository
public class PersistentMessageRepository {

    @Value("${session.storage.dir:./tmp/sessions}")
    private String storageDir;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** Primary index: chatId → ordered message list. */
    private final Map<String, List<PersistentChatMessage>> chatIndex = new ConcurrentHashMap<>();

    /** Secondary index: messageId → message (O(1) lookup). */
    private final Map<String, PersistentChatMessage> messageIdIndex = new ConcurrentHashMap<>();

    private File messagesDir;

    @PostConstruct
    public void init() {
        messagesDir = new File(storageDir, "messages");
        if (!messagesDir.exists()) {
            messagesDir.mkdirs();
        }
        loadAllFromFiles();
        log.info("[message] repository initialized, storage: {}, chats: {}",
                messagesDir.getAbsolutePath(), chatIndex.size());
    }

    // ─── Write ───

    /**
     * Persists a new message. This is the ONLY write entry point.
     *
     * @param chatId  the chat session ID
     * @param role    "user", "assistant", or "system"
     * @param content the message content
     * @return the persisted message with generated messageId and timestamp
     */
    public PersistentChatMessage save(String chatId, String role, String content) {
        return save(chatId, role, content, null, null, null);
    }

    /**
     * Persists a new message with multi-agent source tracking.
     *
     * @param chatId    the chat session ID
     * @param role      "user", "assistant", or "system"
     * @param content   the message content
     * @param sourceType who produced this message (nullable — defaults to role-based inference)
     * @param sourceId   source identifier (nullable)
     * @param sourceName source display name (nullable)
     * @return the persisted message with generated messageId and timestamp
     */
    public PersistentChatMessage save(String chatId, String role, String content,
                                      MessageSource sourceType, String sourceId, String sourceName) {
        PersistentChatMessage msg = new PersistentChatMessage();
        msg.setMessageId(IdUtil.fastSimpleUUID());
        msg.setChatId(chatId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setSourceType(sourceType);
        msg.setSourceId(sourceId);
        msg.setSourceName(sourceName);

        chatIndex.computeIfAbsent(chatId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(msg);
        messageIdIndex.put(msg.getMessageId(), msg);
        saveToFile(chatId);

        log.debug("[message] saved chatId={}, role={}, messageId={}", chatId, role, msg.getMessageId());
        return msg;
    }

    // ─── Read ───

    /**
     * Returns all messages for a chat, ordered by insertion (chronological).
     */
    public List<PersistentChatMessage> findByChatId(String chatId) {
        return chatIndex.getOrDefault(chatId, List.of());
    }

    /**
     * Returns a single message by its stable messageId. O(1) via secondary index.
     */
    public PersistentChatMessage findByMessageId(String messageId) {
        return messageIdIndex.get(messageId);
    }

    /**
     * Returns the number of messages in a chat (for cache consistency checks).
     */
    public int countByChatId(String chatId) {
        List<PersistentChatMessage> msgs = chatIndex.get(chatId);
        return msgs != null ? msgs.size() : 0;
    }

    // ─── Delete ───

    /**
     * Deletes all messages for a chat (used by session deletion).
     */
    public void deleteByChatId(String chatId) {
        List<PersistentChatMessage> removed = chatIndex.remove(chatId);
        if (removed != null) {
            removed.forEach(m -> messageIdIndex.remove(m.getMessageId()));
        }
        deleteFile(chatId);
        log.info("[message] deleted chatId={}, removed {} messages", chatId,
                removed != null ? removed.size() : 0);
    }

    // ─── Compression support ───

    /**
     * Replaces all messages with a summary + recent N messages.
     * Used by ChatMemoryManager when compression is triggered.
     *
     * @param chatId     the chat session ID
     * @param summary    the compressed summary content
     * @param keepRecent number of recent messages to keep
     */
    public void replaceWithSummary(String chatId, String summary, int keepRecent) {
        List<PersistentChatMessage> messages = chatIndex.get(chatId);
        if (messages == null || messages.isEmpty()) return;

        synchronized (messages) {
            int from = Math.max(0, messages.size() - keepRecent);
            List<PersistentChatMessage> recent = new ArrayList<>(messages.subList(from, messages.size()));

            // Remove old messages from messageIdIndex
            messages.forEach(m -> messageIdIndex.remove(m.getMessageId()));

            // Build new list: summary + recent
            List<PersistentChatMessage> compressed = Collections.synchronizedList(new ArrayList<>());
            PersistentChatMessage summaryMsg = new PersistentChatMessage();
            summaryMsg.setMessageId(IdUtil.fastSimpleUUID());
            summaryMsg.setChatId(chatId);
            summaryMsg.setRole("system");
            summaryMsg.setContent("[记忆压缩摘要] " + summary);
            summaryMsg.setTimestamp(System.currentTimeMillis());
            compressed.add(summaryMsg);
            compressed.addAll(recent);

            // Update indexes
            chatIndex.put(chatId, compressed);
            compressed.forEach(m -> messageIdIndex.put(m.getMessageId(), m));
        }

        saveToFile(chatId);
        log.info("[message] compressed chatId={}, summary + {} recent messages", chatId, keepRecent);
    }

    // ─── File I/O ───

    private void saveToFile(String chatId) {
        File file = getFileForChat(chatId);
        try {
            objectMapper.writeValue(file, chatIndex.get(chatId));
        } catch (IOException e) {
            log.error("[message] failed to save chatId={}", chatId, e);
        }
    }

    private void loadAllFromFiles() {
        if (!messagesDir.exists()) return;
        File[] files = messagesDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            String chatId = file.getName().replace(".json", "");
            try {
                List<PersistentChatMessage> messages = objectMapper.readValue(file,
                        new TypeReference<List<PersistentChatMessage>>() {});
                List<PersistentChatMessage> syncList = Collections.synchronizedList(new ArrayList<>(messages));
                chatIndex.put(chatId, syncList);
                messages.forEach(m -> messageIdIndex.put(m.getMessageId(), m));
            } catch (IOException e) {
                log.error("[message] failed to load file: {}", file.getName(), e);
            }
        }
        log.info("[message] loaded {} chats from files", chatIndex.size());
    }

    private File getFileForChat(String chatId) {
        return new File(messagesDir, chatId + ".json");
    }

    private void deleteFile(String chatId) {
        File file = getFileForChat(chatId);
        if (file.exists()) {
            file.delete();
        }
    }
}
