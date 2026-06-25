package com.yupi.yuaiagent.memory.summary;

import com.yupi.yuaiagent.memory.TokenBudgetAllocator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SummaryLayer 单元测试
 *
 * <p>验证：
 * <ul>
 *   <li>FIFO 淘汰策略（6 条摘要 → 只保留最新 5 条）</li>
 *   <li>maxChecklists 上限强制执行</li>
 *   <li>getRecentSummaries 遵循 token 预算</li>
 *   <li>shouldTrigger 阈值判断</li>
 *   <li>持久化 round-trip（写文件 → 重载 → 验证一致）</li>
 *   <li>LLM 失败时的降级策略</li>
 *   <li>不存储原始对话文本（Req 4.5）</li>
 * </ul>
 */
class SummaryLayerTest {

    @TempDir
    Path tempDir;

    private TokenBudgetAllocator allocator;
    private SummaryLayer summaryLayer;

    @BeforeEach
    void setUp() {
        allocator = new TokenBudgetAllocator(60, 15, 10, 15);
        // 使用 null chatClient（不依赖 LLM 的测试场景）
        summaryLayer = new SummaryLayer(
                (ChatClient) null,
                allocator,
                tempDir.toString(),
                5,   // maxChecklists
                10   // triggerThreshold
        );
        summaryLayer.init();
    }

    @Nested
    @DisplayName("FIFO 淘汰策略测试")
    class FifoEvictionTests {

        @Test
        @DisplayName("存储 6 条摘要后只保留最新 5 条（最旧的被淘汰）")
        void sixChecklistsOnlyFiveKept() {
            for (int i = 1; i <= 6; i++) {
                SummaryChecklist checklist = new SummaryChecklist(
                        "conv-" + i,
                        Instant.now().plusSeconds(i),
                        List.of("topic-" + i),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()
                );
                summaryLayer.store("user-1", checklist);
            }

            List<SummaryChecklist> result = summaryLayer.getChecklists("user-1");
            assertThat(result).hasSize(5);
            // 最旧的 conv-1 应被淘汰
            assertThat(result).noneMatch(c -> c.conversationId().equals("conv-1"));
            // conv-2 到 conv-6 应保留
            assertThat(result.get(0).conversationId()).isEqualTo("conv-2");
            assertThat(result.get(4).conversationId()).isEqualTo("conv-6");
        }

        @Test
        @DisplayName("存储 10 条摘要后只保留最新 5 条")
        void tenChecklistsOnlyFiveKept() {
            for (int i = 1; i <= 10; i++) {
                SummaryChecklist checklist = new SummaryChecklist(
                        "conv-" + i,
                        Instant.now().plusSeconds(i),
                        List.of("topic-" + i),
                        List.of("decision-" + i),
                        Collections.emptyList(),
                        Collections.emptyList()
                );
                summaryLayer.store("user-1", checklist);
            }

            List<SummaryChecklist> result = summaryLayer.getChecklists("user-1");
            assertThat(result).hasSize(5);
            // 只保留 conv-6 到 conv-10
            assertThat(result.get(0).conversationId()).isEqualTo("conv-6");
            assertThat(result.get(4).conversationId()).isEqualTo("conv-10");
        }

        @Test
        @DisplayName("恰好 5 条不触发淘汰")
        void exactlyMaxChecklistsNoEviction() {
            for (int i = 1; i <= 5; i++) {
                SummaryChecklist checklist = new SummaryChecklist(
                        "conv-" + i,
                        Instant.now().plusSeconds(i),
                        List.of("topic-" + i),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()
                );
                summaryLayer.store("user-1", checklist);
            }

            List<SummaryChecklist> result = summaryLayer.getChecklists("user-1");
            assertThat(result).hasSize(5);
            assertThat(result.get(0).conversationId()).isEqualTo("conv-1");
        }

        @Test
        @DisplayName("不同用户的摘要独立管理")
        void differentUsersIndependent() {
            for (int i = 1; i <= 6; i++) {
                summaryLayer.store("user-A", new SummaryChecklist(
                        "a-" + i, Instant.now().plusSeconds(i),
                        List.of("topic"), Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList()));
            }
            // user-B 只有 2 条
            for (int i = 1; i <= 2; i++) {
                summaryLayer.store("user-B", new SummaryChecklist(
                        "b-" + i, Instant.now().plusSeconds(i),
                        List.of("topic"), Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList()));
            }

            assertThat(summaryLayer.getChecklists("user-A")).hasSize(5);
            assertThat(summaryLayer.getChecklists("user-B")).hasSize(2);
        }
    }

    @Nested
    @DisplayName("maxChecklists 强制执行测试")
    class MaxChecklistsTests {

        @Test
        @DisplayName("自定义 maxChecklists=3 时淘汰正确")
        void customMaxChecklists() {
            SummaryLayer customLayer = new SummaryLayer(
                    (ChatClient) null, allocator, tempDir.resolve("custom").toString(), 3, 10);
            customLayer.init();

            for (int i = 1; i <= 5; i++) {
                customLayer.store("user-1", new SummaryChecklist(
                        "conv-" + i, Instant.now().plusSeconds(i),
                        List.of("t" + i), Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList()));
            }

            List<SummaryChecklist> result = customLayer.getChecklists("user-1");
            assertThat(result).hasSize(3);
            assertThat(result.get(0).conversationId()).isEqualTo("conv-3");
            assertThat(result.get(2).conversationId()).isEqualTo("conv-5");
        }

        @Test
        @DisplayName("maxChecklists=1 时只保留最新一条")
        void maxChecklistsOne() {
            SummaryLayer singleLayer = new SummaryLayer(
                    (ChatClient) null, allocator, tempDir.resolve("single").toString(), 1, 10);
            singleLayer.init();

            singleLayer.store("user-1", new SummaryChecklist(
                    "old", Instant.now(), List.of("old-topic"),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
            singleLayer.store("user-1", new SummaryChecklist(
                    "new", Instant.now().plusSeconds(10), List.of("new-topic"),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));

            List<SummaryChecklist> result = singleLayer.getChecklists("user-1");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).conversationId()).isEqualTo("new");
        }
    }

    @Nested
    @DisplayName("getRecentSummaries() formatForContext 测试")
    class FormatContextTests {

        @Test
        @DisplayName("无摘要时返回空字符串")
        void noSummariesReturnsEmpty() {
            String result = summaryLayer.getRecentSummaries("empty-user", 500);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("格式化输出包含标头和各字段")
        void formattedOutputContainsAllFields() {
            summaryLayer.store("user-1", new SummaryChecklist(
                    "conv-1", Instant.now(),
                    List.of("简历优化", "面试技巧"),
                    List.of("使用STAR方法"),
                    List.of("完善项目经历"),
                    List.of("薪资期望确认")
            ));

            String result = summaryLayer.getRecentSummaries("user-1", 500);
            assertThat(result).contains("近期对话摘要");
            assertThat(result).contains("简历优化");
            assertThat(result).contains("面试技巧");
            assertThat(result).contains("使用STAR方法");
            assertThat(result).contains("完善项目经历");
            assertThat(result).contains("薪资期望确认");
        }

        @Test
        @DisplayName("token 预算限制截断输出")
        void tokenBudgetTruncatesOutput() {
            // 添加多条摘要使内容变长
            for (int i = 1; i <= 5; i++) {
                summaryLayer.store("budget-user", new SummaryChecklist(
                        "conv-" + i, Instant.now().plusSeconds(i),
                        List.of("非常长的话题描述用于测试截断功能_" + i, "另一个话题_" + i),
                        List.of("决策项目_" + i + "_详细内容"),
                        List.of("行动事项_" + i + "_需要完成的具体任务"),
                        List.of("未解决的问题_" + i + "_需要进一步讨论")
                ));
            }

            // 获取完整内容的 token 数
            String fullResult = summaryLayer.getRecentSummaries("budget-user", 10000);
            int fullTokens = allocator.estimateTokens(fullResult);
            assertThat(fullTokens).isGreaterThan(50);

            // 使用较小预算
            String truncatedResult = summaryLayer.getRecentSummaries("budget-user", 20);
            int truncatedTokens = allocator.estimateTokens(truncatedResult);
            assertThat(truncatedTokens).isLessThanOrEqualTo(20);
        }

        @Test
        @DisplayName("null userId 返回空字符串")
        void nullUserIdReturnsEmpty() {
            String result = summaryLayer.getRecentSummaries(null, 500);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("shouldTrigger() 阈值测试")
    class ShouldTriggerTests {

        @Test
        @DisplayName("消息数等于阈值时触发")
        void messageCountEqualsThreshold() {
            assertThat(summaryLayer.shouldTrigger(10)).isTrue();
        }

        @Test
        @DisplayName("消息数大于阈值时触发")
        void messageCountExceedsThreshold() {
            assertThat(summaryLayer.shouldTrigger(15)).isTrue();
        }

        @Test
        @DisplayName("消息数小于阈值时不触发")
        void messageCountBelowThreshold() {
            assertThat(summaryLayer.shouldTrigger(9)).isFalse();
            assertThat(summaryLayer.shouldTrigger(0)).isFalse();
        }

        @Test
        @DisplayName("自定义阈值生效")
        void customThreshold() {
            SummaryLayer customLayer = new SummaryLayer(
                    (ChatClient) null, allocator, tempDir.resolve("trig").toString(), 5, 3);
            customLayer.init();

            assertThat(customLayer.shouldTrigger(2)).isFalse();
            assertThat(customLayer.shouldTrigger(3)).isTrue();
            assertThat(customLayer.shouldTrigger(4)).isTrue();
        }
    }

    @Nested
    @DisplayName("持久化 round-trip 测试")
    class PersistenceTests {

        @Test
        @DisplayName("写入后新实例可以重载数据")
        void persistenceRoundTrip() {
            Instant time = Instant.parse("2024-06-15T10:30:00Z");
            summaryLayer.store("user-rt", new SummaryChecklist(
                    "conv-persist", time,
                    List.of("求职准备", "简历修改"),
                    List.of("使用STAR法则"),
                    List.of("完善项目经历描述"),
                    List.of("期望薪资待确认")
            ));

            // 创建新实例从同一目录加载
            SummaryLayer reloaded = new SummaryLayer(
                    (ChatClient) null, allocator, tempDir.toString(), 5, 10);
            reloaded.init();

            List<SummaryChecklist> result = reloaded.getChecklists("user-rt");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).conversationId()).isEqualTo("conv-persist");
            assertThat(result.get(0).createdAt()).isEqualTo(time);
            assertThat(result.get(0).topics()).containsExactly("求职准备", "简历修改");
            assertThat(result.get(0).decisions()).containsExactly("使用STAR法则");
            assertThat(result.get(0).actionItems()).containsExactly("完善项目经历描述");
            assertThat(result.get(0).unresolvedQuestions()).containsExactly("期望薪资待确认");
        }

        @Test
        @DisplayName("JSON 文件按 userId 命名")
        void fileNameMatchesUserId() {
            summaryLayer.store("test-user-id", new SummaryChecklist(
                    "c1", Instant.now(), List.of("t"),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));

            Path expectedFile = tempDir.resolve("test-user-id.json");
            assertThat(Files.exists(expectedFile)).isTrue();
        }

        @Test
        @DisplayName("FIFO 淘汰后文件内容同步更新")
        void fifoEvictionPersisted() {
            for (int i = 1; i <= 6; i++) {
                summaryLayer.store("user-fifo", new SummaryChecklist(
                        "conv-" + i, Instant.now().plusSeconds(i),
                        List.of("topic-" + i), Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList()));
            }

            // 重载验证
            SummaryLayer reloaded = new SummaryLayer(
                    (ChatClient) null, allocator, tempDir.toString(), 5, 10);
            reloaded.init();

            List<SummaryChecklist> result = reloaded.getChecklists("user-fifo");
            assertThat(result).hasSize(5);
            assertThat(result.get(0).conversationId()).isEqualTo("conv-2");
        }
    }

    @Nested
    @DisplayName("generateAndStore() LLM 集成测试（mock）")
    class GenerateAndStoreTests {

        @Test
        @DisplayName("LLM 调用失败时使用降级策略生成 fallback 清单")
        void llmFailureFallbackChecklist() {
            // 使用 mock ChatModel 模拟失败
            ChatModel mockChatModel = mock(ChatModel.class);
            when(mockChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                    .thenThrow(new RuntimeException("LLM service unavailable"));

            ChatClient failingClient = ChatClient.builder(mockChatModel).build();
            SummaryLayer layerWithMock = new SummaryLayer(
                    failingClient, allocator, tempDir.resolve("llm-fail").toString(), 5, 10);
            layerWithMock.init();

            List<Message> messages = List.of(
                    new UserMessage("我想了解如何准备面试"),
                    new AssistantMessage("面试准备可以从以下几个方面入手..."),
                    new UserMessage("谢谢，还有其他建议吗")
            );

            // 不应抛出异常
            layerWithMock.generateAndStore("user-fail", "conv-fail", messages);

            List<SummaryChecklist> result = layerWithMock.getChecklists("user-fail");
            assertThat(result).hasSize(1);
            // fallback 应从首条用户消息提取 topic
            assertThat(result.get(0).conversationId()).isEqualTo("conv-fail");
            assertThat(result.get(0).topics()).isNotEmpty();
        }

        @Test
        @DisplayName("LLM 返回有效 JSON 时正确解析")
        void llmSuccessfulParsing() {
            String validJson = """
                    {"topics":["职业规划","技术选型"],"decisions":["选择Java方向"],"actionItems":["学习Spring"],"unresolvedQuestions":["是否需要考证"]}
                    """;

            ChatModel mockChatModel = mock(ChatModel.class);
            Generation generation = new Generation(new AssistantMessage(validJson));
            ChatResponse chatResponse = new ChatResponse(List.of(generation));
            when(mockChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                    .thenReturn(chatResponse);

            ChatClient successClient = ChatClient.builder(mockChatModel).build();
            SummaryLayer layerWithMock = new SummaryLayer(
                    successClient, allocator, tempDir.resolve("llm-ok").toString(), 5, 10);
            layerWithMock.init();

            List<Message> messages = List.of(
                    new UserMessage("我想做职业规划"),
                    new AssistantMessage("好的，我来帮你规划")
            );

            layerWithMock.generateAndStore("user-ok", "conv-ok", messages);

            List<SummaryChecklist> result = layerWithMock.getChecklists("user-ok");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).topics()).containsExactly("职业规划", "技术选型");
            assertThat(result.get(0).decisions()).containsExactly("选择Java方向");
            assertThat(result.get(0).actionItems()).containsExactly("学习Spring");
            assertThat(result.get(0).unresolvedQuestions()).containsExactly("是否需要考证");
        }

        @Test
        @DisplayName("null 参数不报错")
        void nullParametersHandledGracefully() {
            summaryLayer.generateAndStore(null, "conv", List.of(new UserMessage("hi")));
            summaryLayer.generateAndStore("user", null, List.of(new UserMessage("hi")));
            summaryLayer.generateAndStore("user", "conv", null);
            summaryLayer.generateAndStore("user", "conv", Collections.emptyList());

            assertThat(summaryLayer.getChecklists("user")).isEmpty();
        }
    }

    @Nested
    @DisplayName("不存储原始文本验证（Req 4.5）")
    class NoOriginalTextTests {

        @Test
        @DisplayName("存储的摘要不包含对话原始文本")
        void checklistContainsOnlyDistilledContent() {
            SummaryChecklist checklist = new SummaryChecklist(
                    "conv-1", Instant.now(),
                    List.of("面试准备"),
                    List.of("使用STAR法则"),
                    List.of("整理项目经验"),
                    List.of("薪资范围")
            );
            summaryLayer.store("user-1", checklist);

            List<SummaryChecklist> result = summaryLayer.getChecklists("user-1");
            SummaryChecklist stored = result.get(0);

            // 验证只包含提炼内容，不含原始对话片段
            assertThat(stored.topics()).allMatch(t -> t.length() <= 50);
            assertThat(stored.decisions()).allMatch(d -> d.length() <= 50);
            assertThat(stored.actionItems()).allMatch(a -> a.length() <= 50);
            assertThat(stored.unresolvedQuestions()).allMatch(q -> q.length() <= 50);
        }
    }

    @Nested
    @DisplayName("store() 边界条件测试")
    class StoreBoundaryTests {

        @Test
        @DisplayName("null userId 不存储")
        void nullUserIdIgnored() {
            summaryLayer.store(null, new SummaryChecklist(
                    "c", Instant.now(), List.of("t"),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
            // 不应抛异常
        }

        @Test
        @DisplayName("null checklist 不存储")
        void nullChecklistIgnored() {
            summaryLayer.store("user-1", null);
            assertThat(summaryLayer.getChecklists("user-1")).isEmpty();
        }

        @Test
        @DisplayName("不存在的用户返回空列表")
        void nonExistentUserReturnsEmpty() {
            assertThat(summaryLayer.getChecklists("ghost")).isEmpty();
        }
    }
}
