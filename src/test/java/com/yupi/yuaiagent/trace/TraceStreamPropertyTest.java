package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.trace.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for P2 real-time trace event streaming (任务 10.3).
 * <p>
 * Verifies: publishing fail-safe, stream toggle, SSE binding.
 */
class TraceStreamPropertyTest {

    private TraceRecorder recorder;
    private TraceProperties traceProperties;

    @BeforeEach
    void setUp() {
        traceProperties = new TraceProperties();
        recorder = createRecorder(traceProperties);
    }

    private TraceRecorder createRecorder(TraceProperties props) {
        TraceRecorder r = new TraceRecorder();
        try {
            var propsField = TraceRecorder.class.getDeclaredField("traceProperties");
            propsField.setAccessible(true);
            propsField.set(r, props);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SSE binding tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TraceContext bindSseEmitter 后 getSseEmitter 返回同一实例")
    void bindSseEmitterReturnsSameInstance() {
        TraceContext ctx = TraceContext.of(ExecutionTrace.start("u1", "c1", "r1"));
        SseEmitter emitter = new SseEmitter();
        ctx.bindSseEmitter(emitter);
        assertThat(ctx.getSseEmitter()).isSameAs(emitter);
    }

    @Test
    @DisplayName("TraceContext 初始状态 sseClosed 为 false")
    void initialSseClosedIsFalse() {
        TraceContext ctx = TraceContext.of(ExecutionTrace.start("u1", "c1", "r1"));
        assertThat(ctx.isSseClosed()).isFalse();
    }

    @Test
    @DisplayName("TraceContext markSseClosed 后 isSseClosed 为 true")
    void markSseClosedSetsFlag() {
        TraceContext ctx = TraceContext.of(ExecutionTrace.start("u1", "c1", "r1"));
        ctx.markSseClosed();
        assertThat(ctx.isSseClosed()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Publishing fail-safe: null emitter / closed SSE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("无 SSE 绑定时 startSpan/endSpan 不抛异常")
    void spanLifecycleWithoutSseDoesNotThrow() {
        TraceContext ctx = recorder.startTrace("u1", "c1", "r1");
        TraceSpan span = recorder.startSpan(ctx, TraceStepType.TOOL_CALL, "test");
        recorder.endSpan(ctx, span);
        // No exception means pass
        assertThat(span.getStatus()).isEqualTo(TraceStepStatus.SUCCESS);
    }

    @Test
    @DisplayName("SSE 关闭后 startSpan/endSpan 不抛异常")
    void spanLifecycleAfterSseClosedDoesNotThrow() {
        TraceContext ctx = recorder.startTrace("u1", "c1", "r1");
        ctx.bindSseEmitter(new SseEmitter());
        ctx.markSseClosed();

        TraceSpan span = recorder.startSpan(ctx, TraceStepType.TOOL_CALL, "test");
        recorder.endSpan(ctx, span);
        assertThat(span.getStatus()).isEqualTo(TraceStepStatus.SUCCESS);
    }

    @Test
    @DisplayName("streamEnabled=false 时 startSpan/endSpan 不抛异常")
    void spanLifecycleWithStreamDisabledDoesNotThrow() {
        traceProperties.setStreamEnabled(false);

        TraceContext ctx = recorder.startTrace("u1", "c1", "r1");
        ctx.bindSseEmitter(new SseEmitter());

        TraceSpan span = recorder.startSpan(ctx, TraceStepType.TOOL_CALL, "test");
        recorder.endSpan(ctx, span);
        assertThat(span.getStatus()).isEqualTo(TraceStepStatus.SUCCESS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Noop context + SSE binding
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("noop context 绑定 SSE 后 startSpan 仍返回 null")
    void noopContextWithSseReturnsNull() {
        TraceContext noop = TraceContext.noop();
        noop.bindSseEmitter(new SseEmitter());
        TraceSpan span = recorder.startSpan(noop, TraceStepType.TOOL_CALL, "test");
        assertThat(span).isNull();
    }
}
