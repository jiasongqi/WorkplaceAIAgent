package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.hitl.HumanApprovalService;
import com.yupi.yuaiagent.hitl.HumanHandoffService;
import com.yupi.yuaiagent.hitl.HumanHandoffTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Human-in-the-loop approval + conversation-level human handoff API.
 */
@RestController
@RequestMapping("/hitl")
@RequiredArgsConstructor
@Tag(name = "人工审批", description = "高危操作确认 + 会话级人工接管")
public class HitlController {

    private final HumanApprovalService approvalService;
    private final HumanHandoffService humanHandoffService;
    private final AuthService authService;

    @GetMapping("/{approvalId}")
    @Operation(summary = "查询审批单")
    public Response<HumanApprovalService.ApprovalRequest> get(
            @PathVariable String approvalId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        return approvalService.get(approvalId)
                .map(Response::success)
                .orElseGet(() -> Response.failed("approval not found"));
    }

    @PostMapping("/approve")
    @Operation(summary = "批准操作")
    public Response<HumanApprovalService.ApprovalRequest> approve(
            @RequestParam String approvalId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        try {
            return Response.success(approvalService.approve(approvalId, userId));
        } catch (Exception e) {
            return Response.failed(e.getMessage());
        }
    }

    @PostMapping("/reject")
    @Operation(summary = "拒绝操作")
    public Response<HumanApprovalService.ApprovalRequest> reject(
            @RequestParam String approvalId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        try {
            return Response.success(approvalService.reject(approvalId, userId));
        } catch (Exception e) {
            return Response.failed(e.getMessage());
        }
    }

    // ── Conversation-level human handoff (async park / resume) ──

    @GetMapping("/handoff/pending")
    @Operation(summary = "列出当前用户待人工接管的会话")
    public Response<List<HumanHandoffTicket>> listHandoffs(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(humanHandoffService.listWaitingByUser(userId));
    }

    @GetMapping("/handoff/{handoffId}")
    @Operation(summary = "查询人工接管单")
    public Response<HumanHandoffTicket> getHandoff(
            @PathVariable String handoffId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        return humanHandoffService.get(handoffId)
                .map(Response::success)
                .orElseGet(() -> Response.failed("human handoff not found"));
    }

    @PostMapping("/handoff/resume")
    @Operation(summary = "人工续跑：注入意见并唤醒会话状态")
    public Response<HumanHandoffTicket> resumeHandoff(
            @RequestParam String handoffId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        String humanInput = "";
        if (body != null) {
            if (body.get("humanInput") != null) {
                humanInput = body.get("humanInput");
            } else if (body.get("input") != null) {
                humanInput = body.get("input");
            }
        }
        try {
            return Response.success(humanHandoffService.resume(handoffId, userId, humanInput));
        } catch (Exception e) {
            return Response.failed(e.getMessage());
        }
    }

    @PostMapping("/handoff/cancel")
    @Operation(summary = "取消人工接管单")
    public Response<HumanHandoffTicket> cancelHandoff(
            @RequestParam String handoffId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        try {
            return Response.success(humanHandoffService.cancel(handoffId, userId));
        } catch (Exception e) {
            return Response.failed(e.getMessage());
        }
    }
}
