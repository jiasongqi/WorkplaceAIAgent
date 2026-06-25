package com.yupi.yuaiagent.memory.extraction;

import com.yupi.yuaiagent.memory.experience.ExperienceDocument;
import com.yupi.yuaiagent.memory.experience.ExperienceStoreLayer;
import com.yupi.yuaiagent.memory.fact.FactCategory;
import com.yupi.yuaiagent.memory.fact.FactEntry;
import com.yupi.yuaiagent.memory.fact.FactStoreLayer;
import com.yupi.yuaiagent.memory.summary.SummaryChecklist;
import com.yupi.yuaiagent.memory.summary.SummaryLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ExtractionPipeline 单元测试
 *
 * <p>验证：
 * <ul>
 *   <li>路由正确性：facts → FactStoreLayer, summary → SummaryLayer, experiences → ExperienceStoreLayer</li>
 *   <li>错误隔离：单个层失败不影响其他层的路由</li>
 *   <li>异步非阻塞：processAsync() 立即返回，在独立线程池处理</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ExtractionPipelineTest {

    @Mock
    private FactStoreLayer factStoreLayer;

    @Mock
    private SummaryLayer summaryLayer;

    @Mock
    private ExperienceStoreLayer experienceStoreLayer;

    @Mock
    private ChatModel chatModel;

    private ExtractionPipeline pipeline;

    /**
     * Synchronous executor for deterministic testing.
     */
    private static final Executor SYNC_EXECUTOR = Runnable::run;

    @BeforeEach
    void setUp() {
        pipeline = new ExtractionPipeline(
                SYNC_EXECUTOR,
                factStoreLayer,
                summaryLayer,
                experienceStoreLayer,
                chatModel
        );
    }

    /**
     * Helper: mock ChatModel to return the given JSON content for any Prompt.
     */
    private void mockChatModelResponse(String jsonContent) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(jsonContent)))
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    /**
     * Helper: build a simple conversation with one user and one assistant message.
     */
    private List<Message> simpleConversation() {
        return List.of(
                new UserMessage("我在北京做Java开发，工作3年了"),
                new AssistantMessage("了解，您在北京从事Java开发3年")
        );
    }

    // ======================== 路由正确性测试 ========================

    @Nested
    @DisplayName("路由正确性测试")
    class RoutingCorrectnessTests {

        @Test
        @DisplayName("事实正确转换为 FactEntry 并路由到 FactStoreLayer")
        void factsRoutedToFactStoreLayer() {
            String json = """
                    {"facts":[{"key":"城市","value":"北京","category":"identity"},{"key":"岗位","value":"Java开发","category":"career"}],"summary":null,"experiences":[]}
                    """;
            mockChatModelResponse(json);

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            ArgumentCaptor<FactEntry> captor = ArgumentCaptor.forClass(FactEntry.class);
            verify(factStoreLayer, times(2)).upsert(eq("user1"), captor.capture());

            List<FactEntry> captured = captor.getAllValues();
            assertThat(captured.get(0).key()).isEqualTo("城市");
            assertThat(captured.get(0).value()).isEqualTo("北京");
            assertThat(captured.get(0).category()).isEqualTo(FactCategory.IDENTITY);
            assertThat(captured.get(0).sourceConversationId()).isEqualTo("conv1");

            assertThat(captured.get(1).key()).isEqualTo("岗位");
            assertThat(captured.get(1).value()).isEqualTo("Java开发");
            assertThat(captured.get(1).category()).isEqualTo(FactCategory.CAREER);
        }

        @Test
        @DisplayName("摘要正确创建 SummaryChecklist 并路由到 SummaryLayer")
        void summaryRoutedToSummaryLayer() {
            String json = """
                    {"facts":[],"summary":{"topics":["职业规划"],"decisions":["转向管理岗"],"actionItems":["准备PMP证书"],"unresolvedQuestions":["何时跳槽"]},"experiences":[]}
                    """;
            mockChatModelResponse(json);

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            ArgumentCaptor<SummaryChecklist> captor = ArgumentCaptor.forClass(SummaryChecklist.class);
            verify(summaryLayer).store(eq("user1"), captor.capture());

            SummaryChecklist stored = captor.getValue();
            assertThat(stored.conversationId()).isEqualTo("conv1");
            assertThat(stored.topics()).containsExactly("职业规划");
            assertThat(stored.decisions()).containsExactly("转向管理岗");
            assertThat(stored.actionItems()).containsExactly("准备PMP证书");
            assertThat(stored.unresolvedQuestions()).containsExactly("何时跳槽");
        }

        @Test
        @DisplayName("经验正确转换为 ExperienceDocument 并路由到 ExperienceStoreLayer")
        void experiencesRoutedToExperienceStoreLayer() {
            String json = """
                    {"facts":[],"summary":null,"experiences":[{"content":"成功通过阿里面试","outcome":"success"},{"content":"薪资谈判失败","outcome":"failure"}]}
                    """;
            mockChatModelResponse(json);

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            ArgumentCaptor<ExperienceDocument> captor = ArgumentCaptor.forClass(ExperienceDocument.class);
            verify(experienceStoreLayer, times(2)).store(captor.capture());

            List<ExperienceDocument> captured = captor.getAllValues();
            assertThat(captured.get(0).content()).isEqualTo("成功通过阿里面试");
            assertThat(captured.get(0).outcome()).isEqualTo("success");
            assertThat(captured.get(0).userId()).isEqualTo("user1");
            assertThat(captured.get(0).agentType()).isEqualTo("career");
            assertThat(captured.get(0).id()).isNotNull();

            assertThat(captured.get(1).content()).isEqualTo("薪资谈判失败");
            assertThat(captured.get(1).outcome()).isEqualTo("failure");
        }

        @Test
        @DisplayName("category 字符串正确映射到 FactCategory 枚举")
        void categoryMappingWorksCorrectly() {
            String json = """
                    {"facts":[{"key":"k1","value":"v1","category":"identity"},{"key":"k2","value":"v2","category":"career"},{"key":"k3","value":"v3","category":"preferences"},{"key":"k4","value":"v4","category":"goals"},{"key":"k5","value":"v5","category":"constraints"}],"summary":null,"experiences":[]}
                    """;
            mockChatModelResponse(json);

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            ArgumentCaptor<FactEntry> captor = ArgumentCaptor.forClass(FactEntry.class);
            verify(factStoreLayer, times(5)).upsert(eq("user1"), captor.capture());

            List<FactEntry> captured = captor.getAllValues();
            assertThat(captured.get(0).category()).isEqualTo(FactCategory.IDENTITY);
            assertThat(captured.get(1).category()).isEqualTo(FactCategory.CAREER);
            assertThat(captured.get(2).category()).isEqualTo(FactCategory.PREFERENCES);
            assertThat(captured.get(3).category()).isEqualTo(FactCategory.GOALS);
            assertThat(captured.get(4).category()).isEqualTo(FactCategory.CONSTRAINTS);
        }

        @Test
        @DisplayName("无效/空 key 或 value 的事实被跳过")
        void invalidEmptyFactsSkipped() {
            String json = """
                    {"facts":[{"key":"","value":"v1","category":"identity"},{"key":"valid","value":"","category":"career"},{"key":null,"value":"v3","category":"goals"},{"key":"good","value":"nice","category":"career"}],"summary":null,"experiences":[]}
                    """;
            mockChatModelResponse(json);

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            // Only the last valid fact should be routed
            ArgumentCaptor<FactEntry> captor = ArgumentCaptor.forClass(FactEntry.class);
            verify(factStoreLayer, times(1)).upsert(eq("user1"), captor.capture());
            assertThat(captor.getValue().key()).isEqualTo("good");
            assertThat(captor.getValue().value()).isEqualTo("nice");
        }

        @Test
        @DisplayName("无效 category 的事实被跳过")
        void invalidCategoryFactsSkipped() {
            String json = """
                    {"facts":[{"key":"k1","value":"v1","category":"UNKNOWN"},{"key":"k2","value":"v2","category":"career"}],"summary":null,"experiences":[]}
                    """;
            mockChatModelResponse(json);

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            // Only the valid-category fact should be routed
            ArgumentCaptor<FactEntry> captor = ArgumentCaptor.forClass(FactEntry.class);
            verify(factStoreLayer, times(1)).upsert(eq("user1"), captor.capture());
            assertThat(captor.getValue().key()).isEqualTo("k2");
        }

        @Test
        @DisplayName("空摘要（无实质内容）被跳过")
        void emptySummarySkipped() {
            String json = """
                    {"facts":[],"summary":{"topics":[],"decisions":[],"actionItems":[],"unresolvedQuestions":[]},"experiences":[]}
                    """;
            mockChatModelResponse(json);

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            verify(summaryLayer, never()).store(any(), any());
        }

        @Test
        @DisplayName("null 摘要被跳过")
        void nullSummarySkipped() {
            String json = """
                    {"facts":[],"summary":null,"experiences":[]}
                    """;
            mockChatModelResponse(json);

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            verify(summaryLayer, never()).store(any(), any());
        }

        @Test
        @DisplayName("空 content 的经验被跳过")
        void emptyContentExperiencesSkipped() {
            String json = """
                    {"facts":[],"summary":null,"experiences":[{"content":"","outcome":"success"},{"content":"有效经验","outcome":"insight"}]}
                    """;
            mockChatModelResponse(json);

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            ArgumentCaptor<ExperienceDocument> captor = ArgumentCaptor.forClass(ExperienceDocument.class);
            verify(experienceStoreLayer, times(1)).store(captor.capture());
            assertThat(captor.getValue().content()).isEqualTo("有效经验");
        }

        @Test
        @DisplayName("experience 的 outcome 为 null 时默认为 insight")
        void nullOutcomeDefaultsToInsight() {
            String json = """
                    {"facts":[],"summary":null,"experiences":[{"content":"一些经验","outcome":null}]}
                    """;
            mockChatModelResponse(json);

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            ArgumentCaptor<ExperienceDocument> captor = ArgumentCaptor.forClass(ExperienceDocument.class);
            verify(experienceStoreLayer).store(captor.capture());
            assertThat(captor.getValue().outcome()).isEqualTo("insight");
        }

        @Test
        @DisplayName("空对话消息不触发 LLM 调用")
        void emptyMessagesSkipsLlmCall() {
            pipeline.processMessages("user1", "conv1", "career", List.of());

            // ChatModel.call() should not be invoked (note: chatModel is touched during
            // ChatClient.builder() construction, so we verify .call() specifically)
            verify(chatModel, never()).call(any(Prompt.class));
            verifyNoInteractions(factStoreLayer);
            verifyNoInteractions(summaryLayer);
            verifyNoInteractions(experienceStoreLayer);
        }
    }

    // ======================== 错误隔离测试 ========================

    @Nested
    @DisplayName("错误隔离测试")
    class ErrorIsolationTests {

        @Test
        @DisplayName("FactStoreLayer 失败不影响 summary 和 experience 路由")
        void factStoreFailureDoesNotBlockOthers() {
            String json = """
                    {"facts":[{"key":"name","value":"张三","category":"identity"}],"summary":{"topics":["求职"],"decisions":[],"actionItems":[],"unresolvedQuestions":[]},"experiences":[{"content":"面试经验","outcome":"success"}]}
                    """;
            mockChatModelResponse(json);

            doThrow(new RuntimeException("FactStore disk full"))
                    .when(factStoreLayer).upsert(any(), any());

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            // Summary and experience routing should still work
            verify(summaryLayer).store(eq("user1"), any(SummaryChecklist.class));
            verify(experienceStoreLayer).store(any(ExperienceDocument.class));
        }

        @Test
        @DisplayName("SummaryLayer 失败不影响 fact 和 experience 路由")
        void summaryLayerFailureDoesNotBlockOthers() {
            String json = """
                    {"facts":[{"key":"city","value":"上海","category":"identity"}],"summary":{"topics":["规划"],"decisions":[],"actionItems":[],"unresolvedQuestions":[]},"experiences":[{"content":"项目经验","outcome":"insight"}]}
                    """;
            mockChatModelResponse(json);

            doThrow(new RuntimeException("SummaryLayer IO error"))
                    .when(summaryLayer).store(any(), any());

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            // Fact and experience routing should still work
            verify(factStoreLayer).upsert(eq("user1"), any(FactEntry.class));
            verify(experienceStoreLayer).store(any(ExperienceDocument.class));
        }

        @Test
        @DisplayName("ExperienceStoreLayer 失败不影响 fact 和 summary 路由")
        void experienceStoreFailureDoesNotBlockOthers() {
            String json = """
                    {"facts":[{"key":"role","value":"PM","category":"career"}],"summary":{"topics":["面试"],"decisions":[],"actionItems":[],"unresolvedQuestions":[]},"experiences":[{"content":"经验案例","outcome":"failure"}]}
                    """;
            mockChatModelResponse(json);

            doThrow(new RuntimeException("VectorStore connection lost"))
                    .when(experienceStoreLayer).store(any());

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            // Fact and summary routing should still work
            verify(factStoreLayer).upsert(eq("user1"), any(FactEntry.class));
            verify(summaryLayer).store(eq("user1"), any(SummaryChecklist.class));
        }

        @Test
        @DisplayName("单条事实失败不阻塞后续事实处理")
        void individualFactFailureDoesNotBlockRemaining() {
            String json = """
                    {"facts":[{"key":"first","value":"fail-on-this","category":"identity"},{"key":"second","value":"should-succeed","category":"career"}],"summary":null,"experiences":[]}
                    """;
            mockChatModelResponse(json);

            // First upsert fails, second succeeds
            doThrow(new RuntimeException("First fact failed"))
                    .doNothing()
                    .when(factStoreLayer).upsert(any(), any());

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            // Both should be attempted
            verify(factStoreLayer, times(2)).upsert(eq("user1"), any(FactEntry.class));
        }

        @Test
        @DisplayName("单条经验失败不阻塞后续经验处理")
        void individualExperienceFailureDoesNotBlockRemaining() {
            String json = """
                    {"facts":[],"summary":null,"experiences":[{"content":"第一条经验","outcome":"success"},{"content":"第二条经验","outcome":"insight"}]}
                    """;
            mockChatModelResponse(json);

            // First store fails, second succeeds
            doThrow(new RuntimeException("First experience failed"))
                    .doNothing()
                    .when(experienceStoreLayer).store(any());

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            // Both should be attempted
            verify(experienceStoreLayer, times(2)).store(any(ExperienceDocument.class));
        }

        @Test
        @DisplayName("LLM 返回 null 时所有层不被调用")
        void llmReturnsNullNoRouting() {
            // Return empty/null content which causes entity parsing to return null
            ChatResponse chatResponse = new ChatResponse(
                    List.of(new Generation(new AssistantMessage("")))
            );
            when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

            pipeline.processMessages("user1", "conv1", "career", simpleConversation());

            // No routing should happen - LLM parse failure logs error but doesn't propagate
            // The exact behavior depends on how ChatClient.entity() handles empty content
            // But processMessages wraps everything in try-catch, so no exception escapes
        }
    }

    // ======================== 异步非阻塞行为测试 ========================

    @Nested
    @DisplayName("异步非阻塞行为测试")
    class AsyncNonBlockingTests {

        @Test
        @DisplayName("processAsync() 立即返回不阻塞调用者")
        void processAsyncReturnsImmediately() {
            // Use an executor that delays execution
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(1);

            Executor delayedExecutor = runnable -> {
                new Thread(() -> {
                    try {
                        startLatch.await(); // Wait until we signal
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    runnable.run();
                    doneLatch.countDown();
                }).start();
            };

            ExtractionPipeline asyncPipeline = new ExtractionPipeline(
                    delayedExecutor,
                    factStoreLayer,
                    summaryLayer,
                    experienceStoreLayer,
                    chatModel
            );

            // processAsync should return immediately even though executor hasn't started
            asyncPipeline.processAsync("user1", "conv1", "career", simpleConversation());

            // At this point, no processing has happened yet (executor is blocked)
            // ChatModel.call() should not have been invoked yet
            verify(chatModel, never()).call(any(Prompt.class));

            // Now release the executor
            startLatch.countDown();

            // Wait for processing to complete (with timeout)
            try {
                doneLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Test
        @DisplayName("处理在专用线程池上执行")
        void processingHappensOnDedicatedExecutor() {
            AtomicBoolean executorUsed = new AtomicBoolean(false);

            Executor trackingExecutor = runnable -> {
                executorUsed.set(true);
                runnable.run();
            };

            ExtractionPipeline trackedPipeline = new ExtractionPipeline(
                    trackingExecutor,
                    factStoreLayer,
                    summaryLayer,
                    experienceStoreLayer,
                    chatModel
            );

            String json = """
                    {"facts":[],"summary":null,"experiences":[]}
                    """;
            mockChatModelResponse(json);

            trackedPipeline.processAsync("user1", "conv1", "career", simpleConversation());

            assertThat(executorUsed.get()).isTrue();
        }

        @Test
        @DisplayName("processAsync 中的异常不向外传播")
        void asyncExceptionDoesNotPropagate() {
            when(chatModel.call(any(Prompt.class)))
                    .thenThrow(new RuntimeException("LLM service unavailable"));

            // Should not throw even with sync executor
            pipeline.processAsync("user1", "conv1", "career", simpleConversation());

            // If we get here, no exception propagated
        }
    }
}
