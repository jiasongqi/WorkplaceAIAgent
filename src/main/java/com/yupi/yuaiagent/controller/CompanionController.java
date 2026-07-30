package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.JwtUtil;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.companion.UserCompanionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/companion")
@RequiredArgsConstructor
@Tag(name = "智能体伙伴", description = "用户个人智能体伙伴配置")
public class CompanionController {

    private final UserCompanionService companionService;
    private final JwtUtil jwtUtil;

    @GetMapping("/me")
    @Operation(summary = "获取或认领专属伙伴")
    public Response<UserCompanionService.UserCompanionView> getMe(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = extractUserId(authHeader);
        if (userId == null) {
            return Response.failed(401, "未授权，请先登录");
        }
        return Response.success(companionService.getOrClaim(userId));
    }

    @PutMapping("/me")
    @Operation(summary = "更新伙伴人设与偏好（无感进化）")
    public Response<UserCompanionService.UserCompanionView> updateMe(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody UserCompanionService.UpdateCompanionRequest request) {
        String userId = extractUserId(authHeader);
        if (userId == null) {
            return Response.failed(401, "未授权，请先登录");
        }
        return Response.success(companionService.update(userId, request));
    }

    private String extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return jwtUtil.validateToken(authHeader.substring(7));
    }
}
