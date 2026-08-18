package com.yupi.yuaiagent.memory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Token 预算分配器 — 按配置百分比将总预算分配到各记忆层，并提供内容截断能力。
 *
 * <p>默认百分比：L1=60%, L2=15%, L3=10%, L4=15%
 * <p>Token 估算采用简单启发式：1 token ≈ 2 个中文字符 或 4 个英文字符（无需外部 tokenizer）。
 */
@Component
public class TokenBudgetAllocator {

    private final int slidingWindowPercent;
    private final int factStorePercent;
    private final int summaryPercent;
    private final int experiencePercent;

    public TokenBudgetAllocator(
            @Value("${memory.layers.sliding-window.token-budget-percent:60}") int slidingWindowPercent,
            @Value("${memory.layers.fact-store.token-budget-percent:15}") int factStorePercent,
            @Value("${memory.layers.summary.token-budget-percent:10}") int summaryPercent,
            @Value("${memory.layers.experience.token-budget-percent:15}") int experiencePercent) {
        this.slidingWindowPercent = slidingWindowPercent;
        this.factStorePercent = factStorePercent;
        this.summaryPercent = summaryPercent;
        this.experiencePercent = experiencePercent;
    }

    /**
     * 按配置百分比将总 Token 预算分配到各记忆层。
     *
     * <p>不做 padding：各层分配值之和 ≤ totalBudget（整数除法可能有余数）。
     *
     * @param totalBudget 总 Token 预算
     * @return 各层分配到的 Token 数量
     */
    public Map<MemoryLayer, Integer> allocate(int totalBudget) {
        if (totalBudget <= 0) {
            Map<MemoryLayer, Integer> empty = new EnumMap<>(MemoryLayer.class);
            for (MemoryLayer layer : MemoryLayer.values()) {
                empty.put(layer, 0);
            }
            return empty;
        }

        Map<MemoryLayer, Integer> allocation = new EnumMap<>(MemoryLayer.class);
        allocation.put(MemoryLayer.SLIDING_WINDOW, totalBudget * slidingWindowPercent / 100);
        allocation.put(MemoryLayer.FACT_STORE, totalBudget * factStorePercent / 100);
        allocation.put(MemoryLayer.SUMMARY, totalBudget * summaryPercent / 100);
        allocation.put(MemoryLayer.EXPERIENCE, totalBudget * experiencePercent / 100);

        return allocation;
    }

    /**
     * 将内容截断至指定 Token 数以内。
     *
     * <p>截断策略：优先在换行符边界截断，其次在句子结束符（。！？.!?）边界截断，
     * 避免在中文字符中间截断。
     *
     * @param content   原始内容
     * @param maxTokens 最大 Token 数
     * @return 截断后的内容（不超过 maxTokens 对应的字符长度）
     */
    public String truncateToTokens(String content, int maxTokens) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (maxTokens <= 0) {
            return "";
        }

        int currentTokens = estimateTokens(content);
        if (currentTokens <= maxTokens) {
            return content;
        }

        // 估算允许的最大字符数（按混合内容取保守估计：每 token 约 2 字符）
        int maxChars = estimateMaxChars(content, maxTokens);

        if (maxChars >= content.length()) {
            return content;
        }

        // 尝试在换行符边界截断
        String truncated = truncateAtLineBoundary(content, maxChars);
        if (truncated != null && !truncated.isEmpty()) {
            return enforceTokenLimit(truncated, maxTokens);
        }

        // 尝试在句子边界截断
        truncated = truncateAtSentenceBoundary(content, maxChars);
        if (truncated != null && !truncated.isEmpty()) {
            return enforceTokenLimit(truncated, maxTokens);
        }

        // 回退：直接按字符数截断
        truncated = content.substring(0, maxChars);
        return enforceTokenLimit(truncated, maxTokens);
    }

    private String enforceTokenLimit(String content, int maxTokens) {
        String result = content;
        while (!result.isEmpty() && estimateTokens(result) > maxTokens) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 估算内容的 Token 数。
     *
     * <p>启发式规则：中文字符（包括中文标点）每 2 个字符约 1 token；
     * 英文/数字/ASCII 字符每 4 个字符约 1 token。
     *
     * @param content 文本内容
     * @return 估算的 Token 数
     */
    public int estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        int chineseChars = 0;
        int otherChars = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (isChinese(c)) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }

        // 中文：2 字符 ≈ 1 token；英文/其他：4 字符 ≈ 1 token
        // 向上取整确保不低估
        int chineseTokens = (chineseChars + 1) / 2;
        int otherTokens = (otherChars + 3) / 4;

        return chineseTokens + otherTokens;
    }

    /**
     * 根据内容的中英文比例估算 maxTokens 对应的最大字符数。
     */
    private int estimateMaxChars(String content, int maxTokens) {
        if (content.isEmpty()) {
            return 0;
        }

        int chineseChars = 0;
        int totalChars = content.length();

        for (int i = 0; i < totalChars; i++) {
            if (isChinese(content.charAt(i))) {
                chineseChars++;
            }
        }

        double chineseRatio = (double) chineseChars / totalChars;

        // 加权平均：中文每 token 约 2 字符，英文每 token 约 4 字符
        double charsPerToken = chineseRatio * 2.0 + (1.0 - chineseRatio) * 4.0;
        return (int) (maxTokens * charsPerToken);
    }

    /**
     * 尝试在最近的换行符边界截断。
     * 在 maxChars 范围内找最后一个换行符，至少保留 50% 的内容。
     */
    private String truncateAtLineBoundary(String content, int maxChars) {
        int searchEnd = Math.min(maxChars, content.length());
        int lastNewline = content.lastIndexOf('\n', searchEnd - 1);

        // 至少保留 50% 的允许长度，避免过度截断
        if (lastNewline > maxChars / 2) {
            return content.substring(0, lastNewline);
        }
        return null;
    }

    /**
     * 尝试在最近的句子边界截断。
     * 支持中英文句号、问号、感叹号。
     */
    private String truncateAtSentenceBoundary(String content, int maxChars) {
        int searchEnd = Math.min(maxChars, content.length());
        int lastSentenceEnd = -1;

        for (int i = searchEnd - 1; i >= maxChars / 2; i--) {
            char c = content.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                lastSentenceEnd = i + 1;
                break;
            }
        }

        if (lastSentenceEnd > 0) {
            return content.substring(0, lastSentenceEnd);
        }
        return null;
    }

    /**
     * 判断字符是否为中文字符（含中文标点）。
     */
    private boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
