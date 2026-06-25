package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.sandbox.SandboxFactory;
import com.yupi.yuaiagent.sandbox.SandboxRequest;
import com.yupi.yuaiagent.sandbox.SandboxResult;
import com.yupi.yuaiagent.sandbox.ToolSandbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Duration;

/**
 * 终端操作工具 — 通过沙箱执行命令，不再直接调用 ProcessBuilder。
 * <p>
 * 安全架构升级：
 * <ul>
 *     <li>Docker 可用时 → DockerSandbox（完全隔离）</li>
 *     <li>Docker 不可用时 → LocalProcessSandbox（5层防护降级方案）</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
public class TerminalOperationTool {

    private final SandboxFactory sandboxFactory;

    public TerminalOperationTool(SandboxFactory sandboxFactory) {
        this.sandboxFactory = sandboxFactory;
    }

    @Tool(description = "Execute a command in the terminal (sandboxed)")
    public String executeTerminalCommand(
            @ToolParam(description = "Command to execute in the terminal") String command) {

        ToolSandbox sandbox = sandboxFactory.getSandbox();
        if (sandbox == null) {
            return "错误：沙箱未初始化，无法执行命令。";
        }

        log.info("[TerminalTool] 通过 {} 执行命令: {}", sandbox.getPolicy(), command);

        SandboxRequest request = SandboxRequest.builder()
                .command(command)
                .timeout(Duration.ofSeconds(30))
                .build();

        SandboxResult result = sandbox.execute(request);

        if (result.isSuccess()) {
            return result.getStdout();
        } else if (result.isKilled()) {
            return "命令执行超时被终止。\n" + result.getStderr();
        } else {
            String errorInfo = result.getErrorMessage() != null ? result.getErrorMessage() : result.getStderr();
            return "命令执行失败（exitCode=" + result.getExitCode() + "）：\n" + errorInfo;
        }
    }
}
