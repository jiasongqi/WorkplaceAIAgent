package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG Tool — decoupled RAG retrieval as a reusable tool for any Agent.
 *
 * <p>Instead of hardcoding RAG into ResumeAgent, this tool can be registered
 * alongside other tools and called by any agent that needs knowledge retrieval.</p>
 *
 * <p>Features:</p>
 * <ul>
 *     <li>Configurable similarity threshold and topK</li>
 *     <li>Optional status filter (e.g., "求职", "在职")</li>
 *     <li>HyDE support for better retrieval quality</li>
 *     <li>Multi-query expansion via MultiQueryRetriever</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
public class RagTool {

    private final VectorStore vectorStore;
    private final HyDERetriever hydeRetriever;
    private final double defaultSimilarityThreshold;
    private final int defaultTopK;

    public RagTool(VectorStore vectorStore, HyDERetriever hydeRetriever) {
        this(vectorStore, hydeRetriever, 0.5, 5);
    }

    public RagTool(VectorStore vectorStore, HyDERetriever hydeRetriever,
                   double defaultSimilarityThreshold, int defaultTopK) {
        this.vectorStore = vectorStore;
        this.hydeRetriever = hydeRetriever;
        this.defaultSimilarityThreshold = defaultSimilarityThreshold;
        this.defaultTopK = defaultTopK;
    }

    /**
     * Search knowledge base with a query.
     *
     * @param query    search query
     * @param topK     max results (0 = use default)
     * @param filter   status filter (null = no filter)
     * @param useHyDE  whether to use HyDE for retrieval
     * @return formatted search results
     */
    public String search(String query, int topK, String filter, boolean useHyDE) {
        int effectiveTopK = topK > 0 ? topK : defaultTopK;

        try {
            List<Document> results;

            if (useHyDE && hydeRetriever != null) {
                results = hydeRetriever.retrieve(query);
            } else {
                SearchRequest.Builder requestBuilder = SearchRequest.builder()
                        .query(query)
                        .topK(effectiveTopK);

                if (filter != null && !filter.isBlank()) {
                    requestBuilder.filterExpression("status == '" + filter + "'");
                }

                results = vectorStore.similaritySearch(requestBuilder.build());
            }

            if (results.isEmpty()) {
                return "未找到相关文档。请尝试换个关键词或上传相关文档。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(results.size()).append(" 条相关结果：\n\n");
            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                sb.append("[").append(i + 1).append("] ");
                sb.append(doc.getText());
                if (doc.getMetadata() != null && doc.getMetadata().containsKey("filename")) {
                    sb.append(" (来源: ").append(doc.getMetadata().get("filename")).append(")");
                }
                sb.append("\n\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[RagTool] Search failed: {}", e.getMessage());
            return "知识库检索失败：" + e.getMessage();
        }
    }

    /**
     * Register this as a Spring AI ToolCallback.
     */
    public ToolCallback asToolCallback() {
        return FunctionToolCallback.builder(
                "searchKnowledgeBase",
                "Search the knowledge base for relevant career/job documents. " +
                "Use this when the user asks about resume, interview, salary, resignation, or career advice.")
                .description("Search the knowledge base for relevant career/job documents")
                .inputType(RagToolInput.class)
                .apply(input -> search(input.query, input.topK, input.filter, input.useHyDE))
                .build();
    }

    /**
     * Input type for the RAG tool.
     */
    public record RagToolInput(String query, int topK, String filter, boolean useHyDE) {}
}
