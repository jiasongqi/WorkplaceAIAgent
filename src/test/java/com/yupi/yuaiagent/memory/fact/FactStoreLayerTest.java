package com.yupi.yuaiagent.memory.fact;

import com.yupi.yuaiagent.memory.TokenBudgetAllocator;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.profile.model.CommunicationPreference;
import com.yupi.yuaiagent.profile.model.UserProfile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FactStoreLayer 单元测试
 *
 * <p>验证：
 * <ul>
 *   <li>upsert 创建新条目</li>
 *   <li>upsert 覆写已有条目（同 key）</li>
 *   <li>upsert 幂等（同 key+value 无变化）</li>
 *   <li>getFacts 返回所有条目</li>
 *   <li>UserProfile 迁移产生正确的 FactEntry</li>
 *   <li>持久化 round-trip（写文件 → 重载 → 验证一致）</li>
 *   <li>formatForContext 遵循 token 预算</li>
 * </ul>
 */
class FactStoreLayerTest {

    @TempDir
    Path tempDir;

    private FactStoreLayer factStore;
    private TokenBudgetAllocator allocator;
    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        allocator = new TokenBudgetAllocator(60, 15, 10, 15);
        userProfileService = mock(UserProfileService.class);
        when(userProfileService.get(anyString())).thenReturn(Optional.empty());
        factStore = new FactStoreLayer(tempDir.toString(), allocator, userProfileService);
        factStore.init();
    }

    @Nested
    @DisplayName("upsert() 测试")
    class UpsertTests {

        @Test
        @DisplayName("upsert 创建新条目")
        void upsertCreatesNewEntry() {
            FactEntry entry = new FactEntry(
                    "name", "张三", FactCategory.IDENTITY, "conv-1", Instant.now());

            factStore.upsert("user-1", entry);

            List<FactEntry> facts = factStore.getFacts("user-1");
            assertThat(facts).hasSize(1);
            assertThat(facts.get(0).key()).isEqualTo("name");
            assertThat(facts.get(0).value()).isEqualTo("张三");
            assertThat(facts.get(0).category()).isEqualTo(FactCategory.IDENTITY);
        }

        @Test
        @DisplayName("upsert 覆写已有条目（同 key 不同 value）")
        void upsertOverwritesExistingEntry() {
            Instant t1 = Instant.now();
            Instant t2 = t1.plusSeconds(60);

            FactEntry original = new FactEntry(
                    "budget", "5000", FactCategory.CONSTRAINTS, "conv-1", t1);
            FactEntry updated = new FactEntry(
                    "budget", "8000", FactCategory.CONSTRAINTS, "conv-2", t2);

            factStore.upsert("user-1", original);
            factStore.upsert("user-1", updated);

            List<FactEntry> facts = factStore.getFacts("user-1");
            assertThat(facts).hasSize(1);
            assertThat(facts.get(0).value()).isEqualTo("8000");
            assertThat(facts.get(0).sourceConversationId()).isEqualTo("conv-2");
            assertThat(facts.get(0).updatedAt()).isEqualTo(t2);
        }

        @Test
        @DisplayName("upsert 幂等（同 key+value 不重复写入）")
        void upsertIdempotentSameKeyAndValue() {
            FactEntry entry = new FactEntry(
                    "industry", "互联网", FactCategory.CAREER, "conv-1", Instant.now());

            factStore.upsert("user-1", entry);
            factStore.upsert("user-1", entry); // 重复 upsert

            List<FactEntry> facts = factStore.getFacts("user-1");
            assertThat(facts).hasSize(1);
            assertThat(facts.get(0).value()).isEqualTo("互联网");
        }

        @Test
        @DisplayName("upsert 多个不同 key 的条目")
        void upsertMultipleDifferentKeys() {
            Instant now = Instant.now();
            factStore.upsert("user-1", new FactEntry("name", "李四", FactCategory.IDENTITY, "c1", now));
            factStore.upsert("user-1", new FactEntry("age", "28", FactCategory.IDENTITY, "c1", now));
            factStore.upsert("user-1", new FactEntry("industry", "金融", FactCategory.CAREER, "c1", now));

            List<FactEntry> facts = factStore.getFacts("user-1");
            assertThat(facts).hasSize(3);
            assertThat(facts).extracting(FactEntry::key)
                    .containsExactlyInAnyOrder("name", "age", "industry");
        }

        @Test
        @DisplayName("upsert null userId 或 null entry 不报错")
        void upsertNullInputsAreIgnored() {
            factStore.upsert(null, new FactEntry("k", "v", FactCategory.IDENTITY, "c", Instant.now()));
            factStore.upsert("user-1", null);

            assertThat(factStore.getFacts("user-1")).isEmpty();
        }

        @Test
        @DisplayName("不同 userId 的事实相互隔离")
        void factsIsolatedByUserId() {
            Instant now = Instant.now();
            factStore.upsert("user-A", new FactEntry("name", "Alice", FactCategory.IDENTITY, "c1", now));
            factStore.upsert("user-B", new FactEntry("name", "Bob", FactCategory.IDENTITY, "c2", now));

            assertThat(factStore.getFacts("user-A")).hasSize(1);
            assertThat(factStore.getFacts("user-A").get(0).value()).isEqualTo("Alice");

            assertThat(factStore.getFacts("user-B")).hasSize(1);
            assertThat(factStore.getFacts("user-B").get(0).value()).isEqualTo("Bob");
        }
    }

    @Nested
    @DisplayName("getFacts() 测试")
    class GetFactsTests {

        @Test
        @DisplayName("不存在的 userId 返回空列表")
        void nonExistentUserReturnsEmptyList() {
            List<FactEntry> facts = factStore.getFacts("non-existent");
            assertThat(facts).isEmpty();
        }

        @Test
        @DisplayName("返回所有用户事实条目")
        void returnsAllEntriesForUser() {
            Instant now = Instant.now();
            factStore.upsert("user-1", new FactEntry("k1", "v1", FactCategory.IDENTITY, "c1", now));
            factStore.upsert("user-1", new FactEntry("k2", "v2", FactCategory.CAREER, "c1", now));
            factStore.upsert("user-1", new FactEntry("k3", "v3", FactCategory.GOALS, "c1", now));

            List<FactEntry> facts = factStore.getFacts("user-1");
            assertThat(facts).hasSize(3);
        }
    }

    @Nested
    @DisplayName("migrateFromProfile() 迁移测试")
    class MigrationTests {

        @Test
        @DisplayName("完整 UserProfile 迁移产生正确的 FactEntry")
        void fullProfileMigrationProducesCorrectEntries() {
            UserProfile profile = UserProfile.builder()
                    .userId("user-1")
                    .communicationPreference(CommunicationPreference.CONCISE)
                    .tonePreference("鼓励型")
                    .focusAreas(List.of("后端开发", "系统设计", "分布式"))
                    .knownBackground("5年Java开发经验，互联网行业")
                    .historicalDemands(List.of("跳槽建议", "简历优化"))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            factStore.migrateFromProfile("user-1", profile);

            List<FactEntry> facts = factStore.getFacts("user-1");
            assertThat(facts).hasSize(5);

            // 验证 communicationPreference
            assertThat(facts).anySatisfy(f -> {
                assertThat(f.key()).isEqualTo("communication_preference");
                assertThat(f.value()).isEqualTo("简洁");
                assertThat(f.category()).isEqualTo(FactCategory.PREFERENCES);
            });

            // 验证 tonePreference
            assertThat(facts).anySatisfy(f -> {
                assertThat(f.key()).isEqualTo("tone_preference");
                assertThat(f.value()).isEqualTo("鼓励型");
                assertThat(f.category()).isEqualTo(FactCategory.PREFERENCES);
            });

            // 验证 focusAreas (comma-joined)
            assertThat(facts).anySatisfy(f -> {
                assertThat(f.key()).isEqualTo("focus_areas");
                assertThat(f.value()).isEqualTo("后端开发,系统设计,分布式");
                assertThat(f.category()).isEqualTo(FactCategory.CAREER);
            });

            // 验证 knownBackground
            assertThat(facts).anySatisfy(f -> {
                assertThat(f.key()).isEqualTo("background");
                assertThat(f.value()).isEqualTo("5年Java开发经验，互联网行业");
                assertThat(f.category()).isEqualTo(FactCategory.IDENTITY);
            });

            // 验证 historicalDemands (comma-joined)
            assertThat(facts).anySatisfy(f -> {
                assertThat(f.key()).isEqualTo("historical_demands");
                assertThat(f.value()).isEqualTo("跳槽建议,简历优化");
                assertThat(f.category()).isEqualTo(FactCategory.GOALS);
            });
        }

        @Test
        @DisplayName("部分字段为空的 UserProfile 迁移只产生非空条目")
        void partialProfileMigrationSkipsNullFields() {
            UserProfile profile = UserProfile.builder()
                    .userId("user-2")
                    .communicationPreference(CommunicationPreference.DETAILED)
                    .tonePreference(null)       // null
                    .focusAreas(List.of())      // empty
                    .knownBackground("")        // blank
                    .historicalDemands(null)     // null
                    .build();

            factStore.migrateFromProfile("user-2", profile);

            List<FactEntry> facts = factStore.getFacts("user-2");
            // 只有 communicationPreference 不为空
            assertThat(facts).hasSize(1);
            assertThat(facts.get(0).key()).isEqualTo("communication_preference");
            assertThat(facts.get(0).value()).isEqualTo("详细");
        }

        @Test
        @DisplayName("null profile 不报错")
        void nullProfileIsIgnored() {
            factStore.migrateFromProfile("user-3", null);
            assertThat(factStore.getFacts("user-3")).isEmpty();
        }

        @Test
        @DisplayName("迁移后 isMigrated 返回 true")
        void migrationSetsMarker() {
            UserProfile profile = UserProfile.builder()
                    .userId("user-4")
                    .communicationPreference(CommunicationPreference.CONCISE)
                    .build();

            assertThat(factStore.isMigrated("user-4")).isFalse();

            factStore.migrateFromProfile("user-4", profile);

            assertThat(factStore.isMigrated("user-4")).isTrue();
        }

        @Test
        @DisplayName("迁移的事实来源标记为 profile_migration")
        void migrationSourceIsMarked() {
            UserProfile profile = UserProfile.builder()
                    .userId("user-5")
                    .knownBackground("产品经理")
                    .build();

            factStore.migrateFromProfile("user-5", profile);

            List<FactEntry> facts = factStore.getFacts("user-5");
            assertThat(facts).allSatisfy(f ->
                    assertThat(f.sourceConversationId()).isEqualTo("profile_migration"));
        }
    }

    @Nested
    @DisplayName("持久化 round-trip 测试")
    class PersistenceTests {

        @Test
        @DisplayName("写入后新实例可以重载数据")
        void persistenceRoundTrip() {
            Instant now = Instant.parse("2024-06-15T10:30:00Z");
            factStore.upsert("user-rt", new FactEntry("name", "王五", FactCategory.IDENTITY, "c1", now));
            factStore.upsert("user-rt", new FactEntry("goal", "晋升", FactCategory.GOALS, "c2", now));

            // 创建新的 FactStoreLayer 实例从同一目录加载
            FactStoreLayer reloaded = new FactStoreLayer(tempDir.toString(), allocator, userProfileService);
            reloaded.init();

            List<FactEntry> facts = reloaded.getFacts("user-rt");
            assertThat(facts).hasSize(2);
            assertThat(facts).extracting(FactEntry::key)
                    .containsExactlyInAnyOrder("name", "goal");
            assertThat(facts).extracting(FactEntry::value)
                    .containsExactlyInAnyOrder("王五", "晋升");
        }

        @Test
        @DisplayName("JSON 文件按 userId 命名")
        void fileNameMatchesUserId() {
            factStore.upsert("test-user-id", new FactEntry(
                    "k", "v", FactCategory.CAREER, "c", Instant.now()));

            Path expectedFile = tempDir.resolve("test-user-id.json");
            assertThat(Files.exists(expectedFile)).isTrue();
        }

        @Test
        @DisplayName("upsert 覆写后文件内容更新")
        void fileUpdatedAfterOverwrite() throws IOException {
            Instant t1 = Instant.now();
            factStore.upsert("user-persist", new FactEntry(
                    "salary", "20k", FactCategory.CONSTRAINTS, "c1", t1));
            factStore.upsert("user-persist", new FactEntry(
                    "salary", "30k", FactCategory.CONSTRAINTS, "c2", t1.plusSeconds(10)));

            // 读取 JSON 文件内容验证
            Path file = tempDir.resolve("user-persist.json");
            String content = Files.readString(file);
            assertThat(content).contains("30k");
            assertThat(content).doesNotContain("20k");
        }
    }

    @Nested
    @DisplayName("formatForContext() 测试")
    class FormatContextTests {

        @Test
        @DisplayName("无事实时返回空字符串")
        void noFactsReturnsEmpty() {
            String result = factStore.formatForContext("empty-user", 100);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("格式化包含类别标头和事实")
        void formattedOutputIncludesCategoryAndFacts() {
            Instant now = Instant.now();
            factStore.upsert("fmt-user", new FactEntry("name", "赵六", FactCategory.IDENTITY, "c1", now));
            factStore.upsert("fmt-user", new FactEntry("goal", "转行", FactCategory.GOALS, "c1", now));

            String result = factStore.formatForContext("fmt-user", 500);
            assertThat(result).contains("用户事实");
            assertThat(result).contains("身份信息");
            assertThat(result).contains("name=赵六");
            assertThat(result).contains("目标计划");
            assertThat(result).contains("goal=转行");
        }

        @Test
        @DisplayName("formatForContext 截断大量事实时显著减少内容")
        void formatRespectsTokenBudget() {
            Instant now = Instant.now();
            // 添加很多事实让内容超出预算
            for (int i = 0; i < 50; i++) {
                factStore.upsert("budget-user", new FactEntry(
                        "fact_" + i, "这是一段比较长的事实描述用于测试token预算截断功能_" + i,
                        FactCategory.CAREER, "c1", now));
            }

            // 获取完整内容的 token 数
            String fullResult = factStore.formatForContext("budget-user", 10000);
            int fullTokens = allocator.estimateTokens(fullResult);
            // 确认完整内容很长
            assertThat(fullTokens).isGreaterThan(300);

            // 使用较小预算请求截断
            int smallBudget = 100;
            String result = factStore.formatForContext("budget-user", smallBudget);
            int truncatedTokens = allocator.estimateTokens(result);

            // 验证截断确实发生：截断后内容显著小于完整内容
            assertThat(truncatedTokens).isLessThan(fullTokens / 2);
            // 验证截断结果非空
            assertThat(result).isNotEmpty();
            assertThat(result).contains("用户事实");
        }
    }

    @Nested
    @DisplayName("batchUpsert() 测试")
    class BatchUpsertTests {

        @Test
        @DisplayName("批量 upsert 多条事实")
        void batchUpsertMultipleEntries() {
            Instant now = Instant.now();
            List<FactEntry> entries = List.of(
                    new FactEntry("k1", "v1", FactCategory.IDENTITY, "c1", now),
                    new FactEntry("k2", "v2", FactCategory.CAREER, "c1", now),
                    new FactEntry("k3", "v3", FactCategory.PREFERENCES, "c1", now)
            );

            factStore.batchUpsert("batch-user", entries);

            List<FactEntry> facts = factStore.getFacts("batch-user");
            assertThat(facts).hasSize(3);
        }

        @Test
        @DisplayName("批量 upsert null/空列表不报错")
        void batchUpsertNullOrEmptyIsIgnored() {
            factStore.batchUpsert("user", null);
            factStore.batchUpsert("user", List.of());
            factStore.batchUpsert(null, List.of(new FactEntry("k", "v", FactCategory.IDENTITY, "c", Instant.now())));

            assertThat(factStore.getFacts("user")).isEmpty();
        }
    }
}
