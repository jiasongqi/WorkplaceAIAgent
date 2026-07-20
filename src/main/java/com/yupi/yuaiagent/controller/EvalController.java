package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.agent.OrchestratorAgent;
import com.yupi.yuaiagent.auth.AuthPrincipal;
import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.auth.UserRole;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.eval.EvalAppService;
import com.yupi.yuaiagent.eval.EvalCenter;
import com.yupi.yuaiagent.eval.EvalReport;
import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.metrics.AgentExecutionMetrics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * Eval API — routing gate is cheap; live content burns LLM (ADMIN only).
 */
@RestController
@RequestMapping("/eval")
@RequiredArgsConstructor
@Tag(name = "评测中心", description = "Agent 路由/质量回归评测与线上指标")
public class EvalController {

    private final EvalCenter evalCenter;
    private final EvalAppService evalAppService;
    private final OrchestratorAgent orchestratorAgent;
    private final AgentExecutionMetrics agentExecutionMetrics;
    private final AuthService authService;

    @GetMapping("/suites")
    @Operation(summary = "列出评测套件")
    public Response<Set<String>> listSuites(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        return Response.success(evalCenter.getSuiteNames());
    }

    @PostMapping("/run/{suiteId}")
    @Operation(summary = "运行评测套件", description = "需登录；routing-suite 零 LLM")
    public Response<EvalReport> runSuite(@PathVariable String suiteId,
                                          @RequestParam(value = "token", required = false) String token,
                                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(token, authHeader);
        EvalReport report = evalCenter.runEvalSuite(suiteId);
        if (report == null) {
            return Response.failed("suite not found: " + suiteId);
        }
        return Response.success(report);
    }

    @PostMapping("/run/{suiteId}/live")
    @Operation(summary = "内容套件 live（真实 Agent）", description = "ADMIN only，消耗 LLM")
    public Response<EvalReport> runSuiteLive(@PathVariable String suiteId,
                                              @RequestParam(value = "token", required = false) String token,
                                              @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(token, authHeader);
        EvalReport report = evalAppService.runContentLive(suiteId, orchestratorAgent);
        if (report == null) {
            return Response.failed("suite not found: " + suiteId);
        }
        return Response.success(report);
    }

    @PostMapping("/gate/{suiteId}")
    @Operation(summary = "发版门禁")
    public Response<EvalReport> runGate(@PathVariable String suiteId,
                                         @RequestParam(value = "token", required = false) String token,
                                         @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(token, authHeader);
        try {
            return Response.success(evalCenter.runAndAssertGate(suiteId));
        } catch (AssertionError | IllegalStateException e) {
            return Response.failed(e.getMessage());
        }
    }

    @GetMapping("/reports")
    @Operation(summary = "历史评测报告")
    public Response<?> reports(@RequestParam(value = "token", required = false) String token,
                                @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        return Response.success(evalCenter.getAllReports());
    }

    @GetMapping("/metrics")
    @Operation(summary = "线上 Agent 执行指标")
    public Response<Map<String, AgentExecutionMetrics.AgentMetricsSnapshot>> metrics(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        return Response.success(agentExecutionMetrics.getAllSnapshots());
    }

    @GetMapping("/metrics/global")
    @Operation(summary = "全局 Agent 指标")
    public Response<AgentExecutionMetrics.GlobalMetrics> globalMetrics(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        return Response.success(agentExecutionMetrics.getGlobalMetrics());
    }

    private void requireAdmin(String token, String authHeader) {
        AuthPrincipal p = authService.authenticatePrincipal(token, authHeader);
        if (p.role() != UserRole.ADMIN) {
            throw BusinessException.forbidden();
        }
    }
}
