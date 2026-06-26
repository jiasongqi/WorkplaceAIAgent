package com.yupi.yuaiagent.memory.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Key Information Extractor — extracts key entities, topics, and intents from user queries.
 *
 * <p>Extracted information is used to:</p>
 * <ul>
 *     <li>Improve memory retrieval relevance</li>
 *     <li>Guide context assembly priorities</li>
 *     <li>Provide structured input for downstream components</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
public class KeyInfoExtractor {

    // Entity patterns (simple regex-based extraction)
    private static final Pattern ENTITY_PATTERN = Pattern.compile(
        "[\\u4e00-\\u9fa5]{2,4}(?:公司|大学|学校|团队|项目|产品|技术|技能|行业)"
    );

    // Topic keywords
    private static final Set<String> TOPIC_KEYWORDS = Set.of(
        "职业", "规划", "发展", "技能", "学习", "工作", "面试", "简历",
        "career", "plan", "skill", "learn", "work", "interview", "resume"
    );

    /**
     * Extract key information from query.
     *
     * @param query user query
     * @return extracted key information
     */
    public KeyInfo extract(String query) {
        if (query == null || query.isBlank()) {
            return KeyInfo.empty();
        }

        Set<String> entities = extractEntities(query);
        Set<String> topics = extractTopics(query);
        String intent = extractIntent(query);
        Set<String> keywords = extractKeywords(query);

        log.debug("[KeyInfoExtractor] query='{}', entities={}, topics={}, intent={}",
                query.substring(0, Math.min(30, query.length())),
                entities, topics, intent);

        return new KeyInfo(entities, topics, intent, keywords);
    }

    /**
     * Extract entities (named entities, domain terms).
     */
    private Set<String> extractEntities(String query) {
        Set<String> entities = new HashSet<>();

        // Pattern-based extraction
        Matcher matcher = ENTITY_PATTERN.matcher(query);
        while (matcher.find()) {
            entities.add(matcher.group());
        }

        return entities;
    }

    /**
     * Extract topics (domain-specific keywords).
     */
    private Set<String> extractTopics(String query) {
        String lower = query.toLowerCase();
        return TOPIC_KEYWORDS.stream()
                .filter(lower::contains)
                .collect(Collectors.toSet());
    }

    /**
     * Extract intent (question type, action type).
     */
    private String extractIntent(String query) {
        String lower = query.toLowerCase();

        // Question intents
        if (containsAny(lower, "谁", "who")) return "PERSON_QUERY";
        if (containsAny(lower, "什么", "what")) return "FACTUAL_QUERY";
        if (containsAny(lower, "哪里", "where")) return "LOCATION_QUERY";
        if (containsAny(lower, "何时", "when")) return "TIME_QUERY";
        if (containsAny(lower, "为什么", "why")) return "REASON_QUERY";
        if (containsAny(lower, "怎么", "如何", "how")) return "METHOD_QUERY";
        if (containsAny(lower, "多少", "how many", "how much")) return "QUANTITY_QUERY";

        // Action intents
        if (containsAny(lower, "分析", "analyze")) return "ANALYSIS";
        if (containsAny(lower, "推荐", "建议", "recommend", "suggest")) return "RECOMMENDATION";
        if (containsAny(lower, "比较", "对比", "compare")) return "COMPARISON";
        if (containsAny(lower, "总结", "summarize")) return "SUMMARIZATION";

        return "GENERAL";
    }

    /**
     * Extract general keywords (nouns, verbs).
     */
    private Set<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptySet();
        }

        // Simple tokenization
        String[] tokens = query.toLowerCase()
                .split("[\\s\\p{Punct}]+");

        return Arrays.stream(tokens)
                .filter(token -> token.length() > 1)
                .collect(Collectors.toSet());
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
     * Key information record.
     */
    public record KeyInfo(
        Set<String> entities,
        Set<String> topics,
        String intent,
        Set<String> keywords
    ) {
        public static KeyInfo empty() {
            return new KeyInfo(Collections.emptySet(), Collections.emptySet(), "GENERAL", Collections.emptySet());
        }

        /**
         * Check if query is about a specific entity.
         */
        public boolean hasEntities() {
            return entities != null && !entities.isEmpty();
        }

        /**
         * Check if query is about a specific topic.
         */
        public boolean hasTopics() {
            return topics != null && !topics.isEmpty();
        }

        /**
         * Get all extracted terms for search.
         */
        public Set<String> getAllTerms() {
            Set<String> all = new HashSet<>();
            if (entities != null) all.addAll(entities);
            if (topics != null) all.addAll(topics);
            if (keywords != null) all.addAll(keywords);
            return all;
        }
    }
}
