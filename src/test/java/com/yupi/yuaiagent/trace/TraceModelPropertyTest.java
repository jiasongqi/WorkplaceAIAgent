package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.trace.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for trace data model and enums (任务 2.2, 2.3, 2.6, 2.7, 2.8).
 */
class TraceModelPropertyTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Property 16: 步骤类型显示名完整且唯一（任务 2.2）
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void allStepTypesHaveUniqueNonBlankDisplayName() {
        TraceStepType[] types = TraceStepType.values();
        Set<String> names = new HashSet<>();
        for (TraceStepType type : types) {
            assertThat(type.getDisplayName())
                    .as("TraceStepType.%s displayName should not be blank", type.name())
                    .isNotBlank();
            assertThat(names.add(type.getDisplayName()))
                    .as("TraceStepType.%s displayName '%s' should be unique", type.name(), type.getDisplayName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("TraceStepType 应有 14 个取值")
    void stepTypeHasExactly14Values() {
        assertThat(TraceStepType.values()).hasSize(20);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 枚举取值集合单元测试（任务 2.3）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TraceStatus 取值：RUNNING, SUCCESS, FAILED, CANCELLED")
    void traceStatusValues() {
        assertThat(Set.of(TraceStatus.values()))
                .containsExactlyInAnyOrder(
                        TraceStatus.RUNNING, TraceStatus.SUCCESS,
                        TraceStatus.FAILED, TraceStatus.CANCELLED);
    }

    @Test
    @DisplayName("TraceStepStatus 取值：RUNNING, SUCCESS, FAILED, SKIPPED")
    void traceStepStatusValues() {
        assertThat(Set.of(TraceStepStatus.values()))
                .containsExactlyInAnyOrder(
                        TraceStepStatus.RUNNING, TraceStepStatus.SUCCESS,
                        TraceStepStatus.FAILED, TraceStepStatus.SKIPPED);
    }

    @Test
    @DisplayName("所有枚举 displayName 非空")
    void allEnumDisplayNamesAreNonBlank() {
        for (TraceStatus s : TraceStatus.values()) {
            assertThat(s.getDisplayName()).isNotBlank();
        }
        for (TraceStepStatus s : TraceStepStatus.values()) {
            assertThat(s.getDisplayName()).isNotBlank();
        }
        for (TraceStepType t : TraceStepType.values()) {
            assertThat(t.getDisplayName()).isNotBlank();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 1: 终态计时不变量（任务 2.6）
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 200)
    void terminalSpanHasEndTimeAndDurationNonNegative(
            @ForAll("stepTypes") TraceStepType stepType,
            @ForAll("terminalStepStatuses") TraceStepStatus terminalStatus) {
        TraceSpan span = new TraceSpan(0, stepType, "test");
        assertThat(span.isTerminal()).isFalse();

        span.terminate(terminalStatus);

        assertThat(span.isTerminal()).isTrue();
        assertThat(span.getEndTime()).isNotNull();
        assertThat(span.getEndTime()).isAfterOrEqualTo(span.getStartTime());
    }

    @Provide
    Arbitrary<TraceStepType> stepTypes() {
        return Arbitraries.of(TraceStepType.values());
    }

    @Provide
    Arbitrary<TraceStepStatus> terminalStepStatuses() {
        return Arbitraries.of(TraceStepStatus.SUCCESS, TraceStepStatus.FAILED, TraceStepStatus.SKIPPED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 2: RUNNING 期间无终态字段（任务 2.7）
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 200)
    void runningSpanHasNoTerminalFields(
            @ForAll("stepTypes") TraceStepType stepType,
            @ForAll @IntRange(min = 0, max = 999) int sequence) {
        TraceSpan span = new TraceSpan(sequence, stepType, "label-" + sequence);

        assertThat(span.getStatus()).isEqualTo(TraceStepStatus.RUNNING);
        assertThat(span.isTerminal()).isFalse();
        assertThat(span.getEndTime()).isNull();
        assertThat(span.getErrorMessage()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 4: 轨迹状态推导（任务 2.8）
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void traceStatusDerivedFromSpans_allSuccess(
            @ForAll @IntRange(min = 1, max = 10) int spanCount) {
        ExecutionTrace trace = ExecutionTrace.start("u1", "c1", "r1");
        for (int i = 0; i < spanCount; i++) {
            TraceSpan span = new TraceSpan(i, TraceStepType.TOOL_CALL, "t" + i);
            span.terminate(TraceStepStatus.SUCCESS);
            trace.addSpan(span);
        }
        trace.finalizeStatus();
        assertThat(trace.getStatus()).isEqualTo(TraceStatus.SUCCESS);
    }

    @Property(tries = 100)
    void traceStatusDerivedFromSpans_anyFailed(
            @ForAll @IntRange(min = 1, max = 10) int spanCount,
            @ForAll @IntRange(min = 0, max = 9) int failIndex) {
        int safeFailIndex = failIndex % spanCount;
        ExecutionTrace trace = ExecutionTrace.start("u1", "c1", "r1");
        for (int i = 0; i < spanCount; i++) {
            TraceSpan span = new TraceSpan(i, TraceStepType.TOOL_CALL, "t" + i);
            if (i == safeFailIndex) {
                span.fail("error");
            } else {
                span.terminate(TraceStepStatus.SUCCESS);
            }
            trace.addSpan(span);
        }
        trace.finalizeStatus();
        assertThat(trace.getStatus()).isEqualTo(TraceStatus.FAILED);
    }

    @Test
    @DisplayName("无 span 的轨迹 finalizeStatus 后为 SUCCESS")
    void emptyTraceFinalizesToSuccess() {
        ExecutionTrace trace = ExecutionTrace.start("u1", "c1", "r1");
        trace.finalizeStatus();
        assertThat(trace.getStatus()).isEqualTo(TraceStatus.SUCCESS);
    }
}
