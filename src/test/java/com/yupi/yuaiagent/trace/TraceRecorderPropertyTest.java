package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.trace.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for TraceRecorder (任务 4.3, 4.4, 4.5, 4.6, 4.7).
 */
class TraceRecorderPropertyTest {

    private final TraceRecorder recorder = createRecorder();

    private TraceRecorder createRecorder() {
        TraceRecorder r = new TraceRecorder();
        TraceProperties props = new TraceProperties();
        // Inject via reflection (no Spring context needed)
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
    // Property 7: 标识全局唯一（任务 4.3）
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 200)
    void traceIdsAreGloballyUnique(
            @ForAll("alphas") String userId,
            @ForAll("alphas") String chatId) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            TraceContext ctx = recorder.startTrace(userId, chatId, UUID.randomUUID().toString());
            String traceId = ctx.getTrace().getTraceId();
            assertThat(ids.add(traceId))
                    .as("traceId %s should be unique", traceId)
                    .isTrue();
        }
    }

    @Provide
    Arbitrary<String> alphas() {
        return Arbitraries.strings().withCharRange('a', 'z').ofLength(6);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 5: 错误信息非空且有界（任务 4.4）
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 200)
    void errorMessageIsBounded(
            @ForAll @StringLength(min = 0, max = 5000) String rawError) {
        TraceContext ctx = recorder.startTrace("u1", "c1", "r1");
        TraceSpan span = recorder.startSpan(ctx, TraceStepType.TOOL_CALL, "test");

        recorder.failSpan(ctx, span, rawError);

        String errorMsg = span.getErrorMessage();
        if (rawError == null || rawError.isEmpty()) {
            assertThat(errorMsg).isEmpty();
        } else {
            assertThat(errorMsg).isNotNull();
            assertThat(errorMsg.codePointCount(0, errorMsg.length()))
                    .as("Error message should be bounded by MAX_ERROR_CHARS=%d", TraceConstants.MAX_ERROR_CHARS)
                    .isLessThanOrEqualTo(TraceConstants.MAX_ERROR_CHARS);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 6: metadata 限额与码点截断（任务 4.5）
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 200)
    void metadataKeyIsBoundedBy128(
            @ForAll @StringLength(min = 1, max = 300) String key,
            @ForAll @StringLength(min = 1, max = 100) String value) {
        TraceContext ctx = recorder.startTrace("u1", "c1", "r1");
        TraceSpan span = recorder.startSpan(ctx, TraceStepType.TOOL_CALL, "test");

        recorder.putMetadata(span, key, value);

        String safeKey = span.getMetadata().keySet().iterator().next();
        assertThat(safeKey.codePointCount(0, safeKey.length()))
                .isLessThanOrEqualTo(TraceConstants.MAX_METADATA_KEY_CHARS);
    }

    @Property(tries = 200)
    void metadataValueIsBoundedByConfiguredMax(
            @ForAll @StringLength(min = 1, max = 20) String key,
            @ForAll @StringLength(min = 1, max = 6000) String value) {
        TraceContext ctx = recorder.startTrace("u1", "c1", "r1");
        TraceSpan span = recorder.startSpan(ctx, TraceStepType.TOOL_CALL, "test");

        recorder.putMetadata(span, key, value);

        String safeValue = span.getMetadata().values().iterator().next();
        assertThat(safeValue.codePointCount(0, safeValue.length()))
                .as("Value should be bounded by metadataMaxValueChars=%d", 2000)
                .isLessThanOrEqualTo(2000);
    }

    @Property(tries = 50)
    void metadataEntryCountIsBounded(
            @ForAll @IntRange(min = 50, max = 80) int entryCount) {
        TraceContext ctx = recorder.startTrace("u1", "c1", "r1");
        TraceSpan span = recorder.startSpan(ctx, TraceStepType.TOOL_CALL, "test");

        for (int i = 0; i < entryCount; i++) {
            recorder.putMetadata(span, "key" + i, "val" + i);
        }

        assertThat(span.getMetadata().size())
                .as("Metadata entries should not exceed MAX_METADATA_ENTRIES=%d", TraceConstants.MAX_METADATA_ENTRIES)
                .isLessThanOrEqualTo(TraceConstants.MAX_METADATA_ENTRIES);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 15: 记录器容错——绝不向主流程抛异常（任务 4.6）
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void recorderNeverThrowsOnNullContext() {
        // All methods with null context should be silent
        recorder.startSpan(null, TraceStepType.TOOL_CALL, "label");
        recorder.endSpan(null, null);
        recorder.failSpan(null, null, "error");
        recorder.skipSpan(null, null);
        recorder.endTrace(null);
        recorder.failTrace(null);
        recorder.putMetadata(null, "k", "v");
        // If we reach here, no exception was thrown
    }

    @Property(tries = 100)
    void recorderNeverThrowsOnNullSpan() {
        TraceContext ctx = recorder.startTrace("u1", "c1", "r1");
        recorder.endSpan(ctx, null);
        recorder.failSpan(ctx, null, "error");
        recorder.skipSpan(ctx, null);
        recorder.putMetadata(null, "k", "v");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 单元测试：TraceRecorder 三态（任务 4.7）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startSpan → endSpan: span 变为 SUCCESS")
    void spanEndsWithSuccess() {
        TraceContext ctx = recorder.startTrace("u1", "c1", "r1");
        TraceSpan span = recorder.startSpan(ctx, TraceStepType.TOOL_CALL, "test");
        recorder.endSpan(ctx, span);

        assertThat(span.getStatus()).isEqualTo(TraceStepStatus.SUCCESS);
        assertThat(span.isTerminal()).isTrue();
        assertThat(span.getEndTime()).isNotNull();
    }

    @Test
    @DisplayName("startSpan → failSpan: span 变为 FAILED")
    void spanEndsWithFailed() {
        TraceContext ctx = recorder.startTrace("u1", "c1", "r1");
        TraceSpan span = recorder.startSpan(ctx, TraceStepType.TOOL_CALL, "test");
        recorder.failSpan(ctx, span, "something broke");

        assertThat(span.getStatus()).isEqualTo(TraceStepStatus.FAILED);
        assertThat(span.getErrorMessage()).isEqualTo("something broke");
    }

    @Test
    @DisplayName("startSpan → skipSpan: span 变为 SKIPPED")
    void spanEndsWithSkipped() {
        TraceContext ctx = recorder.startTrace("u1", "c1", "r1");
        TraceSpan span = recorder.startSpan(ctx, TraceStepType.SKILL_MATCH, "test");
        recorder.skipSpan(ctx, span);

        assertThat(span.getStatus()).isEqualTo(TraceStepStatus.SKIPPED);
        assertThat(span.isTerminal()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // truncateToCodepoints 边界测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("truncateToCodepoints handles null")
    void truncateNullReturnsEmpty() {
        assertThat(TraceRecorder.truncateToCodepoints(null, 10)).isEmpty();
    }

    @Test
    @DisplayName("truncateToCodepoints preserves short strings")
    void truncateShortStringUnchanged() {
        assertThat(TraceRecorder.truncateToCodepoints("hello", 10)).isEqualTo("hello");
    }

    @Test
    @DisplayName("truncateToCodepoints truncates at codepoint boundary")
    void truncateAtCodepointBoundary() {
        // emoji is 2 code units but 1 codepoint
        String withEmoji = "hello😀world";
        String truncated = TraceRecorder.truncateToCodepoints(withEmoji, 6);
        assertThat(truncated.codePointCount(0, truncated.length())).isEqualTo(6);
        assertThat(truncated).isEqualTo("hello😀");
    }
}
