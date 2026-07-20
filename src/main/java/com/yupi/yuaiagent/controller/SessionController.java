package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AccountService;
import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.dto.RenameRequest;
import com.yupi.yuaiagent.dto.SessionSearchResponse;
import com.yupi.yuaiagent.message.PersistentChatMessage;
import com.yupi.yuaiagent.service.SessionAppService;
import com.yupi.yuaiagent.session.SessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Session management controller — thin HTTP adapter.
 * All business logic is in {@link SessionAppService}.
 *
 * @author jsq
 */
@RestController
@RequestMapping("/session")
@Validated
@Tag(name = "会话管理", description = "会话创建、列表、归档、删除等生命周期管理")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    @Resource
    private AuthService authService;

    @Resource
    private AccountService accountService;

    @Resource
    private SessionAppService sessionAppService;

    // ─── Auth ───

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册正式账号（USER 角色）")
    public Response<Map<String, Object>> register(
            @RequestParam @Size(min = 2, max = 32) String username,
            @RequestParam @Size(min = 6, max = 64) String password) {
        return Response.success(accountService.register(username, password));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "返回 accessToken + refreshToken + role")
    public Response<Map<String, Object>> login(
            @RequestParam(value = "username", defaultValue = "游客") @Size(max = 50) String username,
            @RequestParam(value = "userId", required = false) String existingUserId,
            @RequestParam(value = "password", required = false) String password) {
        return Response.success(accountService.login(username, password, existingUserId));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "用 refreshToken 换取新的 accessToken")
    public Response<Map<String, Object>> refresh(@RequestParam String refreshToken) {
        return Response.success(accountService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "吊销 refreshToken")
    public Response<Void> logout(@RequestParam(required = false) String refreshToken) {
        accountService.logout(refreshToken);
        return Response.success(null);
    }

    @GetMapping("/me")
    @Operation(summary = "当前用户", description = "返回用户信息与今日配额")
    public Response<Map<String, Object>> me(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        var principal = authService.authenticatePrincipal(token, authHeader);
        return Response.success(accountService.me(principal));
    }

    // ─── Session CRUD ───


    @PostMapping("/create")
    @Operation(summary = "创建会话", description = "创建新的对话会话")
    public Response<SessionManager.SessionInfo> createSession(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "title", defaultValue = "新对话") @Size(max = 100, message = "标题不能超过100字符") String title) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.create(userId, title));
    }

    @GetMapping("/list")
    @Operation(summary = "会话列表", description = "获取当前用户的活跃会话列表")
    public Response<List<SessionManager.SessionInfo>> listSessions(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.listActive(userId));
    }

    @GetMapping("/archived")
    @Operation(summary = "已归档会话", description = "获取当前用户的已归档会话列表")
    public Response<List<SessionManager.SessionInfo>> listArchived(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.listArchived(userId));
    }

    @GetMapping("/trash")
    @Operation(summary = "回收站", description = "获取当前用户的回收站会话列表")
    public Response<List<SessionManager.SessionInfo>> listTrash(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.listTrash(userId));
    }

    // ─── Rename ───

    @PutMapping("/{chatId}/title")
    @Operation(summary = "重命名会话", description = "修改会话标题")
    public Response<Void> renameSession(
            @PathVariable String chatId,
            @RequestBody RenameRequest request,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        sessionAppService.rename(userId, chatId, request.title());
        return Response.success();
    }

    // ─── Archive / Unarchive ───

    @PutMapping("/{chatId}/archive")
    @Operation(summary = "归档会话", description = "将会话移入归档")
    public Response<Void> archiveSession(
            @PathVariable String chatId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        sessionAppService.archive(userId, chatId);
        return Response.success();
    }

    @PutMapping("/{chatId}/unarchive")
    @Operation(summary = "取消归档", description = "将会话从归档中恢复")
    public Response<Void> unarchiveSession(
            @PathVariable String chatId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        sessionAppService.unarchive(userId, chatId);
        return Response.success();
    }

    // ─── Delete / Restore ───

    @DeleteMapping("/{chatId}")
    @Operation(summary = "删除会话", description = "将会话移入回收站（软删除）")
    public Response<Void> deleteSession(
            @PathVariable String chatId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        sessionAppService.softDelete(userId, chatId);
        return Response.success();
    }

    @PutMapping("/{chatId}/restore")
    @Operation(summary = "恢复会话", description = "从回收站恢复会话")
    public Response<Void> restoreSession(
            @PathVariable String chatId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        sessionAppService.restore(userId, chatId);
        return Response.success();
    }

    // ─── Search ───

    @GetMapping("/search")
    @Operation(summary = "搜索会话", description = "按关键词搜索会话")
    public Response<List<SessionSearchResponse>> search(
            @RequestParam @NotBlank(message = "关键词不能为空") @Size(max = 200, message = "关键词不能超过200字符") String keyword,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.search(userId, keyword));
    }

    // ─── Message History ───

    @GetMapping("/{chatId}/messages")
    @Operation(summary = "消息历史", description = "获取指定会话的消息历史记录")
    public Response<List<PersistentChatMessage>> getMessages(
            @PathVariable String chatId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.getMessages(userId, chatId));
    }
}
