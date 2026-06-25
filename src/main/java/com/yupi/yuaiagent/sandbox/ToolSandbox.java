package com.yupi.yuaiagent.sandbox;

/**
 * Tool 沙箱接口 — 所有沙箱实现的统一入口。
 * <p>
 * 通过 {@link SandboxFactory} 根据环境自动选择合适的实现。
 *
 * @author jsq
 */
public interface ToolSandbox {

    /**
     * 在沙箱中执行命令
     *
     * @param request 沙箱请求（命令、超时、资源限制等）
     * @return 执行结果
     */
    SandboxResult execute(SandboxRequest request);

    /**
     * 获取沙箱类型标识
     */
    SandboxPolicy getPolicy();
}
