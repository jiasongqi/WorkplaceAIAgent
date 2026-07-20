package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.guard.EmbeddingLoopDetector;
import com.yupi.yuaiagent.guard.ToolResultClassifier;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yupi.yuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    // 终止工具方法名常量，与 TerminateTool.doTerminate() 保持一致
    private static final String TERMINATE_TOOL_NAME = "doTerminate";

    /** Tool execution timeout in seconds — prevents slow MCP/tools from blocking the agent. */
    private static final long TOOL_TIMEOUT_SECONDS = 30;

    /** Maximum number of automatic retries on timeout (direction is correct, just network issue). */
    private static final int MAX_TIMEOUT_RETRIES = 2;

    // Executor for async tool execution with timeout
    private Executor toolExecutor;

    // Guard components — optional, non-invasive integration (Req 4.1, 4.2, 4.8)
    @Autowired(required = false)
    private ToolResultClassifier toolResultClassifier;

    @Autowired(required = false)
    private EmbeddingLoopDetector embeddingLoopDetector;

    public ToolCallAgent(ToolCallback[] availableTools) {
        this(availableTools, java.util.concurrent.ForkJoinPool.commonPool());
    }

    public ToolCallAgent(ToolCallback[] availableTools, Executor toolExecutor) {
        super();
        this.availableTools = availableTools;
        this.toolExecutor = toolExecutor;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 清理资源：清除 LoopDetector 会话状态，防止内存泄漏和跨会话误判
     */
    @Override
    protected void cleanup() {
        nextStepPromptAdded = false;
        if (embeddingLoopDetector != null) {
            try {
                String sessionId = Thread.currentThread().getName();
                embeddingLoopDetector.clearSession(sessionId);
            } catch (Exception e) {
                log.warn("[ToolCallAgent] clearSession failed: {}", e.getMessage());
            }
        }
        super.cleanup();
    }

    // 标记 nextStepPrompt 是否已添加（防止每步重复追加）
    private boolean nextStepPromptAdded = false;

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1、校验提示词，拼接用户提示词（仅首次添加，防止重复追加污染上下文）
        if (!nextStepPromptAdded && StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
            nextStepPromptAdded = true;
        }
        // 2、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 3、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String result = assistantMessage.getText();
            log.info(getName() + "的思考：" + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            // 如果不需要调用工具，返回 false
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才需要手动记录助手消息
                getMessageList().add(assistantMessage);
                return false;
            } else {
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());
            getMessageList().add(new AssistantMessage(friendlyLlmError(e.getMessage())));
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }

        // Record TOOL_CALL span (Req 8.5)
        TraceSpan toolCallSpan = null;
        if (getTraceContext() != null && getTraceRecorder() != null) {
            toolCallSpan = getTraceRecorder().startSpan(getTraceContext(), TraceStepType.TOOL_CALL, "工具调用");
        }

        // --- Guard: EmbeddingLoopDetector — invoke BEFORE tool execution (Req 4.2) ---
        if (embeddingLoopDetector != null) {
            try {
                String sessionId = Thread.currentThread().getName();
                AssistantMessage assistantMsg = toolCallChatResponse.getResult().getOutput();
                List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                if (!toolCalls.isEmpty()) {
                    String toolName = toolCalls.get(0).name();
                    String toolArgs = toolCalls.get(0).arguments();
                    boolean loopDetected = embeddingLoopDetector.checkLoop(sessionId, toolName, toolArgs, getMessageList());
                    if (loopDetected) {
                        // 循环检测命中：终止 Agent 避免无限重复调用
                        log.warn("[ToolCallAgent] loop detected, terminating agent to prevent repeated tool calls");
                        setState(AgentState.FINISHED);
                        return "检测到重复工具调用循环，已自动终止。请重新描述您的需求。";
                    }
                }
            } catch (Exception e) {
                log.warn("[ToolCallAgent] loop detection failed, skipping: {}", e.getMessage());
            }
        }

        // 调用工具（with timeout protection + auto-retry for TIMEOUT）
        boolean isTimeout = false;
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = null;
        int retryCount = 0;
        int maxAttempts = MAX_TIMEOUT_RETRIES + 1; // Total attempts = retries + 1
        
        while (retryCount < maxAttempts) {
            try {
                final Prompt toolPrompt = prompt;
                toolExecutionResult = CompletableFuture
                        .supplyAsync(() -> toolCallingManager.executeToolCalls(toolPrompt, toolCallChatResponse), toolExecutor)
                        .orTimeout(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .join();
                break; // 成功，退出重试循环
            } catch (java.util.concurrent.CompletionException e) {
                if (e.getCause() instanceof TimeoutException) {
                    retryCount++;
                    if (retryCount < maxAttempts) {
                        // 自动重试：方向对，网络问题，不换关键词
                        log.warn("[ToolCall] tool execution timed out (attempt {}/{}), retrying same call...",
                                retryCount, MAX_TIMEOUT_RETRIES);
                        continue;
                    }
                    // 重试用尽，走失败流程
                    log.error("[ToolCall] tool execution timed out after {}s, {} retries exhausted",
                            TOOL_TIMEOUT_SECONDS, MAX_TIMEOUT_RETRIES);
                    if (toolCallSpan != null && getTraceRecorder() != null) {
                        getTraceRecorder().failSpan(getTraceContext(), toolCallSpan,
                                "Tool execution timed out after " + TOOL_TIMEOUT_SECONDS + "s (retries exhausted)");
                    }
                    // --- Guard: ToolResultClassifier — classify timeout result ---
                    if (toolResultClassifier != null) {
                        try {
                            toolResultClassifier.classifyAndGuide(null, true, getMessageList());
                        } catch (Exception ex) {
                            log.warn("[ToolCallAgent] result classification failed, skipping: {}", ex.getMessage());
                        }
                    }
                    return "工具执行超时（" + TOOL_TIMEOUT_SECONDS + "秒，已重试" + MAX_TIMEOUT_RETRIES + "次），请换个方式重试";
                }
                throw e;
            }
        }
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals(TERMINATE_TOOL_NAME));
        if (terminateToolCalled) {
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));

        // --- Guard: TokenBudgetManager — truncate Observation to 3000 chars in Normal mode ---
        if (getTokenBudgetManager() != null) {
            results = getTokenBudgetManager().truncateForNormal(results);
        }

        // Record tool names in trace metadata (Req 8.5)
        if (toolCallSpan != null && getTraceRecorder() != null) {
            String toolNames = toolResponseMessage.getResponses().stream()
                    .map(response -> response.name())
                    .collect(Collectors.joining(","));
            getTraceRecorder().putMetadata(toolCallSpan, "toolNames", toolNames);
            getTraceRecorder().endSpan(getTraceContext(), toolCallSpan);
        }

        // --- Guard: ToolResultClassifier — classify normal result (Req 4.1) ---
        if (toolResultClassifier != null) {
            try {
                ToolResultClassifier.ResultGrade grade = toolResultClassifier.classifyAndGuide(results, false, getMessageList());
                // 如果结果非正常，回填失败原因给 LoopDetector
                if (grade != ToolResultClassifier.ResultGrade.NORMAL && embeddingLoopDetector != null) {
                    String sessionId = Thread.currentThread().getName();
                    AssistantMessage assistantMsg = toolCallChatResponse.getResult().getOutput();
                    List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                    if (!toolCalls.isEmpty()) {
                        String sig = toolCalls.get(0).name() + ":" + toolCalls.get(0).arguments();
                        embeddingLoopDetector.recordFailure(sessionId, sig, grade.name() + " - " + results);
                    }
                }
            } catch (Exception e) {
                log.warn("[ToolCallAgent] result classification failed, skipping: {}", e.getMessage());
            }
        }

        log.debug(results);
        return results;
    }

    private static String friendlyLlmError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "处理时遇到了错误，请稍后重试。";
        }
        if (raw.contains("AllocationQuota") || raw.contains("Free quota exhausted")
                || raw.contains("FreeTierOnly")) {
            return "模型调用失败：DashScope 免费额度已用尽。"
                    + "请到阿里云控制台充值，或关闭「仅使用免费额度」后重试。";
        }
        if (raw.contains("401") || raw.contains("InvalidApiKey") || raw.contains("Unauthorized")) {
            return "模型调用失败：API Key 无效或未配置，请检查 spring.ai.dashscope.api-key。";
        }
        return "处理时遇到了错误：" + raw;
    }
}
