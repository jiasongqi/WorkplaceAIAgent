package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.JwtUtil;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.profile.model.UserProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户画像 REST 接口。
 * <p>
 * 仅暴露 {@code /me} 语义接口，userId 始终取自 JWT 而非请求参数，从根本上保证
 * 用户只能查看与清空与自己 userId 匹配的画像（Req 13.4），无法越权访问他人画像。
 * 鉴权风格复用 {@link JwtUtil#validateToken(String)}，与 {@code SessionController} 一致：
 * 支持 {@code Authorization: Bearer <token>} 头。
 *
 * @author jsq
 */
@RestController
@RequestMapping("/profile")
@Slf4j
@Tag(name = "用户画像", description = "用户画像查看与管理")
public class ProfileController {

    @Resource
    private UserProfileService userProfileService;

    @Resource
    private JwtUtil jwtUtil;

    /**
     * 查看当前用户画像；无画像返回空结果（Req 13.1 / 13.2）。
     * 无有效 JWT 返回未授权（Req 13.5）。
     */
    @GetMapping("/me")
    @Operation(summary = "查看用户画像", description = "查看当前用户的画像信息")
    public Response<UserProfile> getMyProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = extractUserId(authHeader);
        if (userId == null) return Response.failed(401, "未授权，请先登录");
        return Response.success(userProfileService.get(userId).orElse(null));
    }

    /**
     * 清空当前用户画像并持久化删除结果（Req 13.3）。
     * 无有效 JWT 返回未授权（Req 13.5）。
     */
    @DeleteMapping("/me")
    @Operation(summary = "清空用户画像", description = "清空并删除当前用户的画像数据")
    public Response<String> clearMyProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = extractUserId(authHeader);
        if (userId == null) return Response.failed(401, "未授权，请先登录");
        userProfileService.clear(userId);
        return Response.success("画像已清空");
    }

    private String extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return jwtUtil.validateToken(authHeader.substring(7));
    }
}
