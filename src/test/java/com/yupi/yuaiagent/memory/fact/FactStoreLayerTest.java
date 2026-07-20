package com.yupi.yuaiagent.memory.fact;

import com.yupi.yuaiagent.memory.TokenBudgetAllocator;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.profile.model.CommunicationPreference;
import com.yupi.yuaiagent.profile.model.UserProfile;
import com.yupi.yuaiagent.repository.entity.UserFactEntity;
import com.yupi.yuaiagent.repository.jpa.UserFactJpaRepository;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * FactStoreLayer unit tests (JPA version).
 */
class FactStoreLayerTest {

    private FactStoreLayer factStore;
    private TokenBudgetAllocator allocator;
    private UserProfileService userProfileService;
    private UserFactJpaRepository jpaRepo;

    // In-memory simulation of JPA repo
    private final Map<String, List<UserFactEntity>> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        allocator = new TokenBudgetAllocator(60, 15, 10, 15);
        userProfileService = mock(UserProfileService.class);
        when(userProfileService.get(anyString())).thenReturn(Optional.empty());

        jpaRepo = mock(UserFactJpaRepository.class);
        setupJpaMock();

        factStore = new FactStoreLayer(jpaRepo, allocator, userProfileService);
        factStore.init();
    }

    private void setupJpaMock() {
        // findByUserId
        when(jpaRepo.findByUserId(anyString())).thenAnswer(inv -> {
            String userId = inv.getArgument(0);
            return store.getOrDefault(userId, new ArrayList<>());
        });

        // findByUserIdAndFactKey
        when(jpaRepo.findByUserIdAndFactKey(anyString(), anyString())).thenAnswer(inv -> {
            String userId = inv.getArgument(0);
            String factKey = inv.getArgument(1);
            return store.getOrDefault(userId, new ArrayList<>()).stream()
                    .filter(e -> factKey.equals(e.getFactKey()))
                    .findFirst();
        });

        // save - add to in-memory store
        when(jpaRepo.save(any(UserFactEntity.class))).thenAnswer(inv -> {
            UserFactEntity entity = inv.getArgument(0);
            List<UserFactEntity> userFacts = store.computeIfAbsent(entity.getUserId(), k -> new ArrayList<>());
            // Remove existing with same key
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
    @DisplayName("upsert() tests")
    class UpsertTests {

        @Test
        @DisplayName("upsert creates new entry")
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
        @DisplayName("upsert overwrites existing entry (same key, different value)")
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
        }

        @Test
        @DisplayName("upsert is idempotent (same key+value skips)")
        void upsertIdempotentSameKeyAndValue() {
            FactEntry entry = new FactEntry(
                    "industry", "互联网", FactCategory.CAREER, "conv-1", Instant.now());

            factStore.upsert("user-1", entry);
            factStore.upsert("user-1", entry);

            List<FactEntry> facts = factStore.getFacts("user-1");
            assertThat(facts).hasSize(1);
            assertThat(facts.get(0).value()).isEqualTo("互联网");
        }

        @Test
        @DisplayName("upsert multiple different keys")
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
        @DisplayName("upsert null userId or null entry does not throw")
        void upsertNullInputsAreIgnored() {
            factStore.upsert(null, new FactEntry("k", "v", FactCategory.IDENTITY, "c", Instant.now()));
            factStore.upsert("user-1", null);

            assertThat(factStore.getFacts("user-1")).isEmpty();
        }

        @Test
        @DisplayName("facts are isolated by userId")
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
    @DisplayName("getFacts() tests")
    class GetFactsTests {

        @Test
        @DisplayName("non-existent userId returns empty list")
        void nonExistentUserReturnsEmptyList() {
            List<FactEntry> facts = factStore.getFacts("non-existent");
            assertThat(facts).isEmpty();
        }

        @Test
        @DisplayName("returns all entries for user")
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
    @DisplayName("migrateFromProfile() tests")
    class MigrationTests {

        @Test
        @DisplayName("full UserProfile migration produces correct FactEntries")
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

            assertThat(facts).anySatisfy(f -> {
                assertThat(f.key()).isEqualTo("communication_preference");
                assertThat(f.value()).isEqualTo("简洁");
                assertThat(f.category()).isEqualTo(FactCategory.PREFERENCES);
            });

            assertThat(facts).anySatisfy(f -> {
                assertThat(f.key()).isEqualTo("tone_preference");
                assertThat(f.value()).isEqualTo("鼓励型");
                assertThat(f.category()).isEqualTo(FactCategory.PREFERENCES);
            });

            assertThat(facts).anySatisfy(f -> {
                assertThat(f.key()).isEqualTo("focus_areas");
                assertThat(f.value()).isEqualTo("后端开发,系统设计,分布式");
                assertThat(f.category()).isEqualTo(FactCategory.CAREER);
            });

            assertThat(facts).anySatisfy(f -> {
                assertThat(f.key()).isEqualTo("background");
                assertThat(f.value()).isEqualTo("5年Java开发经验，互联网行业");
                assertThat(f.category()).isEqualTo(FactCategory.IDENTITY);
            });

            assertThat(facts).anySatisfy(f -> {
                assertThat(f.key()).isEqualTo("historical_demands");
                assertThat(f.value()).isEqualTo("跳槽建议,简历优化");
                assertThat(f.category()).isEqualTo(FactCategory.GOALS);
            });
        }

        @Test
        @DisplayName("partial UserProfile migration skips null fields")
        void partialProfileMigrationSkipsNullFields() {
            UserProfile profile = UserProfile.builder()
                    .userId("user-2")
                    .communicationPreference(CommunicationPreference.DETAILED)
                    .tonePreference(null)
                    .focusAreas(List.of())
                    .knownBackground("")
                    .historicalDemands(null)
                    .build();

            factStore.migrateFromProfile("user-2", profile);

            List<FactEntry> facts = factStore.getFacts("user-2");
            assertThat(facts).hasSize(1);
            assertThat(facts.get(0).key()).isEqualTo("communication_preference");
            assertThat(facts.get(0).value()).isEqualTo("详细");
        }

        @Test
        @DisplayName("null profile does not throw")
        void nullProfileIsIgnored() {
            factStore.migrateFromProfile("user-3", null);
            assertThat(factStore.getFacts("user-3")).isEmpty();
        }

        @Test
        @DisplayName("isMigrated returns true after migration")
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
        @DisplayName("migration source is marked as profile_migration")
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
    @DisplayName("formatForContext() tests")
    class FormatContextTests {

        @Test
        @DisplayName("no facts returns empty string")
        void noFactsReturnsEmpty() {
            String result = factStore.formatForContext("empty-user", 100);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("formatted output includes category header and facts")
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
        @DisplayName("formatForContext truncates large facts significantly")
        void formatRespectsTokenBudget() {
            Instant now = Instant.now();
            for (int i = 0; i < 50; i++) {
                factStore.upsert("budget-user", new FactEntry(
                        "fact_" + i, "这是一段比较长的事实描述用于测试token预算截断功能_" + i,
                        FactCategory.CAREER, "c1", now));
            }

            String fullResult = factStore.formatForContext("budget-user", 10000);
            int fullTokens = allocator.estimateTokens(fullResult);
            assertThat(fullTokens).isGreaterThan(300);

            int smallBudget = 100;
            String result = factStore.formatForContext("budget-user", smallBudget);
            int truncatedTokens = allocator.estimateTokens(result);

            assertThat(truncatedTokens).isLessThan(fullTokens / 2);
            assertThat(result).isNotEmpty();
            assertThat(result).contains("用户事实");
        }
    }

    @Nested
    @DisplayName("batchUpsert() tests")
    class BatchUpsertTests {

        @Test
        @DisplayName("batch upsert multiple entries")
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
        @DisplayName("batch upsert null/empty list does not throw")
        void batchUpsertNullOrEmptyIsIgnored() {
            factStore.batchUpsert("user", null);
            factStore.batchUpsert("user", List.of());
            factStore.batchUpsert(null, List.of(new FactEntry("k", "v", FactCategory.IDENTITY, "c", Instant.now())));

            assertThat(factStore.getFacts("user")).isEmpty();
        }
    }
}
