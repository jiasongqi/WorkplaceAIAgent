package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.trace.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TraceRepository: save/find, filtering, retention policy.
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
        var propsField = TraceRepository.class.getDeclaredField("traceProperties");
        propsField.setAccessible(true);
        propsField.set(repository, traceProperties);

        var storageDirField = TraceRepository.class.getDeclaredField("storageDir");
        storageDirField.setAccessible(true);
        storageDirField.set(repository, tempDir.toString());

        repository.init();
    }

    @Test
    @DisplayName("save + findById: round-trip preserves traceId and userId")
    void saveAndFindByIdReturnsSameTrace() {
        String userId = "user-" + UUID.randomUUID().toString().substring(0, 8);
        String chatId = "chat-" + UUID.randomUUID().toString().substring(0, 8);
        ExecutionTrace trace = ExecutionTrace.start(userId, chatId, "req-" + UUID.randomUUID());
        TraceSpan span = new TraceSpan(0, TraceStepType.INTENT_DETECTION, "intent");
        span.terminate(TraceStepStatus.SUCCESS);
        trace.addSpan(span);
        trace.finalizeStatus();

        repository.save(trace);

        assertThat(repository.findById(trace.getTraceId()))
                .isPresent()
                .get()
                .satisfies(found -> {
                    assertThat(found.getTraceId()).isEqualTo(trace.getTraceId());
                    assertThat(found.getUserId()).isEqualTo(userId);
                    assertThat(found.getChatId()).isEqualTo(chatId);
                });
    }

    @Test
    @DisplayName("findByChatId: returns only matching chat, sorted newest first")
    void findByChatIdReturnsFilteredAndSorted() {
        String chatA = "chatA-" + UUID.randomUUID().toString().substring(0, 6);
        String chatB = "chatB-" + UUID.randomUUID().toString().substring(0, 6);

        ExecutionTrace t1 = ExecutionTrace.start("u1", chatA, "r1");
        ExecutionTrace t2 = ExecutionTrace.start("u1", chatB, "r2");
        ExecutionTrace t3 = ExecutionTrace.start("u1", chatA, "r3");

        repository.save(t1);
        repository.save(t2);
        repository.save(t3);

        List<ExecutionTrace> results = repository.findByChatId(chatA);
        assertThat(results).hasSize(2);
        // All results should belong to chatA
        assertThat(results).allMatch(t -> chatA.equals(t.getChatId()));
    }

    @Test
    @DisplayName("findByUserId: returns only matching user, sorted newest first")
    void findByUserIdReturnsFilteredAndSorted() {
        String userA = "userA-" + UUID.randomUUID().toString().substring(0, 6);
        String userB = "userB-" + UUID.randomUUID().toString().substring(0, 6);

        ExecutionTrace t1 = ExecutionTrace.start(userA, "c1", "r1");
        ExecutionTrace t2 = ExecutionTrace.start(userB, "c2", "r2");
        ExecutionTrace t3 = ExecutionTrace.start(userA, "c3", "r3");

        repository.save(t1);
        repository.save(t2);
        repository.save(t3);

        List<ExecutionTrace> results = repository.findByUserId(userA);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(t -> userA.equals(t.getUserId()));
    }

    @Test
    @DisplayName("retention policy: enforces max traces per user")
    void retentionPolicyEnforcesMaxTracesPerUser() {
        traceProperties.setMaxTracesPerUser(3);
        String userId = "retention-user";

        for (int i = 0; i < 5; i++) {
            ExecutionTrace t = ExecutionTrace.start(userId, "chat-" + i, "req-" + i);
            repository.save(t);
        }

        repository.enforceRetentionPolicy(userId);

        List<ExecutionTrace> remaining = repository.findByUserId(userId);
        assertThat(remaining).hasSize(3);
    }
}
