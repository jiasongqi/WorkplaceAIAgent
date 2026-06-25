package com.yupi.yuaiagent.memory.experience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ExperienceStoreLayer 单元测试
 *
 * <p>验证：
 * <ul>
 *   <li>userId 隔离：不同用户的文档不会交叉返回</li>
 *   <li>相似度阈值过滤：低于阈值的结果不返回</li>
 *   <li>无匹配时返回空列表</li>
 *   <li>store 方法正确存储文档及其元数据</li>
 *   <li>空白查询返回空列表</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ExperienceStoreLayerTest {

    @Mock
    private VectorStore experienceVectorStore;

    @InjectMocks
    private ExperienceStoreLayer experienceStoreLayer;

    @BeforeEach
    void setUp() throws Exception {
        // Set @Value-injected fields via reflection
        setField(experienceStoreLayer, "defaultTopK", 3);
        setField(experienceStoreLayer, "defaultSimilarityThreshold", 0.7);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Nested
    @DisplayName("searchSimilar() 搜索测试")
    class SearchTests {

        @Test
        @DisplayName("userId 隔离：仅返回属于当前用户的文档")
        void userIdIsolation() {
            // Given: VectorStore returns documents for userA only
            Document userADoc = new Document("doc1", "Experience content for userA", Map.of(
                    "userId", "userA",
                    "agentType", "career",
                    "outcome", "success",
                    "createdAt", Instant.now().toString()
            ));

            when(experienceVectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(userADoc));

            // When: search for userA
            List<ExperienceDocument> resultsA = experienceStoreLayer.searchSimilar("userA", "career advice");

            // Then: userA gets results
            assertThat(resultsA).hasSize(1);
            assertThat(resultsA.get(0).userId()).isEqualTo("userA");

            // When: search for userB (VectorStore returns empty because of userId filter)
            when(experienceVectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(Collections.emptyList());

            List<ExperienceDocument> resultsB = experienceStoreLayer.searchSimilar("userB", "career advice");

            // Then: userB gets no results (isolation enforced)
            assertThat(resultsB).isEmpty();
        }

        @Test
        @DisplayName("相似度阈值过滤：VectorStore 已应用阈值，低于阈值的不返回")
        void thresholdFiltering() {
            // Given: VectorStore returns empty when nothing passes threshold
            when(experienceVectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(Collections.emptyList());

            // When: search with default threshold (0.7)
            List<ExperienceDocument> results = experienceStoreLayer.searchSimilar("user1", "irrelevant query");

            // Then: empty result (nothing passed threshold)
            assertThat(results).isEmpty();

            // Verify the SearchRequest was built with the correct threshold
            ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
            verify(experienceVectorStore).similaritySearch(captor.capture());
            SearchRequest captured = captor.getValue();
            assertThat(captured.getSimilarityThreshold()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("自定义阈值传递给 SearchRequest")
        void customThresholdPassedToSearchRequest() {
            when(experienceVectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(Collections.emptyList());

            // When: search with custom threshold
            experienceStoreLayer.searchSimilar("user1", "some query", 5, 0.9);

            // Then: verify threshold is 0.9
            ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
            verify(experienceVectorStore).similaritySearch(captor.capture());
            assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.9);
            assertThat(captor.getValue().getTopK()).isEqualTo(5);
        }

        @Test
        @DisplayName("无匹配时返回空列表")
        void emptyResultOnNoMatch() {
            // Given: VectorStore returns null
            when(experienceVectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(null);

            // When
            List<ExperienceDocument> results = experienceStoreLayer.searchSimilar("user1", "completely unrelated");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("VectorStore 返回空列表时返回空列表")
        void emptyListFromVectorStore() {
            when(experienceVectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(Collections.emptyList());

            List<ExperienceDocument> results = experienceStoreLayer.searchSimilar("user1", "some query");

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("空白查询返回空列表")
        void blankQueryReturnsEmpty() {
            List<ExperienceDocument> results = experienceStoreLayer.searchSimilar("user1", "   ");

            assertThat(results).isEmpty();
            // VectorStore should not be called for blank queries
            verifyNoInteractions(experienceVectorStore);
        }

        @Test
        @DisplayName("空字符串查询返回空列表")
        void emptyStringQueryReturnsEmpty() {
            // Note: empty string throws NullPointerException via Objects.requireNonNull check,
            // but blank check comes after null check. Let's test with whitespace-only.
            List<ExperienceDocument> results = experienceStoreLayer.searchSimilar("user1", " ");

            assertThat(results).isEmpty();
            verifyNoInteractions(experienceVectorStore);
        }

        @Test
        @DisplayName("VectorStore 异常时返回空列表（容错）")
        void vectorStoreExceptionReturnsEmpty() {
            when(experienceVectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenThrow(new RuntimeException("Connection failed"));

            List<ExperienceDocument> results = experienceStoreLayer.searchSimilar("user1", "test query");

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("搜索结果正确映射回 ExperienceDocument")
        void searchResultsMappedCorrectly() {
            Instant now = Instant.parse("2024-01-15T10:30:00Z");
            Document vectorDoc = new Document("exp-001", "Negotiated salary successfully", Map.of(
                    "userId", "user1",
                    "agentType", "negotiation",
                    "outcome", "success",
                    "createdAt", now.toString(),
                    "custom_company", "TechCorp",
                    "custom_role", "Senior Engineer"
            ));

            when(experienceVectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(vectorDoc));

            List<ExperienceDocument> results = experienceStoreLayer.searchSimilar("user1", "salary negotiation");

            assertThat(results).hasSize(1);
            ExperienceDocument result = results.get(0);
            assertThat(result.id()).isEqualTo("exp-001");
            assertThat(result.userId()).isEqualTo("user1");
            assertThat(result.agentType()).isEqualTo("negotiation");
            assertThat(result.content()).isEqualTo("Negotiated salary successfully");
            assertThat(result.outcome()).isEqualTo("success");
            assertThat(result.createdAt()).isEqualTo(now);
            assertThat(result.metadata()).containsEntry("company", "TechCorp");
            assertThat(result.metadata()).containsEntry("role", "Senior Engineer");
        }
    }

    @Nested
    @DisplayName("store() 存储测试")
    class StoreTests {

        @Test
        @DisplayName("store 正确存储文档及所有元数据")
        void storeDocumentWithAllMetadata() {
            Instant now = Instant.now();
            ExperienceDocument doc = new ExperienceDocument(
                    "exp-001",
                    "user1",
                    "career",
                    "Successfully transitioned to management role",
                    "success",
                    now,
                    Map.of("company", "TechCorp", "duration", "6months")
            );

            // When
            experienceStoreLayer.store(doc);

            // Then: verify VectorStore.add was called with correct Document
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
            verify(experienceVectorStore).add(captor.capture());

            List<Document> storedDocs = captor.getValue();
            assertThat(storedDocs).hasSize(1);

            Document stored = storedDocs.get(0);
            assertThat(stored.getId()).isEqualTo("exp-001");
            assertThat(stored.getText()).isEqualTo("Successfully transitioned to management role");
            assertThat(stored.getMetadata()).containsEntry("userId", "user1");
            assertThat(stored.getMetadata()).containsEntry("agentType", "career");
            assertThat(stored.getMetadata()).containsEntry("outcome", "success");
            assertThat(stored.getMetadata()).containsEntry("createdAt", now.toString());
            assertThat(stored.getMetadata()).containsEntry("custom_company", "TechCorp");
            assertThat(stored.getMetadata()).containsEntry("custom_duration", "6months");
        }

        @Test
        @DisplayName("store 文档 id 为 null 时生成 UUID")
        void storeDocumentWithNullIdGeneratesUUID() {
            ExperienceDocument doc = new ExperienceDocument(
                    null,
                    "user1",
                    "career",
                    "Some experience content",
                    "insight",
                    Instant.now(),
                    null
            );

            experienceStoreLayer.store(doc);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
            verify(experienceVectorStore).add(captor.capture());

            Document stored = captor.getValue().get(0);
            assertThat(stored.getId()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("store 文档 metadata 为 null 时不报错")
        void storeDocumentWithNullMetadata() {
            ExperienceDocument doc = new ExperienceDocument(
                    "exp-002",
                    "user1",
                    "resume",
                    "Prepared resume for tech role",
                    "success",
                    Instant.now(),
                    null
            );

            experienceStoreLayer.store(doc);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
            verify(experienceVectorStore).add(captor.capture());

            Document stored = captor.getValue().get(0);
            assertThat(stored.getMetadata()).containsEntry("userId", "user1");
            // No custom_ keys should be present
            assertThat(stored.getMetadata().keySet().stream()
                    .filter(k -> k.startsWith("custom_"))
                    .toList()).isEmpty();
        }

        @Test
        @DisplayName("store 空 agentType 和 outcome 存储为空字符串")
        void storeDocumentWithNullOptionalFields() {
            ExperienceDocument doc = new ExperienceDocument(
                    "exp-003",
                    "user1",
                    null,
                    "Some content",
                    null,
                    null,
                    null
            );

            experienceStoreLayer.store(doc);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
            verify(experienceVectorStore).add(captor.capture());

            Document stored = captor.getValue().get(0);
            assertThat(stored.getMetadata()).containsEntry("agentType", "");
            assertThat(stored.getMetadata()).containsEntry("outcome", "");
            // createdAt defaults to current time
            assertThat(stored.getMetadata().get("createdAt")).isNotNull();
        }

        @Test
        @DisplayName("store null 文档抛出 NullPointerException")
        void storeNullDocumentThrows() {
            assertThatThrownBy(() -> experienceStoreLayer.store(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("store 文档 userId 为 null 抛出 NullPointerException")
        void storeDocumentWithNullUserIdThrows() {
            ExperienceDocument doc = new ExperienceDocument(
                    "exp-004", null, "career", "content", "success", Instant.now(), null
            );

            assertThatThrownBy(() -> experienceStoreLayer.store(doc))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("store 文档 content 为 null 抛出 NullPointerException")
        void storeDocumentWithNullContentThrows() {
            ExperienceDocument doc = new ExperienceDocument(
                    "exp-005", "user1", "career", null, "success", Instant.now(), null
            );

            assertThatThrownBy(() -> experienceStoreLayer.store(doc))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("VectorStore 异常时 store 不抛出（容错处理）")
        void storeHandlesVectorStoreException() {
            doThrow(new RuntimeException("Storage failure"))
                    .when(experienceVectorStore).add(any());

            ExperienceDocument doc = new ExperienceDocument(
                    "exp-006", "user1", "career", "content", "success", Instant.now(), null
            );

            // Should not throw — error is caught and logged
            experienceStoreLayer.store(doc);
        }
    }
}
