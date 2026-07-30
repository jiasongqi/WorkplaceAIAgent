package com.yupi.yuaiagent.agent.loop;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Extracts token usage from Spring AI {@link ChatResponse} (Ch4 budget-aware loop).
 */
public final class ChatUsageExtractor {

    private ChatUsageExtractor() {
    }

    public static int extractTotalTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return 0;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return 0;
        }
        Integer total = usage.getTotalTokens();
        if (total != null && total > 0) {
            return total;
        }
        int prompt = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completion = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        return prompt + completion;
    }

    /** Fallback when provider omits usage metadata. */
    public static int estimateFromText(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 2);
    }
}
