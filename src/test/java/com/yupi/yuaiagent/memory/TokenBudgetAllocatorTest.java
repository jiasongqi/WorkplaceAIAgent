package com.yupi.yuaiagent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenBudgetAllocator 单元测试
 *
 * <p>验证：
 * <ul>
 *   <li>分配百分比计算正确且不超总预算</li>
 *   <li>截断遵循 maxTokens 限制</li>
 *   <li>截断优先在行/句子边界进行</li>
 *   <li>空内容返回空字符串</li>
 * </ul>
 */
class TokenBudgetAllocatorTest {

    private TokenBudgetAllocator allocator;

    @BeforeEach
    void setUp() {
        // 使用默认配置：L1=60%, L2=15%, L3=10%, L4=15%
        allocator = new TokenBudgetAllocator(60, 15, 10, 15);
    }

    @Nested
    @DisplayName("allocate() 预算分配测试")
    class AllocationTests {

        @Test
        @DisplayName("默认百分比分配 6000 token 预算")
        void allocateDefaultPercentages() {
            Map<MemoryLayer, Integer> result = allocator.allocate(6000);

            assertThat(result.get(MemoryLayer.SLIDING_WINDOW)).isEqualTo(3600); // 60%
            assertThat(result.get(MemoryLayer.FACT_STORE)).isEqualTo(900);      // 15%
            assertThat(result.get(MemoryLayer.SUMMARY)).isEqualTo(600);         // 10%
            assertThat(result.get(MemoryLayer.EXPERIENCE)).isEqualTo(900);      // 15%
        }

        @Test
        @DisplayName("分配值之和不超过总预算")
        void allocationSumDoesNotExceedTotal() {
            int totalBudget = 6000;
            Map<MemoryLayer, Integer> result = allocator.allocate(totalBudget);

            int sum = result.values().stream().mapToInt(Integer::intValue).sum();
            assertThat(sum).isLessThanOrEqualTo(totalBudget);
        }

        @Test
        @DisplayName("百分比之和为 100% 时，分配无损失（整除情况）")
        void percentagesSumTo100NoLoss() {
            // 6000 可被 100 整除，各百分比计算无余数
            Map<MemoryLayer, Integer> result = allocator.allocate(6000);

            int sum = result.values().stream().mapToInt(Integer::intValue).sum();
            assertThat(sum).isEqualTo(6000);
        }

        @Test
        @DisplayName("所有层都获得分配值")
        void allLayersReceiveAllocation() {
            Map<MemoryLayer, Integer> result = allocator.allocate(6000);

            assertThat(result).containsKeys(MemoryLayer.values());
            for (MemoryLayer layer : MemoryLayer.values()) {
                assertThat(result.get(layer)).isGreaterThan(0);
            }
        }

        @Test
        @DisplayName("零预算时所有层分配为 0")
        void zeroBudgetAllocatesZeroToAll() {
            Map<MemoryLayer, Integer> result = allocator.allocate(0);

            for (MemoryLayer layer : MemoryLayer.values()) {
                assertThat(result.get(layer)).isEqualTo(0);
            }
        }

        @Test
        @DisplayName("负预算时所有层分配为 0")
        void negativeBudgetAllocatesZeroToAll() {
            Map<MemoryLayer, Integer> result = allocator.allocate(-100);

            for (MemoryLayer layer : MemoryLayer.values()) {
                assertThat(result.get(layer)).isEqualTo(0);
            }
        }

        @Test
        @DisplayName("小预算时整数除法不产生负值")
        void smallBudgetNoNegativeValues() {
            Map<MemoryLayer, Integer> result = allocator.allocate(7);

            for (MemoryLayer layer : MemoryLayer.values()) {
                assertThat(result.get(layer)).isGreaterThanOrEqualTo(0);
            }
        }

        @Test
        @DisplayName("自定义百分比分配正确")
        void customPercentagesAllocateCorrectly() {
            TokenBudgetAllocator custom = new TokenBudgetAllocator(50, 20, 20, 10);
            Map<MemoryLayer, Integer> result = custom.allocate(1000);

            assertThat(result.get(MemoryLayer.SLIDING_WINDOW)).isEqualTo(500);
            assertThat(result.get(MemoryLayer.FACT_STORE)).isEqualTo(200);
            assertThat(result.get(MemoryLayer.SUMMARY)).isEqualTo(200);
            assertThat(result.get(MemoryLayer.EXPERIENCE)).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("estimateTokens() Token 估算测试")
    class EstimateTokensTests {

        @Test
        @DisplayName("纯英文内容估算：4 字符约 1 token")
        void pureEnglishEstimation() {
            // 12 英文字符 → (12+3)/4 = 3 tokens
            int tokens = allocator.estimateTokens("Hello World!");
            assertThat(tokens).isEqualTo(3);
        }

        @Test
        @DisplayName("纯中文内容估算：2 字符约 1 token")
        void pureChineseEstimation() {
            // 4 中文字符 → (4+1)/2 = 2 tokens（向上取整）
            int tokens = allocator.estimateTokens("你好世界");
            assertThat(tokens).isEqualTo(2);
        }

        @Test
        @DisplayName("空内容返回 0")
        void emptyContentReturnsZero() {
            assertThat(allocator.estimateTokens("")).isEqualTo(0);
            assertThat(allocator.estimateTokens(null)).isEqualTo(0);
        }

        @Test
        @DisplayName("混合中英文内容估算")
        void mixedContentEstimation() {
            // "你好Hello" = 2 中文 + 5 英文
            // 中文 tokens: (2+1)/2 = 1, 英文 tokens: (5+3)/4 = 2
            int tokens = allocator.estimateTokens("你好Hello");
            assertThat(tokens).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("truncateToTokens() 截断测试")
    class TruncationTests {

        @Test
        @DisplayName("内容未超预算时原样返回")
        void contentWithinBudgetReturnedUnchanged() {
            String content = "Hello";
            String result = allocator.truncateToTokens(content, 100);
            assertThat(result).isEqualTo(content);
        }

        @Test
        @DisplayName("空内容返回空字符串")
        void emptyContentReturnsEmpty() {
            assertThat(allocator.truncateToTokens("", 100)).isEmpty();
            assertThat(allocator.truncateToTokens(null, 100)).isEmpty();
        }

        @Test
        @DisplayName("maxTokens 为 0 时返回空字符串")
        void zeroMaxTokensReturnsEmpty() {
            assertThat(allocator.truncateToTokens("Hello World", 0)).isEmpty();
        }

        @Test
        @DisplayName("截断结果不超过 maxTokens")
        void truncationRespectsTokenLimit() {
            // 创建一个较长的内容
            String content = "这是一段较长的中文内容。\n第二行内容。\n第三行内容。\n第四行内容。\n第五行。";
            String result = allocator.truncateToTokens(content, 5);

            int resultTokens = allocator.estimateTokens(result);
            assertThat(resultTokens).isLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("截断优先在换行符边界")
        void truncationPrefersLineBoundary() {
            String content = "第一行内容比较长\n第二行内容也比较长\n第三行内容更加长一些";
            // 给一个刚好能容纳第一行多一点的 token 数
            int firstLineTokens = allocator.estimateTokens("第一行内容比较长");
            String result = allocator.truncateToTokens(content, firstLineTokens + 2);

            // 结果应该在换行符处截断
            assertThat(result).doesNotContain("第三行");
            assertThat(result.endsWith("\n") || !result.contains("\n") || result.endsWith("长"))
                    .isTrue();
        }

        @Test
        @DisplayName("英文内容截断在句子边界")
        void englishTruncationAtSentenceBoundary() {
            String content = "First sentence. Second sentence. Third sentence. Fourth sentence.";
            // 给足够前两句但不够全部的 token 数
            int twoSentenceTokens = allocator.estimateTokens("First sentence. Second sentence.");
            String result = allocator.truncateToTokens(content, twoSentenceTokens + 1);

            // 截断后应在句号处结束
            assertThat(result).endsWith(".");
            int resultTokens = allocator.estimateTokens(result);
            assertThat(resultTokens).isLessThanOrEqualTo(twoSentenceTokens + 1);
        }

        @Test
        @DisplayName("中文内容截断在句号边界")
        void chineseTruncationAtSentenceBoundary() {
            String content = "第一句话很长很长很长。第二句话也很长很长。第三句话同样很长。第四句话。";
            // 只给前两句的 token
            int tokens = allocator.estimateTokens("第一句话很长很长很长。第二句话也很长很长。");
            String result = allocator.truncateToTokens(content, tokens);

            assertThat(result).endsWith("。");
            int resultTokens = allocator.estimateTokens(result);
            assertThat(resultTokens).isLessThanOrEqualTo(tokens);
        }

        @Test
        @DisplayName("负 maxTokens 返回空字符串")
        void negativeMaxTokensReturnsEmpty() {
            assertThat(allocator.truncateToTokens("Hello World", -5)).isEmpty();
        }

        @Test
        @DisplayName("非常大的 maxTokens 不截断内容")
        void veryLargeMaxTokensNoTruncation() {
            String content = "短内容";
            assertThat(allocator.truncateToTokens(content, Integer.MAX_VALUE)).isEqualTo(content);
        }
    }
}
