package com.yupi.yuaiagent.memory;

import com.yupi.yuaiagent.memory.experience.ExperienceStoreLayer;
import com.yupi.yuaiagent.memory.extraction.ExtractionPipeline;
import com.yupi.yuaiagent.memory.fact.FactCategory;
import com.yupi.yuaiagent.memory.fact.FactEntry;
import com.yupi.yuaiagent.memory.fact.FactStoreLayer;
import com.yupi.yuaiagent.memory.sliding.SlidingWindowLayer;
import com.yupi.yuaiagent.memory.summary.SummaryChecklist;
import com.yupi.yuaiagent.memory.summary.SummaryLayer;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.repository.entity.UserFactEntity;
import com.yupi.yuaiagent.repository.jpa.UserFactJpaRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 端到端集成测试 — 验证 MemoryCoordinator 在贴近真实场景下的上下文组装。
 *
 * <p>测试策略：
 * <ul>
 *   <li>使用真实的 {@link TokenBudgetAllocator}、{@link FactStoreLayer}（临时目录）、
 *       {@link SummaryLayer}（null ChatClient，直接 store）</li>
 *   <li>Mock {@link SlidingWindowLayer}（依赖 ChatMemoryManager，构建成本高）</li>
 *   <li>Mock {@link ExperienceStoreLayer}（依赖 VectorStore）</li>
 *   <li>Mock {@link ExtractionPipeline}（验证 onTurnCompleted 触发行为）</li>
 * </ul>
 *
 * <p>这不是 Spring Boot 集成测试（无 @SpringBootTest），而是手动组装真实组件的"组件集成测试"。
 */
@ExtendWith(MockitoExtension.class)
class MemoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Mock
    private SlidingWindowLayer slidingWindowLayer;

    @Mock
    private ExperienceStoreLayer experienceStoreLayer;

    @Mock
    private ExtractionPipeline extractionPipeline;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private ChatModel mockChatModel;

    @Mock
    private UserFactJpaRepository userFactJpaRepository;

    /** In-memory simulation of the JPA repo, mirroring {@code FactStoreLayerTest}. */
    private final Map<String, List<UserFactEntity>> factStoreBackingMap = new HashMap<>();

    private TokenBudgetAllocator budgetAllocator;
    private FactStoreLayer factStoreLayer;
    private SummaryLayer summaryLayer;
    private MemoryCoordinator coordinator;

    private static final String USER_ID = "integration-user-001";
    private static final String CONVERSATION_ID = "conv-integration-001";
    private static final String AGENT_TYPE = "general";
    private static final int TIMEOUT_MS = 2000;
    private static final int TOTAL_BUDGET = 6000;

    @BeforeEach
    void setUp() throws Exception {
        // Real TokenBudgetAllocator (L1=60%, L2=15%, L3=10%, L4=15%)
        budgetAllocator = new TokenBudgetAllocator(60, 15, 10, 15);

        // Real FactStoreLayer backed by a mocked JPA repo (in-memory map simulation)
        lenient().when(userProfileService.get(anyString())).thenReturn(Optional.empty());
        setupJpaMock();
        factStoreLayer = new FactStoreLayer(
                userFactJpaRepository,
                budgetAllocator,
                userProfileService
        );
        invokeInit(factStoreLayer);

        // Real SummaryLayer with mocked ChatModel (we use store() directly, never call LLM)
        summaryLayer = new SummaryLayer(
                mockChatModel,
                budgetAllocator,
                tempDir.resolve("summaries").toString(),
                5,   // maxChecklists
                10   // triggerThreshold
        );
        invokeInit(summaryLayer);

        // Wire together with mocked external dependencies
        Executor directExecutor = Runnable::run;
        coordinator = new MemoryCoordinator(
                slidingWindowLayer,
                factStoreLayer,
                summaryLayer,
                experienceStoreLayer,
                budgetAllocator,
                extractionPipeline,
                directExecutor,
                TIMEOUT_MS,
                TOTAL_BUDGET
        );
    }

    /**
     * Invoke the package-private @PostConstruct init() method via reflection.
     * Needed because FactStoreLayer and SummaryLayer are in sub-packages.
     */
    private void invokeInit(Object component) throws Exception {
        Method initMethod = component.getClass().getDeclaredMethod("init");
        initMethod.setAccessible(true);
        initMethod.invoke(component);
    }

    /**
     * Simulates JPA persistence for {@link FactStoreLayer} via an in-memory map,
     * mirroring the approach used in {@code FactStoreLayerTest}.
     */
    private void setupJpaMock() {
        lenient().when(userFactJpaRepository.findByUserId(anyString())).thenAnswer(inv -> {
            String userId = inv.getArgument(0);
            return factStoreBackingMap.getOrDefault(userId, new ArrayList<>());
        });
        lenient().when(userFactJpaRepository.findByUserIdAndFactKey(anyString(), anyString())).thenAnswer(inv -> {
            String userId = inv.getArgument(0);
            String factKey = inv.getArgument(1);
            return factStoreBackingMap.getOrDefault(userId, new ArrayList<>()).stream()
                    .filter(e -> factKey.equals(e.getFactKey()))
                    .findFirst();
        });
        lenient().when(userFactJpaRepository.save(any(UserFactEntity.class))).thenAnswer(inv -> {
            UserFactEntity entity = inv.getArgument(0);
            List<UserFactEntity> userFacts = factStoreBackingMap.computeIfAbsent(entity.getUserId(), k -> new ArrayList<>());
            userFacts.removeIf(e -> e.getFactKey().equals(entity.getFactKey()));
            if (entity.getId() == null) {
                entity.setId((long) (userFacts.size() + 1));
            }
            entity.setUpdatedAt(OffsetDateTime.now());
            userFacts.add(entity);
            return entity;
        });
    }

    @Nested
    @DisplayName("Scenario 1: Full context assembly with pre-populated data")
    class FullContextAssemblyTest {

        @Test
        @DisplayName("All layers contribute — verify section ordering and content presence")
        void allLayersContribute_verifyOrderingAndContent() {
            // --- Pre-populate FactStoreLayer (L2) with real data ---
            Instant now = Instant.now();
            factStoreLayer.upsert(USER_ID, new FactEntry(
                    "name", "王小明", FactCategory.IDENTITY, "conv-old-1", now));
            factStoreLayer.upsert(USER_ID, new FactEntry(
                    "industry", "互联网", FactCategory.CAREER, "conv-old-1", now));
            factStoreLayer.upsert(USER_ID, new FactEntry(
                    "goal", "年内晋升到高级工程师", FactCategory.GOALS, "conv-old-2", now));

            // --- Pre-populate SummaryLayer (L3) with real data ---
            summaryLayer.store(USER_ID, new SummaryChecklist(
                    "conv-summary-1",
                    now.minusSeconds(3600),
                    List.of("职业规划", "技能提升"),
                    List.of("专注后端深耕"),
                    List.of("学习分布式系统", "整理项目经验"),
                    List.of("是否需要考取证书")
            ));

            // --- Mock SlidingWindowLayer (L1) ---
            when(slidingWindowLayer.formatForContext(eq(CONVERSATION_ID), eq(AGENT_TYPE), anyInt()))
                    .thenReturn("user: 我最近在准备晋升\nassistant: 了解，我来帮你规划晋升路径");

            // --- Mock ExperienceStoreLayer (L4) — returns empty (realistic for new-ish user) ---
            when(experienceStoreLayer.searchSimilar(eq(USER_ID), eq(CONVERSATION_ID)))
                    .thenReturn(Collections.emptyList());

            // === Call assembleContext() ===
            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);

            // === Verify SystemMessage content ===
            String text = result.getText();
            assertThat(text).isNotBlank();

            // Verify L2 facts are present
            assertThat(text).contains("用户事实");
            assertThat(text).contains("王小明");
            assertThat(text).contains("互联网");
            assertThat(text).contains("年内晋升到高级工程师");

            // Verify L3 summary is present
            assertThat(text).contains("近期对话摘要");
            assertThat(text).contains("职业规划");
            assertThat(text).contains("专注后端深耕");
            assertThat(text).contains("学习分布式系统");

            // Verify L1 sliding window is present
            assertThat(text).contains("近期对话");
            assertThat(text).contains("我最近在准备晋升");

            // Verify section ordering: L2(facts) → L3(summary) → L1(sliding window)
            int factIndex = text.indexOf("用户事实");
            int summaryIndex = text.indexOf("近期对话摘要");
            int slidingIndex = text.indexOf("近期对话】");

            assertThat(factIndex).isGreaterThanOrEqualTo(0);
            assertThat(summaryIndex).isGreaterThan(factIndex);
            assertThat(slidingIndex).isGreaterThan(summaryIndex);
        }

        @Test
        @DisplayName("Multiple facts across categories are properly grouped")
        void multipleFactsGroupedByCategory() {
            Instant now = Instant.now();
            factStoreLayer.upsert(USER_ID, new FactEntry(
                    "name", "张三", FactCategory.IDENTITY, "c1", now));
            factStoreLayer.upsert(USER_ID, new FactEntry(
                    "city", "上海", FactCategory.IDENTITY, "c1", now));
            factStoreLayer.upsert(USER_ID, new FactEntry(
                    "industry", "金融", FactCategory.CAREER, "c2", now));
            factStoreLayer.upsert(USER_ID, new FactEntry(
                    "style", "简洁", FactCategory.PREFERENCES, "c2", now));

            when(slidingWindowLayer.formatForContext(anyString(), anyString(), anyInt())).thenReturn("");
            when(experienceStoreLayer.searchSimilar(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);
            String text = result.getText();

            // Both identity facts should appear together
            assertThat(text).contains("张三");
            assertThat(text).contains("上海");
            assertThat(text).contains("金融");
            assertThat(text).contains("简洁");
        }

        @Test
        @DisplayName("Multiple summaries appear in output (most recent first)")
        void multipleSummariesInOutput() {
            summaryLayer.store(USER_ID, new SummaryChecklist(
                    "conv-1", Instant.now().minusSeconds(7200),
                    List.of("简历优化"), List.of("添加项目量化数据"),
                    Collections.emptyList(), Collections.emptyList()));
            summaryLayer.store(USER_ID, new SummaryChecklist(
                    "conv-2", Instant.now().minusSeconds(3600),
                    List.of("面试准备"), List.of("练习自我介绍"),
                    List.of("模拟面试"), Collections.emptyList()));

            when(slidingWindowLayer.formatForContext(anyString(), anyString(), anyInt())).thenReturn("");
            when(experienceStoreLayer.searchSimilar(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            SystemMessage result = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);
            String text = result.getText();

            assertThat(text).contains("简历优化");
            assertThat(text).contains("面试准备");
            assertThat(text).contains("练习自我介绍");
            assertThat(text).contains("模拟面试");
        }
    }

    @Nested
    @DisplayName("Scenario 2: First-time user with no data")
    class FirstTimeUserTest {

        @Test
        @DisplayName("New user with no facts/summaries/experiences gets empty but valid SystemMessage")
        void newUserGetsEmptyValidSystemMessage() {
            String newUserId = "brand-new-user-no-data";

            // SlidingWindowLayer returns empty (no conversation history)
            when(slidingWindowLayer.formatForContext(eq(CONVERSATION_ID), eq(AGENT_TYPE), anyInt()))
                    .thenReturn("");

            // ExperienceStoreLayer returns empty (no vector data)
            when(experienceStoreLayer.searchSimilar(eq(newUserId), eq(CONVERSATION_ID)))
                    .thenReturn(Collections.emptyList());

            // === Call assembleContext() ===
            SystemMessage result = coordinator.assembleContext(newUserId, CONVERSATION_ID, AGENT_TYPE);

            // === Verify: result is valid but empty ===
            assertThat(result).isNotNull();
            assertThat(result.getText()).isEmpty();
        }

        @Test
        @DisplayName("New user — FactStoreLayer returns empty list gracefully")
        void factStoreReturnsEmptyForNewUser() {
            String newUserId = "fresh-user";
            List<FactEntry> facts = factStoreLayer.getFacts(newUserId);
            assertThat(facts).isEmpty();
        }

        @Test
        @DisplayName("New user — SummaryLayer returns empty string gracefully")
        void summaryLayerReturnsEmptyForNewUser() {
            String newUserId = "fresh-user";
            String summaries = summaryLayer.getRecentSummaries(newUserId, 500);
            assertThat(summaries).isEmpty();
        }

        @Test
        @DisplayName("New user — ExperienceStoreLayer returns empty gracefully (mocked)")
        void experienceStoreReturnsEmptyForNewUser() {
            String newUserId = "fresh-user";
            when(experienceStoreLayer.searchSimilar(eq(newUserId), anyString()))
                    .thenReturn(Collections.emptyList());

            // Verify it works within the full coordinator flow
            when(slidingWindowLayer.formatForContext(anyString(), anyString(), anyInt())).thenReturn("");

            SystemMessage result = coordinator.assembleContext(newUserId, CONVERSATION_ID, AGENT_TYPE);
            assertThat(result).isNotNull();
            assertThat(result.getText()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Scenario 3: onTurnCompleted triggers ExtractionPipeline")
    class OnTurnCompletedTest {

        @Test
        @DisplayName("onTurnCompleted delegates to ExtractionPipeline without error")
        void onTurnCompletedDelegatesToPipeline() {
            List<Message> messages = List.of(
                    new UserMessage("我想了解如何谈加薪"),
                    new AssistantMessage("谈加薪需要准备充分的数据支撑...")
            );

            // Should not throw
            assertThatCode(() ->
                    coordinator.onTurnCompleted(USER_ID, CONVERSATION_ID, AGENT_TYPE, messages)
            ).doesNotThrowAnyException();

            // Verify ExtractionPipeline.processAsync was invoked
            verify(extractionPipeline).processAsync(
                    eq(USER_ID),
                    eq(CONVERSATION_ID),
                    eq(AGENT_TYPE),
                    eq(messages)
            );
        }

        @Test
        @DisplayName("onTurnCompleted with multiple messages processes correctly")
        void onTurnCompletedMultiMessages() {
            List<Message> messages = List.of(
                    new UserMessage("我在一家创业公司工作了3年"),
                    new AssistantMessage("3年是个不错的积累期"),
                    new UserMessage("最近想跳槽到大厂"),
                    new AssistantMessage("跳槽到大厂需要注意以下几点...")
            );

            coordinator.onTurnCompleted(USER_ID, CONVERSATION_ID, AGENT_TYPE, messages);

            verify(extractionPipeline).processAsync(
                    eq(USER_ID), eq(CONVERSATION_ID), eq(AGENT_TYPE), eq(messages));
        }

        @Test
        @DisplayName("onTurnCompleted with null userId does not trigger pipeline")
        void nullUserIdDoesNotTrigger() {
            List<Message> messages = List.of(new UserMessage("test"));

            coordinator.onTurnCompleted(null, CONVERSATION_ID, AGENT_TYPE, messages);

            verify(extractionPipeline, never()).processAsync(
                    anyString(), anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("ExtractionPipeline failure does not propagate")
        void pipelineFailureDoesNotPropagate() {
            List<Message> messages = List.of(new UserMessage("test"));
            doThrow(new RuntimeException("LLM timeout"))
                    .when(extractionPipeline).processAsync(anyString(), anyString(), anyString(), anyList());

            assertThatCode(() ->
                    coordinator.onTurnCompleted(USER_ID, CONVERSATION_ID, AGENT_TYPE, messages)
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("End-to-end flow: pre-populate → assemble → onTurnCompleted")
    class EndToEndFlowTest {

        @Test
        @DisplayName("Full lifecycle: populate data, assemble context, complete turn")
        void fullLifecycle() {
            // Step 1: Pre-populate facts
            Instant now = Instant.now();
            factStoreLayer.upsert(USER_ID, new FactEntry(
                    "name", "李华", FactCategory.IDENTITY, "conv-prev", now));
            factStoreLayer.upsert(USER_ID, new FactEntry(
                    "years_exp", "5年", FactCategory.CAREER, "conv-prev", now));

            // Step 2: Pre-populate summary
            summaryLayer.store(USER_ID, new SummaryChecklist(
                    "conv-prev", now.minusSeconds(1800),
                    List.of("职业转型讨论"),
                    List.of("从后端转全栈"),
                    List.of("学习React"),
                    Collections.emptyList()
            ));

            // Step 3: Mock external layers
            when(slidingWindowLayer.formatForContext(eq(CONVERSATION_ID), eq(AGENT_TYPE), anyInt()))
                    .thenReturn("user: 全栈学习进展如何\nassistant: 建议先从React基础开始");
            when(experienceStoreLayer.searchSimilar(eq(USER_ID), eq(CONVERSATION_ID)))
                    .thenReturn(Collections.emptyList());

            // Step 4: Assemble context
            SystemMessage context = coordinator.assembleContext(USER_ID, CONVERSATION_ID, AGENT_TYPE);
            String text = context.getText();

            // Verify all data is assembled
            assertThat(text).contains("李华");
            assertThat(text).contains("5年");
            assertThat(text).contains("职业转型讨论");
            assertThat(text).contains("从后端转全栈");
            assertThat(text).contains("全栈学习进展如何");

            // Step 5: Call onTurnCompleted
            List<Message> turnMessages = List.of(
                    new UserMessage("全栈学习进展如何"),
                    new AssistantMessage("建议先从React基础开始")
            );

            assertThatCode(() ->
                    coordinator.onTurnCompleted(USER_ID, CONVERSATION_ID, AGENT_TYPE, turnMessages)
            ).doesNotThrowAnyException();

            verify(extractionPipeline).processAsync(
                    eq(USER_ID), eq(CONVERSATION_ID), eq(AGENT_TYPE), eq(turnMessages));
        }
    }
}
