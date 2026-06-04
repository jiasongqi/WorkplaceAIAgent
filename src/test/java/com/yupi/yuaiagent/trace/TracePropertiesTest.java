package com.yupi.yuaiagent.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TraceProperties 默认值单元测试（任务 1.4）。
 * <p>
 * 验证 {@link TraceProperties} 的默认值与钳制行为符合设计文档要求。
 *
 * <p><b>Validates: Requirements 3.9, 9.5, 11.1, 11.3, 11.5</b>
 */
class TracePropertiesTest {

    // ─────────────────────────────────────────────────────────────────────────
    // 默认值验证（Req 3.9, 9.5, 11.1, 11.3, 11.5）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("默认值：streamEnabled 应为 true（Req 9.5）")
    void defaultStreamEnabledIsTrue() {
        TraceProperties props = new TraceProperties();
        assertThat(props.isStreamEnabled()).isTrue();
    }

    @Test
    @DisplayName("默认值：maxSpansPerTrace 应为 200（Req 11.1）")
    void defaultMaxSpansPerTraceIs200() {
        TraceProperties props = new TraceProperties();
        assertThat(props.getMaxSpansPerTrace()).isEqualTo(200);
    }

    @Test
    @DisplayName("默认值：metadataMaxValueChars 应为 2000（Req 11.3）")
    void defaultMetadataMaxValueCharsIs2000() {
        TraceProperties props = new TraceProperties();
        assertThat(props.getMetadataMaxValueChars()).isEqualTo(2000);
    }

    @Test
    @DisplayName("默认值：maxTracesPerUser 应为 500（Req 11.5）")
    void defaultMaxTracesPerUserIs500() {
        TraceProperties props = new TraceProperties();
        assertThat(props.getMaxTracesPerUser()).isEqualTo(500);
    }

    @Test
    @DisplayName("默认值经钳制后保持不变（所有默认值均在合法范围内）")
    void defaultValuesRemainUnchangedAfterClamping() {
        TraceProperties props = new TraceProperties();
        props.clampToValidRanges();

        assertThat(props.isStreamEnabled()).isTrue();
        assertThat(props.getMaxSpansPerTrace()).isEqualTo(200);
        assertThat(props.getMetadataMaxValueChars()).isEqualTo(2000);
        assertThat(props.getMaxTracesPerUser()).isEqualTo(500);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 边界值钳制（Req 11.1, 11.3, 11.5）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("maxSpansPerTrace 下界：0 → 钳制为 1")
    void maxSpansPerTraceClampedFromZeroToOne() {
        TraceProperties props = new TraceProperties();
        props.setMaxSpansPerTrace(0);
        props.clampToValidRanges();
        assertThat(props.getMaxSpansPerTrace()).isEqualTo(1);
    }

    @Test
    @DisplayName("maxSpansPerTrace 上界：1001 → 钳制为 1000")
    void maxSpansPerTraceClampedFromOverflowTo1000() {
        TraceProperties props = new TraceProperties();
        props.setMaxSpansPerTrace(1001);
        props.clampToValidRanges();
        assertThat(props.getMaxSpansPerTrace()).isEqualTo(1000);
    }

    @Test
    @DisplayName("maxSpansPerTrace 边界值：1 和 1000 保持不变")
    void maxSpansPerTraceBoundaryValuesUnchanged() {
        TraceProperties propsLow = new TraceProperties();
        propsLow.setMaxSpansPerTrace(1);
        propsLow.clampToValidRanges();
        assertThat(propsLow.getMaxSpansPerTrace()).isEqualTo(1);

        TraceProperties propsHigh = new TraceProperties();
        propsHigh.setMaxSpansPerTrace(1000);
        propsHigh.clampToValidRanges();
        assertThat(propsHigh.getMaxSpansPerTrace()).isEqualTo(1000);
    }

    @Test
    @DisplayName("metadataMaxValueChars 下界：-1 → 钳制为 1")
    void metadataMaxValueCharsClampedFromNegativeToOne() {
        TraceProperties props = new TraceProperties();
        props.setMetadataMaxValueChars(-1);
        props.clampToValidRanges();
        assertThat(props.getMetadataMaxValueChars()).isEqualTo(1);
    }

    @Test
    @DisplayName("metadataMaxValueChars 上界：5000 → 钳制为 4096")
    void metadataMaxValueCharsClampedFromOverflowTo4096() {
        TraceProperties props = new TraceProperties();
        props.setMetadataMaxValueChars(5000);
        props.clampToValidRanges();
        assertThat(props.getMetadataMaxValueChars()).isEqualTo(4096);
    }

    @Test
    @DisplayName("maxTracesPerUser 下界：Integer.MIN_VALUE → 钳制为 1")
    void maxTracesPerUserClampedFromMinValueToOne() {
        TraceProperties props = new TraceProperties();
        props.setMaxTracesPerUser(Integer.MIN_VALUE);
        props.clampToValidRanges();
        assertThat(props.getMaxTracesPerUser()).isEqualTo(1);
    }

    @Test
    @DisplayName("maxTracesPerUser 上界：Integer.MAX_VALUE → 钳制为 100000")
    void maxTracesPerUserClampedFromMaxValueTo100000() {
        TraceProperties props = new TraceProperties();
        props.setMaxTracesPerUser(Integer.MAX_VALUE);
        props.clampToValidRanges();
        assertThat(props.getMaxTracesPerUser()).isEqualTo(100_000);
    }

    @Test
    @DisplayName("maxTracesPerUser 边界值：1 和 100000 保持不变")
    void maxTracesPerUserBoundaryValuesUnchanged() {
        TraceProperties propsLow = new TraceProperties();
        propsLow.setMaxTracesPerUser(1);
        propsLow.clampToValidRanges();
        assertThat(propsLow.getMaxTracesPerUser()).isEqualTo(1);

        TraceProperties propsHigh = new TraceProperties();
        propsHigh.setMaxTracesPerUser(100_000);
        propsHigh.clampToValidRanges();
        assertThat(propsHigh.getMaxTracesPerUser()).isEqualTo(100_000);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // streamEnabled 开关（Req 9.5）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("streamEnabled 可设置为 false，钳制不影响布尔值")
    void streamEnabledCanBeSetToFalse() {
        TraceProperties props = new TraceProperties();
        props.setStreamEnabled(false);
        props.clampToValidRanges();
        assertThat(props.isStreamEnabled()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 幂等性：多次调用 clampToValidRanges 结果一致
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clampToValidRanges 幂等：多次调用结果一致")
    void clampToValidRangesIsIdempotent() {
        TraceProperties props = new TraceProperties();
        props.setMaxSpansPerTrace(9999);
        props.setMetadataMaxValueChars(-100);
        props.setMaxTracesPerUser(0);

        props.clampToValidRanges();
        int spans1 = props.getMaxSpansPerTrace();
        int meta1 = props.getMetadataMaxValueChars();
        int traces1 = props.getMaxTracesPerUser();

        props.clampToValidRanges();
        assertThat(props.getMaxSpansPerTrace()).isEqualTo(spans1);
        assertThat(props.getMetadataMaxValueChars()).isEqualTo(meta1);
        assertThat(props.getMaxTracesPerUser()).isEqualTo(traces1);
    }
}
