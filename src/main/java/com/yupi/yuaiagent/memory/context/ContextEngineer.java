package com.yupi.yuaiagent.memory.context;

import com.yupi.yuaiagent.memory.MemoryLayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Context Engineer — orchestrates context assembly with relevance scoring and dynamic budgeting.
 *
 * <p>This service enhances the existing MemoryCoordinator by adding:</p>
 * <ul>
 *     <li>Query analysis and key information extraction</li>
 *     <li>Dynamic budget allocation based on query type</li>
 *     <li>Relevance-based ranking of memory items</li>
 *     <li>Intelligent context assembly</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Analyze query
 * QueryAnalysis analysis = contextEngineer.analyzeQuery("分析我的职业发展路径");
 *
 * // Get optimized context
 * String context = contextEngineer.assembleContext(userId, query, memoryItems);
 * }</pre>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextEngineer {

    private final KeyInfoExtractor keyInfoExtractor;
    private final DynamicBudgetAllocator budgetAllocator;
    private final ContextRelevanceScorer relevanceScorer;

    /**
     * Analyze a user query to extract key information.
     *
     * @param query user query
     * @return query analysis result
     */
    public QueryAnalysis analyzeQuery(String query) {
        // 1. Extract key information
        KeyInfoExtractor.KeyInfo keyInfo = keyInfoExtractor.extract(query);

        // 2. Classify query type
        DynamicBudgetAllocator.QueryType queryType = budgetAllocator.classifyQuery(query);

        // 3. Build analysis result
        QueryAnalysis analysis = new QueryAnalysis(
            query,
            keyInfo,
            queryType,
            System.currentTimeMillis()
        );

        log.info("[ContextEngineer] Query analysis: query='{}', type={}, entities={}, topics={}",
                query.substring(0, Math.min(50, query.length())),
                queryType,
                keyInfo.entities(),
                keyInfo.topics());

        return analysis;
    }

    /**
     * Get dynamic budget allocation for a query.
     *
     * @param totalBudget total token budget
     * @param query       user query
     * @return allocation per layer
     */
    public Map<MemoryLayer, Integer> getBudgetAllocation(int totalBudget, String query) {
        DynamicBudgetAllocator.QueryType queryType = budgetAllocator.classifyQuery(query);
        return budgetAllocator.allocate(totalBudget, queryType);
    }

    /**
     * Rank memory items by relevance to query.
     *
     * @param query       user query
     * @param memoryItems list of memory content
     * @return ranked list with scores
     */
    public List<ContextRelevanceScorer.ScoredMemory> rankByRelevance(
            String query, List<String> memoryItems) {
        return relevanceScorer.rank(query, memoryItems);
    }

    /**
     * Filter memory items by relevance threshold.
     *
     * @param query       user query
     * @param memoryItems list of memory content
     * @param threshold   minimum relevance score (0.0 to 1.0)
     * @return filtered and sorted list
     */
    public List<ContextRelevanceScorer.ScoredMemory> filterByRelevance(
            String query, List<String> memoryItems, double threshold) {
        return relevanceScorer.filterByRelevance(query, memoryItems, threshold);
    }

    /**
     * Assemble optimized context from memory items.
     *
     * @param query       user query
     * @param memoryItems memory items to assemble
     * @param maxTokens   maximum token budget
     * @return assembled context string
     */
    public String assembleContext(String query, List<String> memoryItems, int maxTokens) {
        if (memoryItems == null || memoryItems.isEmpty()) {
            return "";
        }

        // 1. Rank by relevance
        List<ContextRelevanceScorer.ScoredMemory> ranked = relevanceScorer.rank(query, memoryItems);

        // 2. Assemble within token budget
        StringBuilder context = new StringBuilder();
        int currentTokens = 0;

        for (ContextRelevanceScorer.ScoredMemory item : ranked) {
            int itemTokens = estimateTokens(item.content());
            if (currentTokens + itemTokens > maxTokens) {
                break; // Budget exceeded
            }
            context.append(item.content()).append("\n\n");
            currentTokens += itemTokens;
        }

        log.debug("[ContextEngineer] Assembled context: items={}, tokens={}/{}",
                ranked.size(), currentTokens, maxTokens);

        return context.toString().trim();
    }

    /**
     * Estimate token count (simple heuristic).
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Rough estimate: 1 token per 2 Chinese chars, 1 token per 4 English chars
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (chineseChars + 1) / 2 + (otherChars + 3) / 4;
    }

    /**
     * Query analysis record.
     */
    public record QueryAnalysis(
        String query,
        KeyInfoExtractor.KeyInfo keyInfo,
        DynamicBudgetAllocator.QueryType queryType,
        long timestamp
    ) {}
}
