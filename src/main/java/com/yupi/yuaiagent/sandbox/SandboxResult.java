package com.yupi.yuaiagent.sandbox;

import lombok.Builder;
import lombok.Data;

/**
 * 沙箱执行结果
 *
 * @author jsq
 */
@Data
@Builder
public class SandboxResult {

    /**
     * 进程退出码（0 = 成功）
     */
    private int exitCode;

    /**
     * 标准输出（可能被截断）
     */
    private String stdout;

    /**
     * 标准错误输出（可能被截断）
     */
    private String stderr;

    /**
     * 是否因超时被强制终止
     */
    private boolean killed;

    /**
     * 是否因资源超限被终止
     */
    private boolean resourceLimitExceeded;

    /**
     * 执行耗时（毫秒）
     */
    private long executionTimeMs;

    /**
     * 输出是否被截断
     */
    private boolean outputTruncated;

    /**
     * 沙箱类型（docker / local-process）
     */
    private String sandboxType;

    /**
     * 错误信息（如有）
     */
    private String errorMessage;

    public boolean isSuccess() {
        return exitCode == 0 && !killed && !resourceLimitExceeded;
    }

    public static SandboxResult failure(String errorMessage) {
        return SandboxResult.builder()
                .exitCode(-1)
                .errorMessage(errorMessage)
                .stdout("")
                .stderr(errorMessage)
                .build();
    }
}
