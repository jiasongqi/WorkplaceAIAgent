package com.yupi.yuaiagent.memory.context;

import com.yupi.yuaiagent.memory.MemoryLayer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Dynamic Budget Allocator — adjusts token budgets based on query type and context.
 *
 * <p>Unlike static allocation, this allocator considers:</p>
 * <ul>
 *     <li>Query complexity (simple vs complex)</li>
 *     <li>Query type (factual vs conversational vs analytical)</li>
 *     <li>Historical relevance of each layer</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
public class DynamicBudgetAllocator {

    // Default allocation percentages
    private static final Map<MemoryLayer, Integer> DEFAULT_ALLOCATION = Map.of(
        MemoryLayer.SLIDING_WINDOW, 60,
        MemoryLayer.FACT_STORE, 15,
        MemoryLayer.SUMMARY, 10,
        MemoryLayer.EXPERIENCE, 15
    );

    // Allocation for factual queries (prioritize facts)
    private static final Map<MemoryLayer, Integer> FACTUAL_ALLOCATION = Map.of(
        MemoryLayer.SLIDING_WINDOW, 40,
        MemoryLayer.FACT_STORE, 35,
        MemoryLayer.SUMMARY, 10,
        MemoryLayer.EXPERIENCE, 15
    );

    // Allocation for conversational queries (prioritize recent context)
    private static final Map<MemoryLayer, Integer> CONVERSATIONAL_ALLOCATION = Map.of(
        MemoryLayer.SLIDING_WINDOW, 75,
        MemoryLayer.FACT_STORE, 10,
        MemoryLayer.SUMMARY, 10,
        MemoryLayer.EXPERIENCE, 5
    );

    // Allocation for analytical queries (prioritize experience)
    private static final Map<MemoryLayer, Integer> ANALYTICAL_ALLOCATION = Map.of(
        MemoryLayer.SLIDING_WINDOW, 50,
        MemoryLayer.FACT_STORE, 15,
        MemoryLayer.SUMMARY, 15,
        MemoryLayer.EXPERIENCE, 20
    );

    /**
     * Query type classification.
     */
    public enum QueryType {
        FACTUAL,        // Factual questions (who, what, when, where)
        CONVERSATIONAL, // General chat, follow-ups
        ANALYTICAL      // Analysis, comparison, recommendations
    }

    /**
     * Allocate token budget dynamically based on query type.
     *
     * @param totalBudget total token budget
     * @param queryType   classified query type
     * @return allocation per layer
     */
    public Map<MemoryLayer, Integer> allocate(int totalBudget, QueryType queryType) {
        if (totalBudget <= 0) {
            return emptyAllocation();
        }

        Map<MemoryLayer, Integer> percentages = getPercentages(queryType);
        Map<MemoryLayer, Integer> allocation = new EnumMap<>(MemoryLayer.class);

        for (Map.Entry<MemoryLayer, Integer> entry : percentages.entrySet()) {
            allocation.put(entry.getKey(), totalBudget * entry.getValue() / 100);
        }

        log.debug("[DynamicBudget] queryType={}, allocation={}", queryType, allocation);
        return allocation;
    }

    /**
     * Allocate with default (balanced) strategy.
     *
     * @param totalBudget total token budget
     * @return allocation per layer
     */
    public Map<MemoryLayer, Integer> allocateDefault(int totalBudget) {
        return allocate(totalBudget, QueryType.CONVERSATIONAL);
    }

    /**
     * Classify query type based on keywords.
     *
     * @param query user query
     * @return classified query type
     */
    public QueryType classifyQuery(String query) {
        if (query == null || query.isBlank()) {
            return QueryType.CONVERSATIONAL;
        }

        String lower = query.toLowerCase();

        // Factual keywords
        if (containsAny(lower, "谁", "什么", "哪里", "何时", "为什么", "多少",
                "who", "what", "where", "when", "why", "how many")) {
            return QueryType.FACTUAL;
        }

        // Analytical keywords
        if (containsAny(lower, "分析", "对比", "评估", "推荐", "建议", "比较",
                "analyze", "compare", "evaluate", "recommend", "suggest")) {
            return QueryType.ANALYTICAL;
        }

        return QueryType.CONVERSATIONAL;
    }

    /**
     * Get allocation percentages for query type.
     */
    private Map<MemoryLayer, Integer> getPercentages(QueryType queryType) {
        return switch (queryType) {
            case FACTUAL -> FACTUAL_ALLOCATION;
            case CONVERSATIONAL -> CONVERSATIONAL_ALLOCATION;
            case ANALYTICAL -> ANALYTICAL_ALLOCATION;
        };
    }

    /**
     * Check if text contains any of the keywords.
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create empty allocation.
     */
    private Map<MemoryLayer, Integer> emptyAllocation() {
        Map<MemoryLayer, Integer> empty = new EnumMap<>(MemoryLayer.class);
        for (MemoryLayer layer : MemoryLayer.values()) {
            empty.put(layer, 0);
        }
        return empty;
    }
}
