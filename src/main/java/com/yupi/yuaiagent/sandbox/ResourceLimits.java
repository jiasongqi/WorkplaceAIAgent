package com.yupi.yuaiagent.sandbox;

import lombok.Builder;
import lombok.Data;

/**
 * 沙箱资源限制配置
 *
 * @author jsq
 */
@Data
@Builder
public class ResourceLimits {

    /**
     * 最大输出大小（字节），超出后截断。默认 10MB
     */
    @Builder.Default
    private long maxOutputBytes = 10 * 1024 * 1024L;

    /**
     * 最大内存（MB），Docker 模式下使用 --memory 限制
     */
    @Builder.Default
    private int maxMemoryMb = 256;

    /**
     * CPU 核数限制，Docker 模式下使用 --cpus 限制
     */
    @Builder.Default
    private double maxCpus = 1.0;

    /**
     * 是否禁止网络访问
     */
    @Builder.Default
    private boolean networkDisabled = true;

    /**
     * 默认资源限制
     */
    public static ResourceLimits defaults() {
        return ResourceLimits.builder().build();
    }
}
