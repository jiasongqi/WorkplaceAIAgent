package com.yupi.yuaiagent.rag.rerank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rerank Service — reorders retrieved documents by relevance to the query.
 *
 * <p>Implements a lightweight reranking strategy using:</p>
 * <ul>
 *     <li>Keyword overlap scoring</li>
 *     <li>Document length normalization</li>
 *     <li>Position bias (original retrieval order)</li>
 * </ul>
 *
 * <p>For production use, consider integrating a dedicated rerank model
 * (e.g., Cohere Rerank, BGE Reranker) via API.</p>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
public class RerankService {

    // Stop words to filter out
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "了", "是", "在", "我", "你", "他", "她", "它",
        "the", "a", "an", "is", "are", "was", "were", "i", "you", "he", "she", "it"
    );

    /**
     * Rerank documents by relevance to the query.
     *
     * @param query     search query
     * @param documents retrieved documents
     * @return reranked documents (most relevant first)
     */
    public List<Document> rerank(String query, List<Document> documents) {
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return documents != null ? documents : Collections.emptyList();
        }

        log.debug("[RerankService] Reranking {} documents for query: {}",
                documents.size(), query.substring(0, Math.min(50, query.length())));

        // Extract query keywords
        Set<String> queryKeywords = extractKeywords(query);

        // Score each document
        List<ScoredDocument> scoredDocs = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            double score = calculateScore(queryKeywords, doc, i, documents.size());
            scoredDocs.add(new ScoredDocument(doc, score, i));
        }

        // Sort by score (descending)
        scoredDocs.sort(Comparator.comparingDouble(ScoredDocument::score).reversed());

        // Extract reranked documents
        List<Document> reranked = scoredDocs.stream()
                .map(ScoredDocument::document)
                .collect(Collectors.toList());

        log.debug("[RerankService] Reranking complete. Top document changed from position {} to {}",
                scoredDocs.get(0).originalIndex(), 0);

        return reranked;
    }

    /**
     * Rerank with top-K filtering.
     *
     * @param query     search query
     * @param documents retrieved documents
     * @param topK      number of top documents to return
     * @return top-K reranked documents
     */
    public List<Document> rerankTopK(String query, List<Document> documents, int topK) {
        List<Document> reranked = rerank(query, documents);
        return reranked.size() > topK ? reranked.subList(0, topK) : reranked;
    }

    /**
     * Calculate relevance score for a document.
     */
    private double calculateScore(Set<String> queryKeywords, Document doc, int position, int totalDocs) {
        // 1. Keyword overlap score (0-1)
        Set<String> docKeywords = extractKeywords(doc.getText());
        double keywordScore = calculateKeywordOverlap(queryKeywords, docKeywords);

        // 2. Document quality score (0-1)
        double qualityScore = calculateQualityScore(doc);

        // 3. Position bias (0-1) — original retrieval order
        double positionScore = 1.0 - ((double) position / totalDocs);

        // Weighted combination
        double finalScore = (keywordScore * 0.6) + (qualityScore * 0.2) + (positionScore * 0.2);

        return finalScore;
    }

    /**
     * Calculate keyword overlap between query and document.
     */
    private double calculateKeywordOverlap(Set<String> queryKeywords, Set<String> docKeywords) {
        if (queryKeywords.isEmpty() || docKeywords.isEmpty()) {
            return 0.0;
        }

        // Count overlapping keywords
        long overlap = queryKeywords.stream()
                .filter(docKeywords::contains)
                .count();

        // Jaccard similarity
        Set<String> union = new HashSet<>(queryKeywords);
        union.addAll(docKeywords);
        double jaccard = (double) overlap / union.size();

        // Coverage (how many query keywords are in the document)
        double coverage = (double) overlap / queryKeywords.size();

        // Combined score
        return (jaccard * 0.4) + (coverage * 0.6);
    }

    /**
     * Calculate document quality score.
     */
    private double calculateQualityScore(Document doc) {
        String text = doc.getText();
        if (text == null || text.isBlank()) {
            return 0.0;
        }

        // Length score — prefer moderate length documents
        int length = text.length();
        double lengthScore;
        if (length < 50) {
            lengthScore = 0.3; // Too short
        } else if (length < 200) {
            lengthScore = 0.7; // Short but okay
        } else if (length < 1000) {
            lengthScore = 1.0; // Ideal length
        } else if (length < 2000) {
            lengthScore = 0.8; // Long but okay
        } else {
            lengthScore = 0.5; // Too long
        }

        // Structure score — prefer documents with structure
        double structureScore = 0.5;
        if (text.contains("\n") || text.contains("。") || text.contains(".")) {
            structureScore = 0.8; // Has structure
        }
        if (text.contains("：") || text.contains(":") || text.contains("-")) {
            structureScore = 1.0; // Well-structured
        }

        return (lengthScore * 0.6) + (structureScore * 0.4);
    }

    /**
     * Extract keywords from text.
     */
    private Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        String[] tokens = text.toLowerCase()
                .split("[\\s\\p{Punct}]+");

        return Arrays.stream(tokens)
                .filter(token -> token.length() > 1)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }

    /**
     * Scored document record.
     */
    private record ScoredDocument(Document document, double score, int originalIndex) {}
}
