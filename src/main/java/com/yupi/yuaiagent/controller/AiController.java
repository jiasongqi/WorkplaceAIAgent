package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.agent.YuManus;
import com.yupi.yuaiagent.app.AiChatAgent;
import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.service.OrchestratorAppService;
import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceRecorder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/ai")
@Validated
@Tag(name = "AI 对话", description = "AI 智能对话接口，支持同步/SSE流式/RAG/工具调用等多种模式")
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
    @PostMapping("/ai_chat/chat/sync")
    @Operation(summary = "同步对话", description = "同步调用AI职场顾问，返回完整回答")
    public Response<String> doChatWithAiChatSync(
            @RequestParam @NotBlank(message = "消息不能为空") @Size(max = 10000, message = "消息长度不能超过10000字符") String message,
            @RequestParam String chatId) {
        return Response.success(aiChatAgent.doChat(message, chatId));
    }

    /**
     * SSE 流式调用 AI 职场顾问应用
     */
    @GetMapping(value = "/ai_chat/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE流式对话（Flux）", description = "SSE流式调用AI职场顾问，适用于EventSource客户端")
    public Flux<String> doChatWithAiChatSSE(
            @RequestParam @NotBlank(message = "消息不能为空") @Size(max = 10000, message = "消息长度不能超过10000字符") String message,
            @RequestParam String chatId) {
        return aiChatAgent.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 AI 职场顾问应用（ServerSentEvent 格式）
     */
    @GetMapping(value = "/ai_chat/chat/server_sent_event")
    @Operation(summary = "SSE流式对话（ServerSentEvent格式）", description = "SSE流式调用，返回标准ServerSentEvent格式")
    public Flux<ServerSentEvent<String>> doChatWithAiChatServerSentEvent(
            @RequestParam @NotBlank(message = "消息不能为空") @Size(max = 10000, message = "消息长度不能超过10000字符") String message,
            @RequestParam String chatId) {
        return aiChatAgent.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 职场顾问应用（SseEmitter 格式）
     */
    @GetMapping(value = "/ai_chat/chat/sse_emitter")
    @Operation(summary = "SSE流式对话（SseEmitter格式）", description = "SSE流式调用，使用Spring SseEmitter")
    public SseEmitter doChatWithAiChatServerSseEmitter(
            @RequestParam @NotBlank(message = "消息不能为空") @Size(max = 10000, message = "消息长度不能超过10000字符") String message,
            @RequestParam String chatId) {
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
    @GetMapping(value = "/orchestrator/chat", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "智能路由对话", description = "Multi-Agent智能路由，根据用户意图自动分发给专业子Agent（SSE流式）")
    public SseEmitter doChatWithOrchestrator(
            @RequestParam @NotBlank(message = "消息不能为空") @Size(max = 10000, message = "消息长度不能超过10000字符") String message,
            @RequestParam(defaultValue = "default") String chatId,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        var principal = authService.authenticatePrincipal(tokenParam, authHeader);
        return orchestratorAppService.chatStream(principal, chatId, message);
    }

    /**
     * SSE 断线续传：按 assistantMessageId 回放已落库的 partial/complete 内容。
     */
    @GetMapping(value = "/orchestrator/chat/resume", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "对话续传", description = "SSE断线后按messageId恢复已生成内容")
    public SseEmitter resumeOrchestratorChat(
            @RequestParam @NotBlank String chatId,
            @RequestParam @NotBlank String messageId,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        var principal = authService.authenticatePrincipal(tokenParam, authHeader);
        return orchestratorAppService.resumeStream(principal, chatId, messageId);
    }

    // ==================== Manus 超级智能体 ====================

    /**
     * 流式调用 Manus 超级智能体
     */
    @GetMapping(value = "/manus/chat", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "Manus超级智能体", description = "流式调用Manus超级智能体，支持复杂任务自动执行")
    public SseEmitter doChatWithManus(
            @RequestParam @NotBlank(message = "消息不能为空") @Size(max = 10000, message = "消息长度不能超过10000字符") String message) {
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
    @PostMapping("/ai_chat/rag/sync")
    @Operation(summary = "RAG知识库对话", description = "基于知识库的RAG对话，含Multi-Query多路召回")
    public Response<String> doChatWithRagSync(
            @RequestParam @NotBlank(message = "消息不能为空") @Size(max = 10000, message = "消息长度不能超过10000字符") String message,
            @RequestParam String chatId) {
        return Response.success(aiChatAgent.doChatWithRag(message, chatId));
    }

    // ==================== 工具调用对话 ====================

    /**
     * 工具调用对话（同步）
     */
    @PostMapping("/ai_chat/tools/sync")
    @Operation(summary = "工具调用对话", description = "支持工具调用的AI对话")
    public Response<String> doChatWithToolsSync(
            @RequestParam @NotBlank(message = "消息不能为空") @Size(max = 10000, message = "消息长度不能超过10000字符") String message,
            @RequestParam String chatId) {
        return Response.success(aiChatAgent.doChatWithTools(message, chatId));
    }

    // ==================== MCP 服务对话 ====================

    /**
     * MCP 服务对话（同步）
     */
    @PostMapping("/ai_chat/mcp/sync")
    @Operation(summary = "MCP服务对话", description = "通过MCP协议调用外部服务的AI对话")
    public Response<String> doChatWithMcpSync(
            @RequestParam @NotBlank(message = "消息不能为空") @Size(max = 10000, message = "消息长度不能超过10000字符") String message,
            @RequestParam String chatId) {
        return Response.success(aiChatAgent.doChatWithMcp(message, chatId));
    }

    // ==================== 职场报告（结构化输出）====================

    /**
     * 职场报告生成（结构化输出，同步）
     */
    @PostMapping("/ai_chat/report/sync")
    @Operation(summary = "职场报告生成", description = "生成结构化职场报告，返回完整分析报告")
    public Response<AiChatAgent.AiChatReport> doChatWithReportSync(
            @RequestParam @NotBlank(message = "消息不能为空") @Size(max = 10000, message = "消息长度不能超过10000字符") String message,
            @RequestParam String chatId) {
        return Response.success(aiChatAgent.doChatWithReport(message, chatId));
    }
}
