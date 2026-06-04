package com.yupi.yuaiagent.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.controller.TraceController;
import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.session.SessionManager;
import com.yupi.yuaiagent.trace.model.ExecutionTrace;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepStatus;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TraceController tests — covers auth (token param + Authorization header),
 * pagination, access control, and error scenarios.
 *
 * @author jsq
 */
class TraceControllerTest {

    private MockMvc mockMvc;

    private AuthService authService;
    private SessionManager sessionManager;

    @TempDir
    Path tempDir;

    private TraceRepository traceRepository;

    private static final String USER_A = "userA";
    private static final String USER_B = "userB";
    private static final String CHAT_A = "chatA";
    private static final String CHAT_B = "chatB";
    private static final String TOKEN_A = "token-a";
    private static final String TOKEN_B = "token-b";
    private static final String VALID_TRACE_ID = "valid-trace-id";

    @BeforeEach
    void setUp() throws Exception {
        // Create and configure a real TraceRepository
        traceRepository = new TraceRepository();
        var propsField = TraceRepository.class.getDeclaredField("traceProperties");
        propsField.setAccessible(true);
        propsField.set(traceRepository, new TraceProperties());

        var storageDirField = TraceRepository.class.getDeclaredField("storageDir");
        storageDirField.setAccessible(true);
        storageDirField.set(traceRepository, tempDir.toString());

        traceRepository.init();

        // Mock AuthService
        authService = org.mockito.Mockito.mock(AuthService.class);
        sessionManager = org.mockito.Mockito.mock(SessionManager.class);

        // Inject into controller
        TraceController controller = new TraceController();
        var repoField = TraceController.class.getDeclaredField("traceRepository");
        repoField.setAccessible(true);
        repoField.set(controller, traceRepository);

        var authField = TraceController.class.getDeclaredField("authService");
        authField.setAccessible(true);
        authField.set(controller, authService);

        var sessionField = TraceController.class.getDeclaredField("sessionManager");
        sessionField.setAccessible(true);
        sessionField.set(controller, sessionManager);

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new com.yupi.yuaiagent.exception.GlobalExceptionHandler())
                .build();

        // Seed test data
        ExecutionTrace traceA = ExecutionTrace.start(USER_A, CHAT_A, "req-a");
        var traceIdField = ExecutionTrace.class.getDeclaredField("traceId");
        traceIdField.setAccessible(true);
        traceIdField.set(traceA, VALID_TRACE_ID);

        TraceSpan span = new TraceSpan(0, TraceStepType.INTENT_DETECTION, "意图识别");
        span.terminate(TraceStepStatus.SUCCESS);
        traceA.addSpan(span);
        traceA.finalizeStatus();
        traceRepository.save(traceA);

        ExecutionTrace traceB = ExecutionTrace.start(USER_B, CHAT_B, "req-b");
        traceRepository.save(traceB);

        // Mock AuthService — token param
        when(authService.authenticate(eq(TOKEN_A), any())).thenReturn(USER_A);
        when(authService.authenticate(eq(TOKEN_B), any())).thenReturn(USER_B);
        when(authService.authenticate(eq("invalid"), any()))
                .thenThrow(BusinessException.notLoggedIn());
        when(authService.authenticate(eq(null), any()))
                .thenThrow(BusinessException.notLoggedIn());

        // Mock session ownership
        when(sessionManager.isOwner(USER_A, CHAT_A)).thenReturn(true);
        when(sessionManager.isOwner(USER_B, CHAT_B)).thenReturn(true);
        when(sessionManager.isOwner(USER_A, CHAT_B)).thenReturn(false);
        when(sessionManager.isOwner(USER_B, CHAT_A)).thenReturn(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /trace/{traceId} — token param
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /trace/{traceId} — 无 token 返回 401")
    void getTrace_noToken_returns401() throws Exception {
        mockMvc.perform(get("/trace/{traceId}", VALID_TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("GET /trace/{traceId} — 无效 token 返回 401")
    void getTrace_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/trace/{traceId}", VALID_TRACE_ID)
                        .param("token", "invalid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("GET /trace/{traceId} — 访问自己的 trace 返回 200")
    void getTrace_ownTrace_returns200() throws Exception {
        mockMvc.perform(get("/trace/{traceId}", VALID_TRACE_ID)
                        .param("token", TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.traceId").value(VALID_TRACE_ID))
                .andExpect(jsonPath("$.data.userId").value(USER_A));
    }

    @Test
    @DisplayName("GET /trace/{traceId} — 不存在的 traceId 返回 404")
    void getTrace_notFound_returns404() throws Exception {
        mockMvc.perform(get("/trace/{traceId}", "nonexistent")
                        .param("token", TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("GET /trace/{traceId} — 访问他人 trace 返回 403")
    void getTrace_othersTrace_returns403() throws Exception {
        ExecutionTrace traceB = traceRepository.findByUserId(USER_B).get(0);
        mockMvc.perform(get("/trace/{traceId}", traceB.getTraceId())
                        .param("token", TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /trace/{traceId} — Authorization header
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /trace/{traceId} — Authorization header 鉴权成功返回 200")
    void getTrace_authHeader_returns200() throws Exception {
        // Create a fresh mock that returns USER_A for this specific call
        AuthService headerAuth = org.mockito.Mockito.mock(AuthService.class);
        when(headerAuth.authenticate(eq(null), eq("Bearer " + TOKEN_A))).thenReturn(USER_A);

        // Create a dedicated controller with this mock
        TraceController ctrl = new TraceController();
        var authF = TraceController.class.getDeclaredField("authService");
        authF.setAccessible(true);
        authF.set(ctrl, headerAuth);
        var repoF = TraceController.class.getDeclaredField("traceRepository");
        repoF.setAccessible(true);
        repoF.set(ctrl, traceRepository);
        var sessF = TraceController.class.getDeclaredField("sessionManager");
        sessF.setAccessible(true);
        sessF.set(ctrl, sessionManager);

        MockMvc localMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(ctrl)
                .setControllerAdvice(new com.yupi.yuaiagent.exception.GlobalExceptionHandler())
                .build();

        localMvc.perform(get("/trace/{traceId}", VALID_TRACE_ID)
                        .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.traceId").value(VALID_TRACE_ID));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /trace/chat/{chatId} — pagination
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /trace/chat/{chatId} — 无 token 返回 401")
    void getTracesByChat_noToken_returns401() throws Exception {
        mockMvc.perform(get("/trace/chat/{chatId}", CHAT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("GET /trace/chat/{chatId} — 访问自己的 chat 返回 200")
    void getTracesByChat_ownChat_returns200() throws Exception {
        mockMvc.perform(get("/trace/chat/{chatId}", CHAT_A)
                        .param("token", TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /trace/chat/{chatId} — 访问他人 chat 返回 403")
    void getTracesByChat_othersChat_returns403() throws Exception {
        mockMvc.perform(get("/trace/chat/{chatId}", CHAT_B)
                        .param("token", TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("GET /trace/chat/{chatId} — 分页参数生效")
    void getTracesByChat_pagination() throws Exception {
        mockMvc.perform(get("/trace/chat/{chatId}", CHAT_A)
                        .param("token", TOKEN_A)
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /trace/user/{userId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /trace/user/{userId} — 无 token 返回 401")
    void getTracesByUser_noToken_returns401() throws Exception {
        mockMvc.perform(get("/trace/user/{userId}", USER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("GET /trace/user/{userId} — 访问自己的 traces 返回 200")
    void getTracesByUser_ownUser_returns200() throws Exception {
        mockMvc.perform(get("/trace/user/{userId}", USER_A)
                        .param("token", TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /trace/user/{userId} — 访问他人 traces 返回 403")
    void getTracesByUser_othersUser_returns403() throws Exception {
        mockMvc.perform(get("/trace/user/{userId}", USER_B)
                        .param("token", TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("GET /trace/user/{userId} — 分页参数生效")
    void getTracesByUser_pagination() throws Exception {
        mockMvc.perform(get("/trace/user/{userId}", USER_A)
                        .param("token", TOKEN_A)
                        .param("pageNum", "1")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 14: 授权过滤绝不泄露他人轨迹
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Property 14: 用户 A 无法通过任何端点获取用户 B 的轨迹数据")
    void userACannotAccessUserBTraces() throws Exception {
        ExecutionTrace traceB = traceRepository.findByUserId(USER_B).get(0);

        // GET /trace/{traceId} — should be 403
        mockMvc.perform(get("/trace/{traceId}", traceB.getTraceId())
                        .param("token", TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        // GET /trace/chat/{chatId} — should be 403
        mockMvc.perform(get("/trace/chat/{chatId}", CHAT_B)
                        .param("token", TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        // GET /trace/user/{userId} — should be 403
        mockMvc.perform(get("/trace/user/{userId}", USER_B)
                        .param("token", TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }
}
