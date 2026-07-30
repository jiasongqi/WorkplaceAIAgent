package com.yupi.yuaiagent.rag;

import com.yupi.yuaiagent.demo.rag.MultiQueryExpanderDemo;
import com.yupi.yuaiagent.rag.rerank.RerankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified RAG retrieval: rewrite → multi-route recall → rerank → format.
 * Shared by {@link RagTool}, {@link com.yupi.yuaiagent.app.AiChatAgent}, and future advisors.
 */
@Slf4j
@Service
public class RetrievalPipeline {

    private final VectorStore vectorStore;
    private final QueryRewriter queryRewriter;
    private final RerankService rerankService;
    private final MultiQueryExpanderDemo multiQueryExpanderDemo;
    private final HyDERetriever hydeRetriever;

    private final boolean rerankEnabled;
    private final int rerankTopK;

    public RetrievalPipeline(
            @Qualifier("aiChatVectorStore") VectorStore vectorStore,
            QueryRewriter queryRewriter,
            RerankService rerankService,
            MultiQueryExpanderDemo multiQueryExpanderDemo,
            HyDERetriever hydeRetriever,
            @Value("${rag.rerank.enabled:true}") boolean rerankEnabled,
            @Value("${rag.rerank.top-k:5}") int rerankTopK) {
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
        this.rerankService = rerankService;
        this.multiQueryExpanderDemo = multiQueryExpanderDemo;
        this.hydeRetriever = hydeRetriever;
        this.rerankEnabled = rerankEnabled;
        this.rerankTopK = Math.max(1, rerankTopK);
    }

    public RetrievalResult retrieve(String originalQuery, RetrievalOptions options) {
        RetrievalOptions opts = options != null ? options : RetrievalOptions.chatDefaults();
        if (!StringUtils.hasText(originalQuery)) {
            return RetrievalResult.empty(originalQuery == null ? "" : originalQuery);
        }

        String rewritten = queryRewriter.doQueryRewrite(originalQuery);
        List<Document> documents = collectDocuments(rewritten, opts);
        documents = applyRerank(rewritten, documents, opts.topK());

        log.info("[RetrievalPipeline] queryLen={} rewrittenLen={} docs={} multiQuery={} rerank={}",
                originalQuery.length(), rewritten.length(), documents.size(),
                opts.multiQuery(), rerankEnabled);

        return new RetrievalResult(originalQuery, rewritten, documents,
                formatDocuments(documents), !documents.isEmpty());
    }

    private List<Document> collectDocuments(String rewrittenQuery, RetrievalOptions opts) {
        if (opts.useHyDE() && hydeRetriever != null) {
            return new ArrayList<>(hydeRetriever.retrieve(rewrittenQuery));
        }
        if (opts.multiQuery()) {
            MultiQueryRetriever retriever = new MultiQueryRetriever(vectorStore, queryRewriter, opts.topK());
            List<Query> expanded = multiQueryExpanderDemo.expand(rewrittenQuery);
            return retriever.retrieve(rewrittenQuery, expanded);
        }
        return directSearch(rewrittenQuery, opts);
    }

    private List<Document> directSearch(String query, RetrievalOptions opts) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(1, opts.topK()))
                    .similarityThreshold(opts.similarityThreshold());

            if (StringUtils.hasText(opts.statusFilter())) {
                builder.filterExpression("status == '" + opts.statusFilter() + "'");
            }

            List<Document> results = vectorStore.similaritySearch(builder.build());
            return results != null ? results : Collections.emptyList();
        } catch (Exception e) {
            log.warn("[RetrievalPipeline] direct search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Document> applyRerank(String query, List<Document> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        if (!rerankEnabled) {
            return trimTopK(documents, topK);
        }
        int effectiveTopK = Math.min(Math.max(1, topK), rerankTopK);
        return rerankService.rerankTopK(query, documents, effectiveTopK);
    }

    private static List<Document> trimTopK(List<Document> documents, int topK) {
        if (documents.size() <= topK) {
            return documents;
        }
        return new ArrayList<>(documents.subList(0, topK));
    }

    public static String formatDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(documents.size()).append(" 条相关结果：\n\n");
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append(doc.getText());
            if (doc.getMetadata() != null && doc.getMetadata().containsKey("filename")) {
                sb.append(" (来源: ").append(doc.getMetadata().get("filename")).append(")");
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    public static String buildContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }
        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    public record RetrievalResult(
            String originalQuery,
            String rewrittenQuery,
            List<Document> documents,
            String formattedResults,
            boolean hasHits
    ) {
        public static RetrievalResult empty(String originalQuery) {
            return new RetrievalResult(originalQuery, originalQuery,
                    List.of(), "", false);
        }

        public boolean hasContext() {
            return hasHits && formattedResults != null && !formattedResults.isBlank();
        }

        public String buildPrompt(String userQuestion) {
            String question = StringUtils.hasText(rewrittenQuery) ? rewrittenQuery : userQuestion;
            String context = buildContext(documents);
            if (!StringUtils.hasText(context)) {
                return question;
            }
            return "请基于以下参考资料回答用户问题：\n\n" + context + "\n\n用户问题：" + question;
        }
    }
}
