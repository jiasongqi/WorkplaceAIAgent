package com.yupi.yuaiagent.trace.model;

/**
 * 执行轨迹相关硬上限常量（绝对上限，独立于可配置项）。
 * <p>
 * 这些常量是系统级硬约束，不受 {@link com.yupi.yuaiagent.trace.TraceProperties} 配置影响，
 * 用于在 {@link com.yupi.yuaiagent.trace.TraceContext} 与
 * {@link com.yupi.yuaiagent.trace.TraceRecorder} 中做最终兜底校验。
 *
 * @author jsq
 */
public final class TraceConstants {

    /**
     * 单条轨迹 spans 列表的绝对上限（Req 1.1）。
     * 即使配置项 {@code trace.max-spans-per-trace} 设置更大的值，也不会超过此上限。
     */
    public static final int ABSOLUTE_MAX_SPANS = 1000;

    /**
     * 单个步骤 metadata 的最大键数（Req 1.6）。
     */
    public static final int MAX_METADATA_ENTRIES = 50;

    /**
     * metadata 键的最大字符数（Req 1.6）。
     */
    public static final int MAX_METADATA_KEY_CHARS = 128;

    /**
     * 步骤错误信息的最大 Unicode 码点数（Req 5.3）。
     */
    public static final int MAX_ERROR_CHARS = 2048;

    private TraceConstants() {
        // 工具类，禁止实例化
    }
}
