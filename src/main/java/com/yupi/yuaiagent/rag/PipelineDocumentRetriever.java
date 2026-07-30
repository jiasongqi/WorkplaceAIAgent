package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;

/**
 * Spring AI {@link DocumentRetriever} backed by {@link RetrievalPipeline}.
 */
@Slf4j
public class PipelineDocumentRetriever implements DocumentRetriever {

    private final RetrievalPipeline retrievalPipeline;
    private final RetrievalOptions options;

    public PipelineDocumentRetriever(RetrievalPipeline retrievalPipeline, RetrievalOptions options) {
        this.retrievalPipeline = retrievalPipeline;
        this.options = options != null ? options : RetrievalOptions.chatDefaults();
    }

    @Override
    public List<Document> retrieve(Query query) {
        String text = query != null ? query.text() : "";
        RetrievalPipeline.RetrievalResult result = retrievalPipeline.retrieve(text, options);
        log.debug("[PipelineDocumentRetriever] query='{}' hits={}", truncate(text, 40), result.documents().size());
        return result.documents();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
