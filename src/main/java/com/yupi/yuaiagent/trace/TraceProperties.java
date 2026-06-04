package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.trace.model.TraceConstants;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 执行轨迹可配置项。
 * <p>
 * 绑定 {@code trace.*} 配置前缀，在 {@link #clampToValidRanges()} 中将各项取值钳制到合法范围，
 * 防止极端配置导致内存溢出或功能失效（Req 11.1 / 11.3 / 11.5）。
 * <p>
 * 默认值：
 * <ul>
 *   <li>{@code streamEnabled}：{@code true}（Req 9.5）</li>
 *   <li>{@code maxSpansPerTrace}：{@code 200}，范围 [1, 1000]（Req 11.1）</li>
 *   <li>{@code metadataMaxValueChars}：{@code 2000}，范围 [1, 4096]（Req 11.3）</li>
 *   <li>{@code maxTracesPerUser}：{@code 500}，范围 [1, 100000]（Req 11.5）</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "trace")
public class TraceProperties {

    /**
     * 实时轨迹事件流开关，默认启用（Req 9.5）。
     * 关闭后 {@link TraceStreamPublisher} 不推送任何 SSE trace 事件，但持久化采集不受影响。
     */
    private boolean streamEnabled = true;

    /**
     * 单条轨迹允许追加的最大步骤数，范围 [1, 1000]，默认 200（Req 11.1）。
     * 超过此上限后新步骤被丢弃，已有步骤不受影响。
     * 绝对上限由 {@link TraceConstants#ABSOLUTE_MAX_SPANS} 保证，不受此配置影响。
     */
    private int maxSpansPerTrace = 200;

    /**
     * 步骤 metadata 单个值的最大 Unicode 码点数，范围 [1, 4096]，默认 2000（Req 11.3）。
     * 超出部分按码点截断，避免把代理对（emoji 等）截成半个字符（Req 11.4）。
     */
    private int metadataMaxValueChars = 2000;

    /**
     * 单个 userId 在仓库中保留的最大轨迹条数，范围 [1, 100000]，默认 500（Req 11.5）。
     * 超出上限时按 startTime 升序删除最早的轨迹，直到不超过上限（Req 11.6）。
     */
    private int maxTracesPerUser = 500;

    /**
     * 将各配置项钳制到合法范围（Req 11.1 / 11.3 / 11.5）。
     * <p>
     * 在 Spring 完成属性绑定后自动执行，确保无论外部配置传入何值，
     * 运行时使用的值都在安全范围内。
     */
    @PostConstruct
    public void clampToValidRanges() {
        int originalMaxSpans = maxSpansPerTrace;
        int originalMaxValueChars = metadataMaxValueChars;
        int originalMaxTracesPerUser = maxTracesPerUser;

        maxSpansPerTrace = clamp(maxSpansPerTrace, 1, 1000);
        metadataMaxValueChars = clamp(metadataMaxValueChars, 1, 4096);
        maxTracesPerUser = clamp(maxTracesPerUser, 1, 100_000);

        if (originalMaxSpans != maxSpansPerTrace) {
            log.warn("[trace] maxSpansPerTrace={} 超出范围 [1,1000]，已钳制为 {}",
                    originalMaxSpans, maxSpansPerTrace);
        }
        if (originalMaxValueChars != metadataMaxValueChars) {
            log.warn("[trace] metadataMaxValueChars={} 超出范围 [1,4096]，已钳制为 {}",
                    originalMaxValueChars, metadataMaxValueChars);
        }
        if (originalMaxTracesPerUser != maxTracesPerUser) {
            log.warn("[trace] maxTracesPerUser={} 超出范围 [1,100000]，已钳制为 {}",
                    originalMaxTracesPerUser, maxTracesPerUser);
        }

        log.info("[trace] 配置加载完成：streamEnabled={}, maxSpansPerTrace={}, " +
                        "metadataMaxValueChars={}, maxTracesPerUser={}",
                streamEnabled, maxSpansPerTrace, metadataMaxValueChars, maxTracesPerUser);
    }

    /**
     * 将值 {@code v} 钳制到 [{@code lo}, {@code hi}] 范围内。
     */
    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
