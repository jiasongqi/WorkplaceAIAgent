package com.yupi.yuaiagent.memory.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Context Relevance Scorer — scores and ranks memory items by relevance to current query.
 *
 * <p>Uses keyword overlap and semantic similarity heuristics to rank memory items,
 * ensuring the most relevant context appears first in the prompt.</p>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
public class ContextRelevanceScorer {

    // Stop words to filter out
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "了", "是", "在", "我", "你", "他", "她", "它",
        "the", "a", "an", "is", "are", "was", "were", "i", "you", "he", "she", "it"
    );

    /**
     * Score a memory item's relevance to the current query.
     *
     * @param query      current user query
     * @param memoryItem memory content to score
     * @return relevance score (0.0 to 1.0)
     */
    public double score(String query, String memoryItem) {
        if (query == null || memoryItem == null || query.isBlank() || memoryItem.isBlank()) {
            return 0.0;
        }

        // Extract keywords from both
        Set<String> queryKeywords = extractKeywords(query);
        Set<String> memoryKeywords = extractKeywords(memoryItem);

        if (queryKeywords.isEmpty() || memoryKeywords.isEmpty()) {
            return 0.0;
        }

        // Calculate keyword overlap (Jaccard similarity)
        Set<String> intersection = new HashSet<>(queryKeywords);
        intersection.retainAll(memoryKeywords);

        Set<String> union = new HashSet<>(queryKeywords);
        union.addAll(memoryKeywords);

        double jaccardScore = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();

        // Calculate keyword density (how many query keywords appear in memory)
        long matchCount = queryKeywords.stream()
                .filter(memoryItem::contains)
                .count();
        double densityScore = (double) matchCount / queryKeywords.size();

        // Weighted combination
        double finalScore = (jaccardScore * 0.4) + (densityScore * 0.6);

        log.debug("[RelevanceScorer] query='{}', memory='{}', jaccard={}, density={}, final={}",
                query.substring(0, Math.min(30, query.length())),
                memoryItem.substring(0, Math.min(30, memoryItem.length())),
                String.format("%.2f", jaccardScore),
                String.format("%.2f", densityScore),
                String.format("%.2f", finalScore));

        return Math.min(1.0, finalScore);
    }

    /**
     * Rank a list of memory items by relevance to the query.
     *
     * @param query       current user query
     * @param memoryItems list of memory content
     * @return sorted list (most relevant first) with scores
     */
    public List<ScoredMemory> rank(String query, List<String> memoryItems) {
        if (memoryItems == null || memoryItems.isEmpty()) {
            return Collections.emptyList();
        }

        return memoryItems.stream()
                .map(item -> new ScoredMemory(item, score(query, item)))
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Filter memory items by minimum relevance threshold.
     *
     * @param query       current user query
     * @param memoryItems list of memory content
     * @param threshold   minimum relevance score (0.0 to 1.0)
     * @return filtered and sorted list
     */
    public List<ScoredMemory> filterByRelevance(String query, List<String> memoryItems, double threshold) {
        return rank(query, memoryItems).stream()
                .filter(item -> item.score() >= threshold)
                .collect(Collectors.toList());
    }

    /**
     * Extract keywords from text (simple tokenization + stop word removal).
     */
    private Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        // Split by whitespace and punctuation
        String[] tokens = text.toLowerCase()
                .split("[\\s\\p{Punct}]+");

        return Arrays.stream(tokens)
                .filter(token -> token.length() > 1) // Skip single chars
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }

    /**
     * Scored memory record.
     */
    public record ScoredMemory(String content, double score) {}
}
