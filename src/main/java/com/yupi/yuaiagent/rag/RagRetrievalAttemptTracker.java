package com.yupi.yuaiagent.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-chat empty-retrieval counter — prevents retrieval loops (Ch5 max-retry gotcha).
 */
@Component
public class RagRetrievalAttemptTracker {

    private final int maxEmptyRetries;
    private final ConcurrentHashMap<String, AtomicInteger> emptyAttempts = new ConcurrentHashMap<>();

    public RagRetrievalAttemptTracker(
            @Value("${rag.pipeline.max-empty-retries:2}") int maxEmptyRetries) {
        this.maxEmptyRetries = Math.max(1, maxEmptyRetries);
    }

    public boolean shouldBlock(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return false;
        }
        AtomicInteger count = emptyAttempts.get(chatId);
        return count != null && count.get() >= maxEmptyRetries;
    }

    public void recordEmpty(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return;
        }
        emptyAttempts.computeIfAbsent(chatId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void recordSuccess(String chatId) {
        if (chatId != null && !chatId.isBlank()) {
            emptyAttempts.remove(chatId);
        }
    }

    public int maxEmptyRetries() {
        return maxEmptyRetries;
    }
}
