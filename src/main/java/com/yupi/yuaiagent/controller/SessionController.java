package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.auth.JwtUtil;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.dto.RenameRequest;
import com.yupi.yuaiagent.dto.SessionSearchResponse;
import com.yupi.yuaiagent.message.PersistentChatMessage;
import com.yupi.yuaiagent.service.SessionAppService;
import com.yupi.yuaiagent.session.SessionManager;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Session management controller — thin HTTP adapter.
 * All business logic is in {@link SessionAppService}.
 *
 * @author jsq
 */
@RestController
@RequestMapping("/session")
public class SessionController {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private AuthService authService;

    @Resource
    private SessionAppService sessionAppService;

    // ─── Auth ───

    @PostMapping("/login")
    public Response<Map<String, String>> login(
            @RequestParam(value = "username", defaultValue = "游客") String username) {
        String userId = UUID.randomUUID().toString();
        String token = jwtUtil.generateToken(userId, username);
        return Response.success(Map.of("token", token, "userId", userId, "username", username));
    }

    // ─── Session CRUD ───

    @PostMapping("/create")
    public Response<SessionManager.SessionInfo> createSession(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "title", defaultValue = "新对话") String title) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.create(userId, title));
    }

    @GetMapping("/list")
    public Response<List<SessionManager.SessionInfo>> listSessions(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.listActive(userId));
    }

    @GetMapping("/archived")
    public Response<List<SessionManager.SessionInfo>> listArchived(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.listArchived(userId));
    }

    @GetMapping("/trash")
    public Response<List<SessionManager.SessionInfo>> listTrash(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.listTrash(userId));
    }

    // ─── Rename ───

    @PutMapping("/{chatId}/title")
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
    public Response<Void> archiveSession(
            @PathVariable String chatId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        sessionAppService.archive(userId, chatId);
        return Response.success();
    }

    @PutMapping("/{chatId}/unarchive")
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
    public Response<Void> deleteSession(
            @PathVariable String chatId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        sessionAppService.softDelete(userId, chatId);
        return Response.success();
    }

    @PutMapping("/{chatId}/restore")
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
    public Response<List<SessionSearchResponse>> search(
            @RequestParam String keyword,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.search(userId, keyword));
    }

    // ─── Message History ───

    @GetMapping("/{chatId}/messages")
    public Response<List<PersistentChatMessage>> getMessages(
            @PathVariable String chatId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(sessionAppService.getMessages(userId, chatId));
    }
}
