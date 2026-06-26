package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * HyDE (Hypothetical Document Embedding) Retriever.
 *
 * <p>Instead of searching with the user's question directly, HyDE first asks the LLM
 * to generate a hypothetical answer document, then uses that answer for embedding search.
 * This often retrieves more relevant results because the hypothetical answer shares
 * more semantic similarity with actual documents than the question does.</p>
 *
 * <p>Pipeline: Question → LLM generates hypothetical answer → Embed answer → Vector search</p>
 *
 * @author jsq
 */
@Slf4j
public class HyDERetriever {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final int topK;

    private static final String HYDE_PROMPT = """
            请根据以下问题，写一段可能包含答案的文档内容。
            不需要回答问题，只需要写一段可能存在于知识库中的相关文档。
            要求：用中文撰写，100-200字，包含关键术语和概念。
            
            问题：{question}
            
            相关文档：
            """;

    public HyDERetriever(ChatModel chatModel, VectorStore vectorStore) {
        this(chatModel, vectorStore, 3);
    }

    public HyDERetriever(ChatModel chatModel, VectorStore vectorStore, int topK) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.vectorStore = vectorStore;
        this.topK = topK;
    }

    /**
     * Execute HyDE retrieval.
     *
     * @param question the user's question
     * @return list of relevant documents
     */
    public List<Document> retrieve(String question) {
        try {
            // Step 1: Generate hypothetical document
            String hypothetical = chatClient.prompt()
                    .user(HYDE_PROMPT.replace("{question}", question))
                    .call()
                    .content();
            log.debug("[HyDE] Generated hypothetical document: {} chars", hypothetical.length());

            // Step 2: Search with hypothetical document as query
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(hypothetical)
                            .topK(topK)
                            .build());

            log.info("[HyDE] Retrieved {} documents using hypothetical embedding", results.size());
            return results;
        } catch (Exception e) {
            log.warn("[HyDE] HyDE retrieval failed, falling back to direct search: {}", e.getMessage());
            // Fallback: direct vector search
            return vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(topK)
                            .build());
        }
    }

    /**
     * Build context string from documents.
     */
    public String buildContext(List<Document> documents) {
        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }
}
