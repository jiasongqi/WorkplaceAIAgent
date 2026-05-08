package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.JwtUtil;
import com.yupi.yuaiagent.common.Result;
import com.yupi.yuaiagent.session.SessionManager;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话管理 + 用户认证接口
 */
@RestController
@RequestMapping("/session")
public class SessionController {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private SessionManager sessionManager;

    /**
     * 游客登录（生成临时用户 ID 和 Token）
     * 实际项目可替换为真实的用户名密码认证
     */
    @PostMapping("/login")
    public Result<Map<String, String>> login(
            @RequestParam(value = "username", defaultValue = "游客") String username) {
        String userId = UUID.randomUUID().toString();
        String token = jwtUtil.generateToken(userId, username);
        return Result.success(Map.of(
                "token", token,
                "userId", userId,
                "username", username
        ));
    }

    /**
     * 创建新会话
     */
    @PostMapping("/create")
    public Result<SessionManager.SessionInfo> createSession(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(value = "title", defaultValue = "新对话") String title) {
        String userId = extractUserId(authHeader);
        if (userId == null) return Result.error(401, "未授权，请先登录");
        return Result.success(sessionManager.createSession(userId, title));
    }

    /**
     * 获取当前用户的会话列表
     */
    @GetMapping("/list")
    public Result<List<SessionManager.SessionInfo>> listSessions(
            @RequestHeader("Authorization") String authHeader) {
        String userId = extractUserId(authHeader);
        if (userId == null) return Result.error(401, "未授权，请先登录");
        return Result.success(sessionManager.getUserSessions(userId));
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{chatId}")
    public Result<String> deleteSession(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String chatId) {
        String userId = extractUserId(authHeader);
        if (userId == null) return Result.error(401, "未授权，请先登录");
        boolean deleted = sessionManager.deleteSession(userId, chatId);
        return deleted ? Result.success("删除成功") : Result.error(403, "无权删除该会话");
    }

    private String extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return jwtUtil.validateToken(authHeader.substring(7));
    }
}
