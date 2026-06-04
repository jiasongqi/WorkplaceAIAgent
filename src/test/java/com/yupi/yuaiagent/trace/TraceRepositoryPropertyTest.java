package com.yupi.yuaiagent.trace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.trace.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for TraceRepository (任务 5.3, 5.4, 5.5, 5.6).
 */
class TraceRepositoryPropertyTest {

    @TempDir
    Path tempDir;

    private TraceRepository repository;
    private TraceProperties traceProperties;

    @BeforeEach
    void setUp() throws Exception {
        traceProperties = new TraceProperties();
        repository = new TraceRepository();
        // Inject dependencies via reflection
        var propsField = TraceRepository.class.getDeclaredField("traceProperties");
        propsField.setAccessible(true);
        propsField.set(repository, traceProperties);

        var storageDirField = TraceRepository.class.getDeclaredField("storageDir");
        storageDirField.setAccessible(true);
        storageDirField.set(repository, tempDir.toString());

        repository.init();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 9: 序列化往返一致（任务 5.3）
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 50)
    void saveAndFindByIdReturnsSameTrace(
            @ForAll("alphas") String userId,
            @ForAll("alphas") String chatId) {
        ExecutionTrace trace = ExecutionTrace.start(userId, chatId, "req-" + UUID.randomUUID());
        TraceSpan span = new TraceSpan(0, TraceStepType.INTENT_DETECTION, "intent");
        span.terminate(TraceStepStatus.SUCCESS);
        trace.addSpan(span);
        trace.finalizeStatus();

        repository.save(trace);
        ExecutionTrace loaded = repository.findById(trace.getTraceId()).orElse(null);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getTraceId()).isEqualTo(trace.getTraceId());
        assertThat(loaded.getUserId()).isEqualTo(userId);
        assertThat(loaded.getChatId()).isEqualTo(chatId);
        assertThat(loaded.getStatus()).isEqualTo(trace.getStatus());
        assertThat(loaded.getSpans()).hasSize(1);
        assertThat(loaded.getSpans().get(0).getStepType()).isEqualTo(TraceStepType.INTENT_DETECTION);
    }

    @Provide
    Arbitrary<String> alphas() {
        return Arbitraries.strings().withCharRange('a', 'f').ofLength(6);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 11: 单用户轨迹保留上限（任务 5.4）
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 20)
    void retentionPolicyEnforcesMaxTracesPerUser(
            @ForAll @IntRange(min = 3, max = 10) int maxTraces) {
        traceProperties.setMaxTracesPerUser(maxTraces);

        String userId = "retention-user";
        for (int i = 0; i < maxTraces + 5; i++) {
            ExecutionTrace trace = ExecutionTrace.start(userId, "chat-" + i, "req-" + i);
            repository.save(trace);
            repository.enforceRetentionPolicy(userId);
        }

        List<ExecutionTrace> userTraces = repository.findByUserId(userId);
        assertThat(userTraces.size())
                .as("User should have at most %d traces", maxTraces)
                .isLessThanOrEqualTo(maxTraces);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 13: 列表查询过滤与倒序（任务 5.5）
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 30)
    void findByChatIdReturnsFilteredAndSorted(
            @ForAll @IntRange(min = 3, max = 10) int traceCount) {
        String targetChatId = "target-chat";
        String otherChatId = "other-chat";

        for (int i = 0; i < traceCount; i++) {
            String chatId = (i % 2 == 0) ? targetChatId : otherChatId;
            ExecutionTrace trace = ExecutionTrace.start("u1", chatId, "req-" + i);
            repository.save(trace);
        }

        List<ExecutionTrace> results = repository.findByChatId(targetChatId);

        // All results should belong to targetChatId
        for (ExecutionTrace t : results) {
            assertThat(t.getChatId()).isEqualTo(targetChatId);
        }

        // Results should be sorted by startTime descending
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i - 1).getStartTime())
                    .isAfterOrEqualTo(results.get(i).getStartTime());
        }
    }

    @Property(tries = 30)
    void findByUserIdReturnsFilteredAndSorted(
            @ForAll @IntRange(min = 3, max = 10) int traceCount) {
        String targetUser = "target-user";
        String otherUser = "other-user";

        for (int i = 0; i < traceCount; i++) {
            String userId = (i % 2 == 0) ? targetUser : otherUser;
            ExecutionTrace trace = ExecutionTrace.start(userId, "chat-" + i, "req-" + i);
            repository.save(trace);
        }

        List<ExecutionTrace> results = repository.findByUserId(targetUser);

        for (ExecutionTrace t : results) {
            assertThat(t.getUserId()).isEqualTo(targetUser);
        }

        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i - 1).getStartTime())
                    .isAfterOrEqualTo(results.get(i).getStartTime());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 单元测试：加载/容错（任务 5.6）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("空仓库 findById 返回 empty")
    void findByIdOnEmptyRepoReturnsEmpty() {
        assertThat(repository.findById("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("findByChatId 空参数返回空列表")
    void findByChatIdWithBlankReturnsEmpty() {
        assertThat(repository.findByChatId(null)).isEmpty();
        assertThat(repository.findByChatId("")).isEmpty();
        assertThat(repository.findByChatId("  ")).isEmpty();
    }

    @Test
    @DisplayName("findByUserId 空参数返回空列表")
    void findByUserIdWithBlankReturnsEmpty() {
        assertThat(repository.findByUserId(null)).isEmpty();
        assertThat(repository.findByUserId("")).isEmpty();
    }

    @Test
    @DisplayName("enforceRetentionPolicy 空 userId 不抛异常")
    void enforceRetentionPolicyWithNullUserId() {
        repository.enforceRetentionPolicy(null);
        repository.enforceRetentionPolicy("");
    }

    @Test
    @DisplayName("保存后 count 增加")
    void saveIncrementsCount() {
        int before = repository.count();
        repository.save(ExecutionTrace.start("u1", "c1", "r1"));
        assertThat(repository.count()).isEqualTo(before + 1);
    }
}
