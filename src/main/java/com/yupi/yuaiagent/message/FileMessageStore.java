package com.yupi.yuaiagent.message;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File-backed message store ({@code app.storage.type=file}, default for demo).
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "app.storage.type", havingValue = "file", matchIfMissing = true)
public class FileMessageStore implements MessageStore {

    @Value("${session.storage.dir:./tmp/sessions}")
    private String storageDir;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Map<String, List<PersistentChatMessage>> chatIndex = new ConcurrentHashMap<>();
    private final Map<String, PersistentChatMessage> messageIdIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> chatLocks = new ConcurrentHashMap<>();
    private File messagesDir;

    @PostConstruct
    public void init() {
        messagesDir = new File(storageDir, "messages");
        if (!messagesDir.exists()) {
            messagesDir.mkdirs();
        }
        loadAllFromFiles();
        log.info("[message:file] initialized, storage={}, chats={}",
                messagesDir.getAbsolutePath(), chatIndex.size());
    }

    @Override
    public PersistentChatMessage save(String chatId, String role, String content,
                                      MessageSource sourceType, String sourceId, String sourceName) {
        ReentrantLock lock = lockFor(chatId);
        lock.lock();
        try {
            PersistentChatMessage msg = newMessage(chatId, role, content, sourceType, sourceId, sourceName);
            msg.setStatus(MessageStatus.COMPLETE);
            indexAdd(msg);
            saveToFile(chatId);
            return msg;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PersistentChatMessage startStreaming(String chatId, String role,
                                                MessageSource sourceType, String sourceId, String sourceName) {
        ReentrantLock lock = lockFor(chatId);
        lock.lock();
        try {
            PersistentChatMessage msg = newMessage(chatId, role, "", sourceType, sourceId, sourceName);
            msg.setStatus(MessageStatus.STREAMING);
            msg.setPartialContent("");
            indexAdd(msg);
            saveToFile(chatId);
            return msg;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updatePartial(String messageId, String partialContent) {
        PersistentChatMessage msg = messageIdIndex.get(messageId);
        if (msg == null) {
            return;
        }
        ReentrantLock lock = lockFor(msg.getChatId());
        lock.lock();
        try {
            msg.setPartialContent(partialContent);
            if (msg.getStatus() != MessageStatus.COMPLETE) {
                msg.setStatus(MessageStatus.STREAMING);
            }
            saveToFile(msg.getChatId());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void complete(String messageId, String fullContent) {
        PersistentChatMessage msg = messageIdIndex.get(messageId);
        if (msg == null) {
            return;
        }
        ReentrantLock lock = lockFor(msg.getChatId());
        lock.lock();
        try {
            msg.setContent(fullContent != null ? fullContent : "");
            msg.setPartialContent(null);
            msg.setStatus(MessageStatus.COMPLETE);
            saveToFile(msg.getChatId());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void markPartial(String messageId) {
        PersistentChatMessage msg = messageIdIndex.get(messageId);
        if (msg == null) {
            return;
        }
        ReentrantLock lock = lockFor(msg.getChatId());
        lock.lock();
        try {
            if (msg.getStatus() == MessageStatus.COMPLETE) {
                return;
            }
            if (msg.getPartialContent() != null && msg.getContent() != null
                    && msg.getContent().isEmpty() && !msg.getPartialContent().isEmpty()) {
                msg.setContent(msg.getPartialContent());
            }
            msg.setStatus(MessageStatus.PARTIAL);
            saveToFile(msg.getChatId());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<PersistentChatMessage> findByChatId(String chatId) {
        return chatIndex.getOrDefault(chatId, List.of());
    }

    @Override
    public PersistentChatMessage findByMessageId(String messageId) {
        return messageIdIndex.get(messageId);
    }

    @Override
    public int countByChatId(String chatId) {
        List<PersistentChatMessage> msgs = chatIndex.get(chatId);
        return msgs != null ? msgs.size() : 0;
    }

    @Override
    public void deleteByChatId(String chatId) {
        ReentrantLock lock = lockFor(chatId);
        lock.lock();
        try {
            List<PersistentChatMessage> removed = chatIndex.remove(chatId);
            if (removed != null) {
                removed.forEach(m -> messageIdIndex.remove(m.getMessageId()));
            }
            File file = getFileForChat(chatId);
            if (file.exists()) {
                file.delete();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void replaceWithSummary(String chatId, String summary, int keepRecent) {
        ReentrantLock lock = lockFor(chatId);
        lock.lock();
        try {
            List<PersistentChatMessage> messages = chatIndex.get(chatId);
            if (messages == null || messages.isEmpty()) {
                return;
            }
            synchronized (messages) {
                int from = Math.max(0, messages.size() - keepRecent);
                List<PersistentChatMessage> recent = new ArrayList<>(messages.subList(from, messages.size()));
                messages.forEach(m -> messageIdIndex.remove(m.getMessageId()));

                List<PersistentChatMessage> compressed = Collections.synchronizedList(new ArrayList<>());
                PersistentChatMessage summaryMsg = newMessage(chatId, "system",
                        "[记忆压缩摘要] " + summary, MessageSource.SYSTEM, null, null);
                summaryMsg.setStatus(MessageStatus.COMPLETE);
                compressed.add(summaryMsg);
                compressed.addAll(recent);
                chatIndex.put(chatId, compressed);
                compressed.forEach(m -> messageIdIndex.put(m.getMessageId(), m));
            }
            saveToFile(chatId);
        } finally {
            lock.unlock();
        }
    }

    private PersistentChatMessage newMessage(String chatId, String role, String content,
                                             MessageSource sourceType, String sourceId, String sourceName) {
        PersistentChatMessage msg = new PersistentChatMessage();
        msg.setMessageId(IdUtil.fastSimpleUUID());
        msg.setChatId(chatId);
        msg.setRole(role);
        msg.setContent(content != null ? content : "");
        msg.setTimestamp(System.currentTimeMillis());
        msg.setSourceType(sourceType);
        msg.setSourceId(sourceId);
        msg.setSourceName(sourceName);
        return msg;
    }

    private void indexAdd(PersistentChatMessage msg) {
        chatIndex.computeIfAbsent(msg.getChatId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(msg);
        messageIdIndex.put(msg.getMessageId(), msg);
    }

    private ReentrantLock lockFor(String chatId) {
        return chatLocks.computeIfAbsent(chatId, k -> new ReentrantLock());
    }

    private void saveToFile(String chatId) {
        File file = getFileForChat(chatId);
        try {
            objectMapper.writeValue(file, chatIndex.get(chatId));
        } catch (IOException e) {
            log.error("[message:file] save failed chatId={}", chatId, e);
        }
    }

    private void loadAllFromFiles() {
        if (!messagesDir.exists()) {
            return;
        }
        File[] files = messagesDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String chatId = file.getName().replace(".json", "");
            try {
                List<PersistentChatMessage> messages = objectMapper.readValue(file, new TypeReference<>() {});
                List<PersistentChatMessage> syncList = Collections.synchronizedList(new ArrayList<>(messages));
                for (PersistentChatMessage m : syncList) {
                    if (m.getStatus() == null) {
                        m.setStatus(MessageStatus.COMPLETE);
                    }
                    messageIdIndex.put(m.getMessageId(), m);
                }
                chatIndex.put(chatId, syncList);
            } catch (IOException e) {
                log.error("[message:file] load failed {}", file.getName(), e);
            }
        }
    }

    private File getFileForChat(String chatId) {
        return new File(messagesDir, chatId + ".json");
    }
}
