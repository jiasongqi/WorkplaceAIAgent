package com.yupi.yuaiagent.memory.sliding;

import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.memory.TokenBudgetAllocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SlidingWindowLayer 单元测试
 *
 * <p>验证：
 * <ul>
 *   <li>消息保留与 maxMessages 限制（Req 2.1 / 2.2）</li>
 *   <li>Token 预算裁剪移除最旧消息（Req 2.4）</li>
 *   <li>消息始终按插入顺序排列（Req 2.5）</li>
 *   <li>空消息列表返回空列表</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlidingWindowLayerTest {

    @Mock
    private ChatMemoryManager chatMemoryManager;

    @Mock
    private ChatMemory chatMemory;

    private TokenBudgetAllocator tokenBudgetAllocator;
    private SlidingWindowLayer slidingWindowLayer;

    private static final String CONVERSATION_ID = "test-conv-123";
    private static final String AGENT_TYPE = "general";
    private static final int MAX_MESSAGES = 5;

    @BeforeEach
    void setUp() {
        tokenBudgetAllocator = new TokenBudgetAllocator(60, 15, 10, 15);
        slidingWindowLayer = new SlidingWindowLayer(chatMemoryManager, tokenBudgetAllocator, MAX_MESSAGES);
        when(chatMemoryManager.getMemory(AGENT_TYPE)).thenReturn(chatMemory);
    }

    @Nested
    @DisplayName("消息保留与 maxMessages 限制")
    class MessageRetentionTests {

        @Test
        @DisplayName("消息数量不超过 maxMessages 时全部保留")
        void retainsAllMessagesWithinLimit() {
            List<Message> messages = createMessages(3);
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, 10000);

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("消息数量恰好等于 maxMessages 时全部保留")
        void retainsAllMessagesAtLimit() {
            List<Message> messages = createMessages(MAX_MESSAGES);
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, 10000);

            assertThat(result).hasSize(MAX_MESSAGES);
        }

        @Test
        @DisplayName("消息数量超过 maxMessages 时只保留最近 N 条（Req 2.1 / 2.2）")
        void discardsOldestWhenExceedingMaxMessages() {
            List<Message> messages = createNumberedMessages(8);
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, 10000);

            // 应保留最后 5 条（索引 3-7），丢弃最旧的 3 条（索引 0-2）
            assertThat(result).hasSize(MAX_MESSAGES);
            assertThat(result.get(0).getText()).isEqualTo("Message 3");
            assertThat(result.get(4).getText()).isEqualTo("Message 7");
        }
    }

    @Nested
    @DisplayName("Token 预算裁剪")
    class TokenBudgetTrimmingTests {

        @Test
        @DisplayName("消息总 token 不超预算时全部保留")
        void retainsAllWhenWithinBudget() {
            List<Message> messages = List.of(
                    new UserMessage("Hi"),
                    new AssistantMessage("Hello")
            );
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, 10000);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("超出 token 预算时从最旧消息开始移除（Req 2.4）")
        void trimsOldestWhenExceedingTokenBudget() {
            // 创建 3 条消息，每条约有足够的 token
            List<Message> messages = List.of(
                    new UserMessage("这是第一条比较长的消息内容，包含很多中文字符用于测试裁剪"),
                    new UserMessage("这是第二条比较长的消息内容，同样包含很多中文字符"),
                    new UserMessage("这是第三条消息")
            );
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            // 计算第三条消息的 token 数，设置预算略大于最后两条的 token
            int lastTwoTokens = tokenBudgetAllocator.estimateTokens("这是第二条比较长的消息内容，同样包含很多中文字符")
                    + tokenBudgetAllocator.estimateTokens("这是第三条消息");

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, lastTwoTokens);

            // 第一条（最旧）应被裁剪掉
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getText()).contains("第二条");
            assertThat(result.get(1).getText()).contains("第三条");
        }

        @Test
        @DisplayName("token 预算为 0 时返回空列表")
        void zeroBudgetReturnsEmpty() {
            List<Message> messages = List.of(new UserMessage("Hello"));
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, 0);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("token 预算极小时仅保留最新的小消息")
        void verySmallBudgetKeepsOnlyLatestSmallMessage() {
            List<Message> messages = List.of(
                    new UserMessage("这是一条很长很长很长很长很长很长很长很长的消息"),
                    new UserMessage("短")
            );
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            // "短" 估算约 1 token
            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, 2);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getText()).isEqualTo("短");
        }
    }

    @Nested
    @DisplayName("消息顺序保持")
    class OrderPreservationTests {

        @Test
        @DisplayName("返回消息保持插入顺序（Req 2.5）")
        void preservesInsertionOrder() {
            List<Message> messages = createNumberedMessages(MAX_MESSAGES);
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, 10000);

            for (int i = 0; i < result.size() - 1; i++) {
                String currentText = result.get(i).getText();
                String nextText = result.get(i + 1).getText();
                int currentIndex = Integer.parseInt(currentText.replace("Message ", ""));
                int nextIndex = Integer.parseInt(nextText.replace("Message ", ""));
                assertThat(currentIndex).isLessThan(nextIndex);
            }
        }

        @Test
        @DisplayName("maxMessages 裁剪后仍保持顺序")
        void preservesOrderAfterMaxMessagesTrimming() {
            List<Message> messages = createNumberedMessages(10);
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, 10000);

            // 应该是 Message 5, 6, 7, 8, 9
            assertThat(result).hasSize(MAX_MESSAGES);
            for (int i = 0; i < result.size(); i++) {
                assertThat(result.get(i).getText()).isEqualTo("Message " + (i + 5));
            }
        }

        @Test
        @DisplayName("token 裁剪后仍保持顺序（最旧先移除）")
        void preservesOrderAfterTokenTrimming() {
            // 3 条消息，中间那条最长
            List<Message> messages = List.of(
                    new UserMessage("第一条消息内容比较短"),
                    new UserMessage("第二条消息内容"),
                    new UserMessage("第三条")
            );
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            // 设置一个刚够后两条的 token 预算
            int budget = tokenBudgetAllocator.estimateTokens("第二条消息内容")
                    + tokenBudgetAllocator.estimateTokens("第三条");

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, budget);

            // 应按顺序返回第二、第三条
            assertThat(result.get(0).getText()).isEqualTo("第二条消息内容");
            assertThat(result.get(result.size() - 1).getText()).isEqualTo("第三条");
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCaseTests {

        @Test
        @DisplayName("空消息列表返回空列表")
        void emptyMessagesReturnsEmptyList() {
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(Collections.emptyList());

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, 10000);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null 消息列表返回空列表")
        void nullMessagesReturnsEmptyList() {
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(null);

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, 10000);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null conversationId 返回空列表")
        void nullConversationIdReturnsEmptyList() {
            List<Message> result = slidingWindowLayer.getRecentMessages(null, AGENT_TYPE, 10000);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null agentType 返回空列表")
        void nullAgentTypeReturnsEmptyList() {
            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, null, 10000);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("单条消息正常返回")
        void singleMessageReturnsCorrectly() {
            List<Message> messages = List.of(new UserMessage("Solo message"));
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            List<Message> result = slidingWindowLayer.getRecentMessages(CONVERSATION_ID, AGENT_TYPE, 10000);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getText()).isEqualTo("Solo message");
        }
    }

    @Nested
    @DisplayName("formatForContext() 格式化测试")
    class FormatTests {

        @Test
        @DisplayName("格式化输出包含角色和内容")
        void formatIncludesRoleAndContent() {
            List<Message> messages = List.of(
                    new UserMessage("用户问题"),
                    new AssistantMessage("AI回答")
            );
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(messages);

            String result = slidingWindowLayer.formatForContext(CONVERSATION_ID, AGENT_TYPE, 10000);

            assertThat(result).contains("user: 用户问题");
            assertThat(result).contains("assistant: AI回答");
        }

        @Test
        @DisplayName("空消息返回空字符串")
        void emptyMessagesReturnsEmptyString() {
            when(chatMemory.get(CONVERSATION_ID)).thenReturn(Collections.emptyList());

            String result = slidingWindowLayer.formatForContext(CONVERSATION_ID, AGENT_TYPE, 10000);

            assertThat(result).isEmpty();
        }
    }

    // ─── Helper Methods ───

    /**
     * 创建指定数量的简短 UserMessage 列表
     */
    private List<Message> createMessages(int count) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add(new UserMessage("msg " + i));
        }
        return messages;
    }

    /**
     * 创建编号消息列表，文本格式为 "Message N"
     */
    private List<Message> createNumberedMessages(int count) {
        List<Message> messages = new ArrayList<>();
        IntStream.range(0, count).forEach(i ->
                messages.add(new UserMessage("Message " + i))
        );
        return messages;
    }
}
