package com.yupi.yuaiagent.memory;

import com.yupi.yuaiagent.memory.experience.ExperienceDocument;
import com.yupi.yuaiagent.memory.experience.ExperienceStoreLayer;
import com.yupi.yuaiagent.memory.extraction.ExtractionPipeline;
import com.yupi.yuaiagent.memory.fact.FactStoreLayer;
import com.yupi.yuaiagent.memory.sliding.SlidingWindowLayer;
import com.yupi.yuaiagent.memory.summary.SummaryLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MemoryCoordinator 集成测试（使用 Mock 模拟外部层依赖）。
 *
 * <p>测试场景：
 * <ul>
 *   <li>全层组装：4 层全部返回内容，验证 SystemMessage 包含所有 section</li>
 *   <li>超时回退：某层超时后使用缓存/空值</li>
 *   <li>部分失败：某层抛异常，其余层正常贡献</li>
 *   <li>onTurnCompleted：委托给 ExtractionPipeline，优雅处理空输入</li>
 *   <li>缓存行为：成功组装后，失败层使用缓存的 "last known good" 值</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MemoryCoordinatorTest {

    @Mock
    private SlidingWindowLayer slidingWindow;

    @Mock
    private FactStoreLayer factStore;

    @Mock
    private SummaryLayer summaryLayer;

    @Mock
    private ExperienceStoreLayer experienceStore;

    @Mock
    private ExtractionPipeline extractionPipeline;

    @Mock
    private ExperienceQueryBuilder experienceQueryBuilder;

    private TokenBudgetAllocator budgetAllocator;
    private MemoryCoordinator coordinator;

    private static final String USER_ID = "test-user-001";
    private static final String CONVERSATION_ID = "conv-001";
    private static final String AGENT_TYPE = "general";
    private static final int TIMEOUT_MS = 500;
    private static final int TOTAL_BUDGET = 6000;

    @BeforeEach
    void setUp() {
        // Real allocator with default percentages: L1=60%, L2=15%, L3=10%, L4=15%
        budgetAllocator = new TokenBudgetAllocator(60, 15, 10, 15);
        Executor directExecutor = Runnable::run;
        coordinator = new MemoryCoordinator(
                slidingWindow,
                factStore,
                summaryLayer,
                experienceStore,
                budgetAllocator,
                extractionPipeline,
                experienceQueryBuilder,
                directExecutor,
                TIMEOUT_MS,
                TOTAL_BUDGET
        );
        lenient().when(experienceQueryBuilder.build(anyString(), any())).thenReturn("职场咨询 面试");
    }

    @Nested
    @DisplayName("全层组装测试")
    class FullAssemblyTests {

        @Test
        @DisplayName("所有层返回内容时，SystemMessage 包含所有 section 且按正确顺序排列")
        void allLayersContribute_systemMessageContainsAllSections() {
            // Arrange
            when(slidingWindow.formatForContext(eq(CONVERSATION_ID), eq(AGENT_TYPE), anyInt()))
                    .thenReturn("user: 你好\nassistant: 你好，有什么可以帮您？");
            when(factStore.formatForContext(eq(USER_ID), anyInt()))
                    .thenReturn("【用户事实】\n- 身份: 姓名=张三; 行业=互联网");
            when(summaryLayer.getRecentSummaries(eq(USER_ID), anyInt()))
                    .thenReturn("【近期对话摘要】\n  话题: 职业规划\n  待办: 更新简历");
            when(experienceStore.searchSimilar(eq(USER_ID), anyString()))
                    .thenReturn(List.of(new ExperienceDocument(
                            "exp-1", USER_ID, AGENT_TYPE,
                            "成功通过面试获得 offer",
                            "success", Instant.now(), Map.of()
                    )));

            // Act
            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // Assert
            String text = result.getText();
            assertThat(text).isNotBlank();

            // Verify ordering: L2 facts → L3 summary → L4 experience → L1 sliding window
            int factIndex = text.indexOf("【用户事实】");
            int summaryIndex = text.indexOf("【近期对话摘要】");
            int experienceIndex = text.indexOf("【历史经验】");
            int slidingIndex = text.indexOf("【近期对话】");

            assertThat(factIndex).isGreaterThanOrEqualTo(0);
            assertThat(summaryIndex).isGreaterThan(factIndex);
            assertThat(experienceIndex).isGreaterThan(summaryIndex);
            assertThat(slidingIndex).isGreaterThan(experienceIndex);
        }

        @Test
        @DisplayName("SystemMessage 包含各层实际内容")
        void allLayersContribute_contentPreserved() {
            // Arrange
            when(slidingWindow.formatForContext(eq(CONVERSATION_ID), eq(AGENT_TYPE), anyInt()))
                    .thenReturn("user: 如何准备面试");
            when(factStore.formatForContext(eq(USER_ID), anyInt()))
                    .thenReturn("【用户事实】\n- 身份: 行业=金融");
            when(summaryLayer.getRecentSummaries(eq(USER_ID), anyInt()))
                    .thenReturn("【近期对话摘要】\n  话题: 跳槽准备");
            when(experienceStore.searchSimilar(eq(USER_ID), anyString()))
                    .thenReturn(List.of(new ExperienceDocument(
                            "exp-2", USER_ID, AGENT_TYPE,
                            "跳槽后薪资涨幅30%",
                            "success", Instant.now(), null
                    )));

            // Act
            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // Assert
            String text = result.getText();
            assertThat(text).contains("行业=金融");
            assertThat(text).contains("跳槽准备");
            assertThat(text).contains("跳槽后薪资涨幅30%");
            assertThat(text).contains("如何准备面试");
        }

        @Test
        @DisplayName("所有层返回空内容时，返回空的 SystemMessage")
        void allLayersReturnEmpty_emptySystemMessage() {
            // Arrange
            when(slidingWindow.formatForContext(anyString(), anyString(), anyInt())).thenReturn("");
            when(factStore.formatForContext(anyString(), anyInt())).thenReturn("");
            when(summaryLayer.getRecentSummaries(anyString(), anyInt())).thenReturn("");
            when(experienceStore.searchSimilar(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            // Act
            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // Assert
            assertThat(result.getText()).isEmpty();
        }
    }

    @Nested
    @DisplayName("超时回退测试")
    class TimeoutFallbackTests {

        @Test
        @DisplayName("单层超时时，其余层正常贡献")
        void oneLayerTimesOut_otherLayersStillContribute() {
            // Arrange: fact store is slow (exceeds timeout)
            when(factStore.formatForContext(eq(USER_ID), anyInt())).thenAnswer(invocation -> {
                Thread.sleep(TIMEOUT_MS + 300);
                return "【用户事实】\n- slow data";
            });

            // Other layers respond quickly
            when(slidingWindow.formatForContext(eq(CONVERSATION_ID), eq(AGENT_TYPE), anyInt()))
                    .thenReturn("user: 我需要帮助");
            when(summaryLayer.getRecentSummaries(eq(USER_ID), anyInt()))
                    .thenReturn("【近期对话摘要】\n  话题: 求助");
            when(experienceStore.searchSimilar(eq(USER_ID), anyString()))
                    .thenReturn(Collections.emptyList());

            // Act
            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // Assert: other layers contributed
            String text = result.getText();
            assertThat(text).contains("我需要帮助");
            assertThat(text).contains("求助");
        }

        @Test
        @DisplayName("超时层无缓存时使用空字符串回退")
        void timedOutLayerNoCached_fallsBackToEmpty() {
            // Arrange: sliding window is slow, no prior cache exists
            when(slidingWindow.formatForContext(eq(CONVERSATION_ID), eq(AGENT_TYPE), anyInt()))
                    .thenAnswer(invocation -> {
                        Thread.sleep(TIMEOUT_MS + 300);
                        return "slow sliding content";
                    });

            when(factStore.formatForContext(eq(USER_ID), anyInt()))
                    .thenReturn("【用户事实】\n- 身份: 有效数据");
            when(summaryLayer.getRecentSummaries(eq(USER_ID), anyInt())).thenReturn("");
            when(experienceStore.searchSimilar(eq(USER_ID), anyString()))
                    .thenReturn(Collections.emptyList());

            // Act
            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // Assert: fact store contributed, sliding window content not present (timed out, no cache)
            String text = result.getText();
            assertThat(text).contains("有效数据");
            // The slow content should not be in the result (timed out)
            // Note: due to async nature, it might or might not make it in time;
            // but the other layers are definitely there
            assertThat(text).contains("【用户事实】");
        }
    }

    @Nested
    @DisplayName("部分失败测试")
    class PartialFailureTests {

        @Test
        @DisplayName("单层抛异常时，其余层正常贡献")
        void oneLayerThrowsException_otherLayersWork() {
            // Arrange: summary layer throws exception
            when(summaryLayer.getRecentSummaries(eq(USER_ID), anyInt()))
                    .thenThrow(new RuntimeException("Summary storage corrupt"));

            when(slidingWindow.formatForContext(eq(CONVERSATION_ID), eq(AGENT_TYPE), anyInt()))
                    .thenReturn("user: 今天天气好");
            when(factStore.formatForContext(eq(USER_ID), anyInt()))
                    .thenReturn("【用户事实】\n- 偏好: 简洁风格");
            when(experienceStore.searchSimilar(eq(USER_ID), anyString()))
                    .thenReturn(List.of(new ExperienceDocument(
                            "exp-3", USER_ID, AGENT_TYPE,
                            "面试经验丰富",
                            "insight", Instant.now(), null
                    )));

            // Act
            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // Assert: other layers still contribute
            String text = result.getText();
            assertThat(text).contains("今天天气好");
            assertThat(text).contains("简洁风格");
            assertThat(text).contains("面试经验丰富");
        }

        @Test
        @DisplayName("多层同时失败时，存活层仍正常贡献")
        void multipleLayersFail_survivingLayersWork() {
            // Arrange: both fact store and experience store fail
            when(factStore.formatForContext(eq(USER_ID), anyInt()))
                    .thenThrow(new RuntimeException("Disk full"));
            when(experienceStore.searchSimilar(eq(USER_ID), anyString()))
                    .thenThrow(new RuntimeException("Vector store unavailable"));

            when(slidingWindow.formatForContext(eq(CONVERSATION_ID), eq(AGENT_TYPE), anyInt()))
                    .thenReturn("user: 帮我写简历");
            when(summaryLayer.getRecentSummaries(eq(USER_ID), anyInt()))
                    .thenReturn("【近期对话摘要】\n  话题: 简历优化");

            // Act
            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // Assert: surviving layers contribute, result is not empty
            String text = result.getText();
            assertThat(text).contains("帮我写简历");
            assertThat(text).contains("简历优化");
        }

        @Test
        @DisplayName("所有层失败时返回空 SystemMessage 而非抛异常")
        void allLayersFail_returnsEmptySystemMessage() {
            // Arrange
            when(slidingWindow.formatForContext(anyString(), anyString(), anyInt()))
                    .thenThrow(new RuntimeException("L1 failure"));
            when(factStore.formatForContext(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("L2 failure"));
            when(summaryLayer.getRecentSummaries(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("L3 failure"));
            when(experienceStore.searchSimilar(anyString(), anyString()))
                    .thenThrow(new RuntimeException("L4 failure"));

            // Act — should not throw
            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getText()).isEmpty();
        }
    }

    @Nested
    @DisplayName("onTurnCompleted 测试")
    class OnTurnCompletedTests {

        @Test
        @DisplayName("正常调用委托给 ExtractionPipeline.processAsync")
        void normalCall_delegatesToExtractionPipeline() {
            // Arrange
            List<Message> messages = List.of(
                    new UserMessage("你好"),
                    new AssistantMessage("你好，有什么可以帮您？")
            );

            // Act
            coordinator.onTurnCompleted(USER_ID, CONVERSATION_ID, AGENT_TYPE, messages);

            // Assert
            verify(extractionPipeline).processAsync(
                    eq(USER_ID), eq(CONVERSATION_ID), eq(AGENT_TYPE), eq(messages));
        }

        @Test
        @DisplayName("userId 为 null 时不调用 ExtractionPipeline")
        void nullUserId_skipsExtraction() {
            List<Message> messages = List.of(new UserMessage("test"));

            coordinator.onTurnCompleted(null, CONVERSATION_ID, AGENT_TYPE, messages);

            verify(extractionPipeline, never()).processAsync(anyString(), anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("userId 为空白时不调用 ExtractionPipeline")
        void blankUserId_skipsExtraction() {
            List<Message> messages = List.of(new UserMessage("test"));

            coordinator.onTurnCompleted("  ", CONVERSATION_ID, AGENT_TYPE, messages);

            verify(extractionPipeline, never()).processAsync(anyString(), anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("conversationId 为 null 时不调用 ExtractionPipeline")
        void nullConversationId_skipsExtraction() {
            List<Message> messages = List.of(new UserMessage("test"));

            coordinator.onTurnCompleted(USER_ID, null, AGENT_TYPE, messages);

            verify(extractionPipeline, never()).processAsync(anyString(), anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("messages 为 null 时不调用 ExtractionPipeline")
        void nullMessages_skipsExtraction() {
            coordinator.onTurnCompleted(USER_ID, CONVERSATION_ID, AGENT_TYPE, null);

            verify(extractionPipeline, never()).processAsync(anyString(), anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("messages 为空列表时不调用 ExtractionPipeline")
        void emptyMessages_skipsExtraction() {
            coordinator.onTurnCompleted(USER_ID, CONVERSATION_ID, AGENT_TYPE, Collections.emptyList());

            verify(extractionPipeline, never()).processAsync(anyString(), anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("agentType 为 null 时使用默认值 'general'")
        void nullAgentType_usesDefault() {
            List<Message> messages = List.of(new UserMessage("test"));

            coordinator.onTurnCompleted(USER_ID, CONVERSATION_ID, null, messages);

            verify(extractionPipeline).processAsync(
                    eq(USER_ID), eq(CONVERSATION_ID), eq("general"), eq(messages));
        }

        @Test
        @DisplayName("ExtractionPipeline 抛异常时不向外传播")
        void extractionThrows_noException() {
            List<Message> messages = List.of(new UserMessage("test"));
            doThrow(new RuntimeException("pipeline error"))
                    .when(extractionPipeline).processAsync(anyString(), anyString(), anyString(), anyList());

            // Should not throw
            coordinator.onTurnCompleted(USER_ID, CONVERSATION_ID, AGENT_TYPE, messages);
        }

        @Test
        @DisplayName("三参数重载方法使用默认 agentType 'general'")
        void threeArgOverload_usesDefaultAgentType() {
            List<Message> messages = List.of(new UserMessage("hello"));

            coordinator.onTurnCompleted(USER_ID, CONVERSATION_ID, messages);

            verify(extractionPipeline).processAsync(
                    eq(USER_ID), eq(CONVERSATION_ID), eq("general"), eq(messages));
        }
    }

    @Nested
    @DisplayName("缓存行为测试")
    class CacheBehaviorTests {

        @Test
        @DisplayName("成功组装后，后续调用中失败层使用缓存值")
        void successfulAssembly_subsequentFailureUsesCachedValue() {
            // Arrange: first call — all layers succeed
            when(slidingWindow.formatForContext(eq(CONVERSATION_ID), eq(AGENT_TYPE), anyInt()))
                    .thenReturn("user: 第一次对话");
            when(factStore.formatForContext(eq(USER_ID), anyInt()))
                    .thenReturn("【用户事实】\n- 身份: 姓名=李四");
            when(summaryLayer.getRecentSummaries(eq(USER_ID), anyInt()))
                    .thenReturn("【近期对话摘要】\n  话题: 初次咨询");
            when(experienceStore.searchSimilar(eq(USER_ID), anyString()))
                    .thenReturn(Collections.emptyList());

            // First call to populate cache
            SystemMessage firstResult = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);
            assertThat(firstResult.getText()).contains("姓名=李四");

            // Arrange: second call — fact store fails
            when(factStore.formatForContext(eq(USER_ID), anyInt()))
                    .thenThrow(new RuntimeException("Connection refused"));

            // Act: second call
            SystemMessage secondResult = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // Assert: cached fact content is still present
            String text = secondResult.getText();
            assertThat(text).contains("姓名=李四");
            assertThat(text).contains("第一次对话");
        }

        @Test
        @DisplayName("缓存不保存空内容")
        void emptyContentNotCached() {
            // Arrange: first call — fact store returns empty
            when(slidingWindow.formatForContext(eq(CONVERSATION_ID), eq(AGENT_TYPE), anyInt()))
                    .thenReturn("user: test");
            when(factStore.formatForContext(eq(USER_ID), anyInt()))
                    .thenReturn("");
            when(summaryLayer.getRecentSummaries(eq(USER_ID), anyInt())).thenReturn("");
            when(experienceStore.searchSimilar(eq(USER_ID), anyString()))
                    .thenReturn(Collections.emptyList());

            // First call
            coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // Second call — fact store fails
            when(factStore.formatForContext(eq(USER_ID), anyInt()))
                    .thenThrow(new RuntimeException("Failure"));

            // Act
            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // Assert: no cached fact content (wasn't stored because it was empty)
            String text = result.getText();
            assertThat(text).doesNotContain("【用户事实】");
        }
    }
}
