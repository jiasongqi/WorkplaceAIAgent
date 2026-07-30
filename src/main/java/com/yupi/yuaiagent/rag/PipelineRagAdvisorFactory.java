package com.yupi.yuaiagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Component;

/**
 * Factory for RAG advisors backed by {@link RetrievalPipeline}.
 */
@Component
public class PipelineRagAdvisorFactory {

    private final RetrievalPipeline retrievalPipeline;

    public PipelineRagAdvisorFactory(RetrievalPipeline retrievalPipeline) {
        this.retrievalPipeline = retrievalPipeline;
    }

    public Advisor createAdvisor(String statusFilter, double similarityThreshold, int topK) {
        RetrievalOptions options = new RetrievalOptions(
                statusFilter, topK, false, false, similarityThreshold);
        DocumentRetriever retriever = new PipelineDocumentRetriever(retrievalPipeline, options);
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(AiChatContextualQueryAugmenterFactory.createInstance())
                .build();
    }
}
