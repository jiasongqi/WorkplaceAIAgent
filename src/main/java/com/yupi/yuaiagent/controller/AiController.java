package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.agent.YuManus;
import com.yupi.yuaiagent.app.AiChatAgent;
import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.service.OrchestratorAppService;
import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceRecorder;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiChatAgent aiChatAgent;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private AuthService authService;

    @Resource
    private OrchestratorAppService orchestratorAppService;

    @Resource
    private TraceRecorder traceRecorder;

    // ==================== 职场顾问（基础对话）====================

    /**
     * 同步调用 AI 职场顾问应用
     */
    @GetMapping("/ai_chat/chat/sync")
    public Response<String> doChatWithAiChatSync(String message, String chatId) {
        return Response.success(aiChatAgent.doChat(message, chatId));
    }

    /**
     * SSE 流式调用 AI 职场顾问应用
     */
    @GetMapping(value = "/ai_chat/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithAiChatSSE(String message, String chatId) {
        return aiChatAgent.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 AI 职场顾问应用（ServerSentEvent 格式）
     */
    @GetMapping(value = "/ai_chat/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithAiChatServerSentEvent(String message, String chatId) {
        return aiChatAgent.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 职场顾问应用（SseEmitter 格式）
     */
    @GetMapping(value = "/ai_chat/chat/sse_emitter")
    public SseEmitter doChatWithAiChatServerSseEmitter(String message, String chatId) {
        SseEmitter sseEmitter = new SseEmitter(180000L);
        aiChatAgent.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        return sseEmitter;
    }

    // ==================== Multi-Agent 智能路由 ====================

    /**
     * 智能路由：根据用户意图自动分发给专业子 Agent（SSE 流式）
     * 支持：简历优化(ResumeAgent)、薪资谈判(NegotiationAgent)、离职规划(EscapeAgent)、通用(YuManus)
     *
     * <p>鉴权说明：EventSource 不支持自定义请求头，因此 Token 通过 URL 参数 {@code token} 传递，
     * 同时兼容 Authorization 头。未携带有效 Token 将被拒绝。
     */
    @GetMapping("/orchestrator/chat")
    public SseEmitter doChatWithOrchestrator(
            String message,
            @RequestParam(defaultValue = "default") String chatId,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(tokenParam, authHeader);
        return orchestratorAppService.chatStream(userId, chatId, message);
    }

    // ==================== Manus 超级智能体 ====================

    /**
     * 流式调用 Manus 超级智能体
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        YuManus yuManus = new YuManus(allTools, dashscopeChatModel);
        // Set up trace context for tool call recording (Req 8.5)
        String requestId = UUID.randomUUID().toString().replace("-", "");
        TraceContext traceCtx = traceRecorder.startTrace(null, null, requestId);
        yuManus.setTraceContext(traceCtx);
        yuManus.setTraceRecorder(traceRecorder);
        return yuManus.runStream(message);
    }

    // ==================== RAG 知识库对话 ====================

    /**
     * RAG 知识库对话（含 Multi-Query 多路召回，同步）
     */
    @GetMapping("/ai_chat/rag/sync")
    public Response<String> doChatWithRagSync(String message, String chatId) {
        return Response.success(aiChatAgent.doChatWithRag(message, chatId));
    }

    // ==================== 工具调用对话 ====================

    /**
     * 工具调用对话（同步）
     */
    @GetMapping("/ai_chat/tools/sync")
    public Response<String> doChatWithToolsSync(String message, String chatId) {
        return Response.success(aiChatAgent.doChatWithTools(message, chatId));
    }

    // ==================== MCP 服务对话 ====================

    /**
     * MCP 服务对话（同步）
     */
    @GetMapping("/ai_chat/mcp/sync")
    public Response<String> doChatWithMcpSync(String message, String chatId) {
        return Response.success(aiChatAgent.doChatWithMcp(message, chatId));
    }

    // ==================== 职场报告（结构化输出）====================

    /**
     * 职场报告生成（结构化输出，同步）
     */
    @GetMapping("/ai_chat/report/sync")
    public Response<AiChatAgent.AiChatReport> doChatWithReportSync(String message, String chatId) {
        return Response.success(aiChatAgent.doChatWithReport(message, chatId));
    }
}
