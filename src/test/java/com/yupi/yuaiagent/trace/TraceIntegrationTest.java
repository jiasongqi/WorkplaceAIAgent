package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.trace.model.*;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for trace collection in the orchestrator pipeline.
 * Verifies all 10 step types are recorded and trace lifecycle is correct.
 *
 * @author jsq
 */
@SpringBootTest
class TraceIntegrationTest {

    @Resource
    private TraceRecorder traceRecorder;

    @Resource
    private TraceRepository traceRepository;

    /**
     * Task 8.7: Verify all 10+ step types can be recorded without error.
     */
    @Test
    void allStepTypes_canBeRecorded() {
        TraceContext ctx = traceRecorder.startTrace("test-user", "test-chat", "req-8.7");

        for (TraceStepType stepType : TraceStepType.values()) {
            TraceSpan span = traceRecorder.startSpan(ctx, stepType, "测试 " + stepType.getDisplayName());
            assertNotNull(span, "Span should be created for " + stepType);
            traceRecorder.endSpan(ctx, span);
        }

        traceRecorder.endTrace(ctx);
        assertEquals(TraceStatus.SUCCESS, ctx.getTrace().getStatus());
        assertTrue(ctx.getTrace().getSpans().size() >= TraceStepType.values().length);
    }

    /**
     * Task 8.7: Verify metadata is recorded on spans.
     */
    @Test
    void spanMetadata_isRecorded() {
        TraceContext ctx = traceRecorder.startTrace("test-user", "test-chat", "req-8.7-meta");

        TraceSpan span = traceRecorder.startSpan(ctx, TraceStepType.INTENT_DETECTION, "意图识别");
        traceRecorder.putMetadata(span, "intent", "RESUME");
        traceRecorder.putMetadata(span, "confidence", "0.92");
        traceRecorder.endSpan(ctx, span);

        traceRecorder.endTrace(ctx);

        Map<String, String> meta = ctx.getTrace().getSpans().get(0).getMetadata();
        assertEquals("RESUME", meta.get("intent"));
        assertEquals("0.92", meta.get("confidence"));
    }

    /**
     * Task 8.8: Verify trace recording does not throw exceptions (non-intrusive).
     * Even with bad inputs, the recorder should never crash the main flow.
     */
    @Test
    void recorder_neverThrows_onBadInput() {
        // null context
        assertDoesNotThrow(() -> traceRecorder.endTrace(null));
        assertDoesNotThrow(() -> traceRecorder.failTrace(null));

        // null span
        TraceContext ctx = traceRecorder.startTrace("user", "chat", "req-8.8");
        assertDoesNotThrow(() -> traceRecorder.endSpan(ctx, null));
        assertDoesNotThrow(() -> traceRecorder.failSpan(ctx, null, "error"));

        // empty metadata
        TraceSpan span = traceRecorder.startSpan(ctx, TraceStepType.TOOL_CALL, "工具调用");
        assertDoesNotThrow(() -> traceRecorder.putMetadata(null, null, null));
        assertDoesNotThrow(() -> traceRecorder.putMetadata(span, "", ""));
        traceRecorder.endSpan(ctx, span);

        traceRecorder.endTrace(ctx);
    }

    /**
     * Task 8.9: Verify save → findById round-trip.
     */
    @Test
    void trace_persistedAndLoaded() {
        String traceId = "persist-test-" + System.currentTimeMillis();
        TraceContext ctx = traceRecorder.startTrace("persist-user", "persist-chat", traceId);

        TraceSpan span = traceRecorder.startSpan(ctx, TraceStepType.SKILL_MATCH, "技能匹配");
        traceRecorder.putMetadata(span, "result", "no_match");
        traceRecorder.endSpan(ctx, span);

        traceRecorder.endTrace(ctx);

        // Persist
        traceRepository.save(ctx.getTrace());

        // Load
        var loaded = traceRepository.findById(traceId);
        assertTrue(loaded.isPresent(), "Trace should be found after save");
        assertEquals(traceId, loaded.get().getTraceId());
        assertEquals(TraceStatus.SUCCESS, loaded.get().getStatus());
        assertEquals(1, loaded.get().getSpans().size());
        assertEquals("no_match", loaded.get().getSpans().get(0).getMetadata().get("result"));
    }

    /**
     * Task 8.10: Verify single trace event latency ≤ 50ms.
     */
    @Test
    void recorder_singleEvent_under50ms() {
        TraceContext ctx = traceRecorder.startTrace("perf-user", "perf-chat", "req-8.10");

        // Warm up
        for (int i = 0; i < 10; i++) {
            TraceSpan s = traceRecorder.startSpan(ctx, TraceStepType.TOOL_CALL, "warmup");
            traceRecorder.endSpan(ctx, s);
        }

        // Measure 100 events
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            TraceSpan s = traceRecorder.startSpan(ctx, TraceStepType.TOOL_CALL, "perf");
            traceRecorder.putMetadata(s, "key", "value");
            traceRecorder.endSpan(ctx, s);
        }
        long elapsed = System.nanoTime() - start;
        long avgMicros = elapsed / 100 / 1000;

        assertTrue(avgMicros < 50_000, // 50ms = 50,000 μs
            "Average span record time should be < 50ms, was " + avgMicros + "μs");

        traceRecorder.endTrace(ctx);
    }
}
