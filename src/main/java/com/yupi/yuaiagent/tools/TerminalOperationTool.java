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
import java.util.Optional;

/**
 * 终端操作工具 — 沙箱执行 + HITL + 幂等去重。
 */
@Slf4j
public class TerminalOperationTool {

    private final SandboxFactory sandboxFactory;
    private final HumanApprovalService approvalService;
    private final ToolIdempotencyStore idempotencyStore;

    public TerminalOperationTool(SandboxFactory sandboxFactory) {
        this(sandboxFactory, null, null);
    }

    public TerminalOperationTool(SandboxFactory sandboxFactory, HumanApprovalService approvalService) {
        this(sandboxFactory, approvalService, null);
    }

    public TerminalOperationTool(SandboxFactory sandboxFactory,
                                 HumanApprovalService approvalService,
                                 ToolIdempotencyStore idempotencyStore) {
        this.sandboxFactory = sandboxFactory;
        this.approvalService = approvalService;
        this.idempotencyStore = idempotencyStore;
    }

    @Tool(description = """
            Execute a shell command in the sandbox (side effect). High-risk commands require human approval — \
            if pending-approval is returned, obtain approvalId via POST /api/hitl/approve and retry with the same command and approvalId.
            WHEN TO USE: user explicitly needs shell/file-system automation in the sandbox.
            DO NOT USE: for web search, scraping, or PDF generation (dedicated tools exist).
            NOT safe to blindly retry on timeout — duplicates are blocked via idempotency within TTL.""")
    public String executeTerminalCommand(
            @ToolParam(description = "Command to execute in the terminal") String command,
            @ToolParam(description = "Approval ID from prior HITL approval, if any; leave empty if none") String approvalId) {

        String fingerprint = command + "::" + (approvalId == null ? "" : approvalId);
        if (idempotencyStore != null) {
            String key = idempotencyStore.key("executeTerminalCommand", fingerprint);
            Optional<String> cached = idempotencyStore.find(key);
            if (cached.isPresent()) {
                return cached.get() + "\n[System Note: idempotent replay — command was not re-executed]";
            }
        }

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

        String out;
        if (result.isSuccess()) {
            out = result.getStdout();
        } else if (result.isKilled()) {
            out = "命令执行超时被终止。\n" + result.getStderr();
        } else {
            String errorInfo = result.getErrorMessage() != null ? result.getErrorMessage() : result.getStderr();
            out = "命令执行失败（exitCode=" + result.getExitCode() + "）：\n" + errorInfo;
        }

        if (idempotencyStore != null && result.isSuccess()) {
            idempotencyStore.remember(idempotencyStore.key("executeTerminalCommand", fingerprint), out);
        }
        return out;
    }
}
