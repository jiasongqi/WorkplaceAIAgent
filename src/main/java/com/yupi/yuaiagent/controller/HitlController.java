package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.hitl.HumanApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Human-in-the-loop approval API for high-risk agent actions.
 */
@RestController
@RequestMapping("/hitl")
@RequiredArgsConstructor
@Tag(name = "人工审批", description = "终端/日历等高危操作的人工确认")
public class HitlController {

    private final HumanApprovalService approvalService;
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
}
