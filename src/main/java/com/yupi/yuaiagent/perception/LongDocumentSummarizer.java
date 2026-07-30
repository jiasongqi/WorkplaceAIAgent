package com.yupi.yuaiagent.perception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Map-Reduce summarization for long extracted text (Ch5 Q1 context budget).
 */
@Slf4j
@Service
public class LongDocumentSummarizer {

    private static final String MAP_PROMPT = """
            以下是长文档的一个片段。请用 150 字以内中文摘要该片段要点（保留数字、专有名词）：
            
            %s
            """;

    private static final String REDUCE_PROMPT = """
            以下是同一长文档各片段的摘要。请合并为一份 400 字以内的总摘要，保留关键事实：
            
            %s
            """;

    private final ChatClient chatClient;
    private final int charThreshold;
    private final int chunkSize;

    public LongDocumentSummarizer(@Qualifier("dashscopeChatModel") ChatModel chatModel,
                                  @Value("${perception.map-reduce.char-threshold:12000}") int charThreshold,
                                  @Value("${perception.map-reduce.chunk-size:4000}") int chunkSize) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.charThreshold = Math.max(2000, charThreshold);
        this.chunkSize = Math.max(1000, chunkSize);
    }

    public String summarizeIfNeeded(String text) {
        if (!StringUtils.hasText(text) || text.length() <= charThreshold) {
            return text;
        }
        log.info("[MapReduce] textLen={} threshold={} — running map-reduce", text.length(), charThreshold);
        List<String> chunks = splitChunks(text, chunkSize);
        List<String> partials = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            try {
                String part = chatClient.prompt()
                        .user(MAP_PROMPT.formatted(chunks.get(i)))
                        .call()
                        .content();
                if (StringUtils.hasText(part)) {
                    partials.add(part.trim());
                }
            } catch (Exception e) {
                log.warn("[MapReduce] map step {} failed: {}", i, e.getMessage());
                partials.add(chunks.get(i).substring(0, Math.min(500, chunks.get(i).length())) + "…");
            }
        }
        if (partials.isEmpty()) {
            return text.substring(0, Math.min(charThreshold, text.length())) + "…";
        }
        if (partials.size() == 1) {
            return partials.get(0);
        }
        try {
            String merged = String.join("\n\n", partials);
            return chatClient.prompt()
                    .user(REDUCE_PROMPT.formatted(merged))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[MapReduce] reduce failed: {}", e.getMessage());
            return String.join("\n", partials);
        }
    }

    private static List<String> splitChunks(String text, int size) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return chunks;
    }
}
