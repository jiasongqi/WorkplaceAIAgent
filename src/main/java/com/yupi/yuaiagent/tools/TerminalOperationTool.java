package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.hitl.AgentRequestContext;
import com.yupi.yuaiagent.hitl.HumanApprovalService;
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
 *     <li>HITL 网关：高危命令执行前需人工审批（HumanApprovalService）</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
public class TerminalOperationTool {

    private final SandboxFactory sandboxFactory;
    /** Nullable — when absent, HITL gating is skipped (e.g. unit tests / disabled feature). */
    private final HumanApprovalService approvalService;

    public TerminalOperationTool(SandboxFactory sandboxFactory) {
        this(sandboxFactory, null);
    }

    public TerminalOperationTool(SandboxFactory sandboxFactory, HumanApprovalService approvalService) {
        this.sandboxFactory = sandboxFactory;
        this.approvalService = approvalService;
    }

    @Tool(description = "Execute a command in the terminal (sandboxed). High-risk commands may require human approval first - if the tool returns a pending-approval message, obtain approvalId via POST /api/hitl/approve and retry with the same command and approvalId.")
    public String executeTerminalCommand(
            @ToolParam(description = "Command to execute in the terminal") String command,
            @ToolParam(description = "Approval ID obtained from a prior human approval request, if any. Leave empty if none is available yet.") String approvalId) {

        if (approvalService != null && approvalService.requiresApproval(HumanApprovalService.ActionType.TERMINAL_COMMAND)) {
            boolean approved = approvalService.consumeIfApproved(
                    approvalId, HumanApprovalService.ActionType.TERMINAL_COMMAND, command);
            if (!approved) {
                AgentRequestContext.Holder ctx = AgentRequestContext.get();
                String userId = ctx != null ? ctx.userId() : null;
                String chatId = ctx != null ? ctx.chatId() : null;
                HumanApprovalService.ApprovalRequest req = approvalService.requestApproval(
                        userId, chatId, HumanApprovalService.ActionType.TERMINAL_COMMAND,
                        "执行终端命令：" + command, command);
                return approvalService.pendingMessage(req);
            }
        }

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
