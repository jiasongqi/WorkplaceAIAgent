package com.yupi.yuaiagent.sandbox;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * 沙箱执行请求
 *
 * @author jsq
 */
@Data
@Builder
public class SandboxRequest {

    /**
     * 要执行的命令
     */
    private String command;

    /**
     * 工作目录（相对于沙箱根目录）
     */
    @Builder.Default
    private String workDir = "default";

    /**
     * 执行超时
     */
    @Builder.Default
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * 资源限制
     */
    private ResourceLimits resourceLimits;

    /**
     * 任务 ID（用于隔离工作目录）
     */
    private String taskId;
}
