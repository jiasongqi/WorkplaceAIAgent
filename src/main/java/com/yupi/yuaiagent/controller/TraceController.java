package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Result;
import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.session.SessionManager;
import com.yupi.yuaiagent.trace.TraceRepository;
import com.yupi.yuaiagent.trace.model.ExecutionTrace;
import jakarta.annotation.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for querying execution traces (Req 7.1).
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code GET /trace/{traceId}} — fetch a single trace by ID</li>
 *   <li>{@code GET /trace/chat/{chatId}} — fetch traces for a chat session (paginated)</li>
 *   <li>{@code GET /trace/user/{userId}} — fetch traces for a user (paginated)</li>
 * </ul>
 * <p>
 * Authentication: supports both {@code Authorization: Bearer xxx} header
 * and {@code ?token=xxx} URL parameter (for EventSource compatibility).
 *
 * @author jsq
 */
@RestController
@RequestMapping("/trace")
public class TraceController {

    @Resource
    private TraceRepository traceRepository;

    @Resource
    private AuthService authService;

    @Resource
    private SessionManager sessionManager;

    /**
     * Fetches a single trace by its ID.
     * The caller must own the trace or own the chat session.
     */
    @GetMapping("/{traceId}")
    public Result<ExecutionTrace> getTrace(
            @PathVariable String traceId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = authService.authenticate(token, authHeader);

        ExecutionTrace trace = traceRepository.findById(traceId)
                .orElseThrow(() -> BusinessException.notFound("轨迹"));

        if (!canAccess(userId, trace)) {
            throw BusinessException.forbidden();
        }

        return Result.success(trace);
    }

    /**
     * Fetches traces for a given chatId with pagination.
     * Verifies the caller owns the chat session before returning traces.
     */
    @GetMapping("/chat/{chatId}")
    public Result<List<ExecutionTrace>> getTracesByChat(
            @PathVariable String chatId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = authService.authenticate(token, authHeader);

        if (!sessionManager.isOwner(userId, chatId)) {
            throw BusinessException.forbidden();
        }

        List<ExecutionTrace> traces = traceRepository.findByChatId(chatId, pageNum, pageSize);
        return Result.success(traces);
    }

    /**
     * Fetches traces for a given userId with pagination.
     * The caller can only access their own traces.
     */
    @GetMapping("/user/{userId}")
    public Result<List<ExecutionTrace>> getTracesByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String callerId = authService.authenticate(token, authHeader);

        if (!callerId.equals(userId)) {
            throw BusinessException.forbidden();
        }

        List<ExecutionTrace> traces = traceRepository.findByUserId(userId, pageNum, pageSize);
        return Result.success(traces);
    }

    // --- helpers ---

    /**
     * Checks if the user can access the given trace.
     * Access is granted if the user owns the trace or owns the chat session.
     */
    private boolean canAccess(String userId, ExecutionTrace trace) {
        if (userId.equals(trace.getUserId())) {
            return true;
        }
        return StringUtils.hasText(trace.getChatId())
                && sessionManager.isOwner(userId, trace.getChatId());
    }
}
