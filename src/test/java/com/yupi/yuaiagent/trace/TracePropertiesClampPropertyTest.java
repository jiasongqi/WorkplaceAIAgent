package com.yupi.yuaiagent.trace;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 12: 配置取值范围钳制
 * <p>
 * For any 配置输入整数，{@link TraceProperties} 钳制后的值满足：
 * <ul>
 *   <li>{@code maxSpansPerTrace} 落在 [1, 1000]</li>
 *   <li>{@code metadataMaxValueChars} 落在 [1, 4096]</li>
 *   <li>{@code maxTracesPerUser} 落在 [1, 100000]</li>
 * </ul>
 *
 * <p><b>Feature: agent-execution-trace, Property 12: 配置取值范围钳制</b>
 * <p><b>Validates: Requirements 11.1, 11.3, 11.5</b>
 */
class TracePropertiesClampPropertyTest {

    /**
     * 任意整数（含负数、零、超界大值）输入后，maxSpansPerTrace 钳制结果落在 [1, 1000]。
     *
     * <p>// Feature: agent-execution-trace, Property 12: 配置取值范围钳制
     */
    @Property(tries = 200)
    void maxSpansPerTraceIsClampedToValidRange(
            @ForAll @IntRange(min = Integer.MIN_VALUE, max = Integer.MAX_VALUE) int rawValue) {
        TraceProperties props = new TraceProperties();
        props.setMaxSpansPerTrace(rawValue);
        props.clampToValidRanges();

        assertThat(props.getMaxSpansPerTrace())
                .as("maxSpansPerTrace 应在 [1, 1000] 范围内，原始值=%d", rawValue)
                .isBetween(1, 1000);
    }

    /**
     * 任意整数输入后，metadataMaxValueChars 钳制结果落在 [1, 4096]。
     *
     * <p>// Feature: agent-execution-trace, Property 12: 配置取值范围钳制
     */
    @Property(tries = 200)
    void metadataMaxValueCharsIsClampedToValidRange(
            @ForAll @IntRange(min = Integer.MIN_VALUE, max = Integer.MAX_VALUE) int rawValue) {
        TraceProperties props = new TraceProperties();
        props.setMetadataMaxValueChars(rawValue);
        props.clampToValidRanges();

        assertThat(props.getMetadataMaxValueChars())
                .as("metadataMaxValueChars 应在 [1, 4096] 范围内，原始值=%d", rawValue)
                .isBetween(1, 4096);
    }

    /**
     * 任意整数输入后，maxTracesPerUser 钳制结果落在 [1, 100000]。
     *
     * <p>// Feature: agent-execution-trace, Property 12: 配置取值范围钳制
     */
    @Property(tries = 200)
    void maxTracesPerUserIsClampedToValidRange(
            @ForAll @IntRange(min = Integer.MIN_VALUE, max = Integer.MAX_VALUE) int rawValue) {
        TraceProperties props = new TraceProperties();
        props.setMaxTracesPerUser(rawValue);
        props.clampToValidRanges();

        assertThat(props.getMaxTracesPerUser())
                .as("maxTracesPerUser 应在 [1, 100000] 范围内，原始值=%d", rawValue)
                .isBetween(1, 100_000);
    }

    /**
     * 合法范围内的值经钳制后保持不变（恒等变换）。
     *
     * <p>// Feature: agent-execution-trace, Property 12: 配置取值范围钳制
     */
    @Property(tries = 200)
    void validValuesAreUnchangedAfterClamping(
            @ForAll @IntRange(min = 1, max = 1000) int validSpans,
            @ForAll @IntRange(min = 1, max = 4096) int validMetaChars,
            @ForAll @IntRange(min = 1, max = 100_000) int validMaxTraces) {
        TraceProperties props = new TraceProperties();
        props.setMaxSpansPerTrace(validSpans);
        props.setMetadataMaxValueChars(validMetaChars);
        props.setMaxTracesPerUser(validMaxTraces);
        props.clampToValidRanges();

        assertThat(props.getMaxSpansPerTrace())
                .as("合法值 maxSpansPerTrace=%d 不应被改变", validSpans)
                .isEqualTo(validSpans);
        assertThat(props.getMetadataMaxValueChars())
                .as("合法值 metadataMaxValueChars=%d 不应被改变", validMetaChars)
                .isEqualTo(validMetaChars);
        assertThat(props.getMaxTracesPerUser())
                .as("合法值 maxTracesPerUser=%d 不应被改变", validMaxTraces)
                .isEqualTo(validMaxTraces);
    }

    /**
     * 负数或零输入后，所有配置项钳制结果均 ≥ 1（下界保证）。
     *
     * <p>// Feature: agent-execution-trace, Property 12: 配置取值范围钳制
     */
    @Property(tries = 200)
    void nonPositiveValuesAreClampedToAtLeastOne(
            @ForAll @IntRange(min = Integer.MIN_VALUE, max = 0) int nonPositive) {
        TraceProperties props = new TraceProperties();
        props.setMaxSpansPerTrace(nonPositive);
        props.setMetadataMaxValueChars(nonPositive);
        props.setMaxTracesPerUser(nonPositive);
        props.clampToValidRanges();

        assertThat(props.getMaxSpansPerTrace()).isGreaterThanOrEqualTo(1);
        assertThat(props.getMetadataMaxValueChars()).isGreaterThanOrEqualTo(1);
        assertThat(props.getMaxTracesPerUser()).isGreaterThanOrEqualTo(1);
    }
}
