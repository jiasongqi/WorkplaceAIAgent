package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.access.AccessDecisionContext;
import com.yupi.yuaiagent.access.AccessDecisionService;
import com.yupi.yuaiagent.access.PermittedToolFilter;
import com.yupi.yuaiagent.guard.EmbeddingLoopDetector;
import com.yupi.yuaiagent.guard.ObservationSanitizer;
import com.yupi.yuaiagent.guard.ToolResultClassifier;
import com.yupi.yuaiagent.agent.goal.GoalAnchor;
import com.yupi.yuaiagent.agent.loop.ChatUsageExtractor;
import com.yupi.yuaiagent.permission.AgentCodeResolver;
import com.yupi.yuaiagent.permission.ToolNameMatcher;
import com.yupi.yuaiagent.tools.ToolSideEffectPolicy;
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
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
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

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文（并行 Fan-out 见 ParallelToolCallingSupport）
    private final ChatOptions chatOptions;

    // 终止工具方法名常量，与 TerminateTool.doTerminate() 保持一致
    private static final String TERMINATE_TOOL_NAME = "doTerminate";

    /** Per-tool execution timeout in seconds — prevents slow MCP/tools from blocking the agent. */
    private static final long TOOL_TIMEOUT_SECONDS = 30;

    /** Maximum number of automatic retries on timeout for read-only tool batches only. */
    private static final int MAX_TIMEOUT_RETRIES = 2;

    // Executor for async / parallel tool execution
    private Executor toolExecutor;

    // Guard components — optional, non-invasive integration (Req 4.1, 4.2, 4.8)
    @Autowired(required = false)
    private ToolResultClassifier toolResultClassifier;

    @Autowired(required = false)
    private EmbeddingLoopDetector embeddingLoopDetector;

    @Autowired(required = false)
    private ObservationSanitizer observationSanitizer;

    @Autowired(required = false)
    private AccessDecisionService accessDecisionService;

    /** Tool calls allowed in this run — feeds {@link com.yupi.yuaiagent.access.QuotaPolicyVoter}. */
    private final AtomicInteger toolCallCount = new AtomicInteger(0);

    public ToolCallAgent(ToolCallback[] availableTools) {
        this(availableTools, java.util.concurrent.ForkJoinPool.commonPool());
    }

    public ToolCallAgent(ToolCallback[] availableTools, Executor toolExecutor) {
        super();
        this.availableTools = availableTools;
        this.toolExecutor = toolExecutor;
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
        toolCallCount.set(0);
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
            // Goal Anchor: re-attach goal on every think() so long loops don't forget the mission
            String systemPrompt = getSystemPrompt();
            if (StrUtil.isNotBlank(getTurnGoal())) {
                systemPrompt = systemPrompt + "\n\n" + GoalAnchor.buildBlock(getTurnGoal(), null, getName());
            }
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(systemPrompt)
                    .toolCallbacks(toolsForLlm())
                    .call()
                    .chatResponse();
            recordThinkTokenUsage(chatResponse, assistantMessageText(chatResponse));
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
            if (getConsecutiveFailureGuard() != null) {
                getConsecutiveFailureGuard().recordFailure("think:" + e.getMessage());
                if (getConsecutiveFailureGuard().shouldStop()) {
                    setState(AgentState.FINISHED);
                    escalateOnConsecutiveFailure();
                }
            }
            return false;
        }
    }

    private static String assistantMessageText(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getText();
    }

    private void recordThinkTokenUsage(ChatResponse chatResponse, String fallbackText) {
        if (getRunBudget() == null) {
            return;
        }
        int tokens = ChatUsageExtractor.extractTotalTokens(chatResponse);
        if (tokens <= 0) {
            tokens = ChatUsageExtractor.estimateFromText(fallbackText);
        }
        getRunBudget().record(tokens);
        if (getRunBudget().isExhausted()) {
            log.warn("[ToolCallAgent] run token budget exhausted after think name={} used={}",
                    getName(), getRunBudget().getTokensUsed());
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

        // Resolve whether this batch is safe to auto-retry on timeout (read-only only)
        List<AssistantMessage.ToolCall> pendingCalls =
                toolCallChatResponse.getResult().getOutput().getToolCalls();
        boolean retryableBatch = pendingCalls.stream()
                .allMatch(tc -> ToolSideEffectPolicy.isRetryableOnTimeout(tc.name()));

        // Parallel fan-out (Ch3) with per-tool timeout; auto-retry only for read-only batches
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = null;
        int retryCount = 0;
        int maxAttempts = retryableBatch ? (MAX_TIMEOUT_RETRIES + 1) : 1;

        while (retryCount < maxAttempts) {
            toolExecutionResult = ParallelToolCallingSupport.execute(
                    prompt, toolCallChatResponse, availableTools, toolExecutor, TOOL_TIMEOUT_SECONDS,
                    this::isToolAllowed);
            boolean anyTimeout = lastResponsesTimedOut(toolExecutionResult);
            if (!anyTimeout) {
                break;
            }
            retryCount++;
            if (retryCount < maxAttempts) {
                log.warn("[ToolCall] read-only batch timed out (attempt {}/{}), retrying...",
                        retryCount, MAX_TIMEOUT_RETRIES);
                continue;
            }
            log.error("[ToolCall] tool execution timed out after {}s (retries exhausted, retryable={})",
                    TOOL_TIMEOUT_SECONDS, retryableBatch);
            if (toolCallSpan != null && getTraceRecorder() != null) {
                getTraceRecorder().failSpan(getTraceContext(), toolCallSpan,
                        "Tool execution timed out after " + TOOL_TIMEOUT_SECONDS + "s");
            }
            if (toolResultClassifier != null) {
                try {
                    toolResultClassifier.classifyAndGuide(null, true, getMessageList());
                } catch (Exception ex) {
                    log.warn("[ToolCallAgent] result classification failed, skipping: {}", ex.getMessage());
                }
            }
            if (getConsecutiveFailureGuard() != null) {
                getConsecutiveFailureGuard().recordFailure("TIMEOUT after retries");
                if (getConsecutiveFailureGuard().shouldStop()) {
                    setState(AgentState.FINISHED);
                    escalateOnConsecutiveFailure();
                    return getConsecutiveFailureGuard().stopMessage();
                }
            }
            // Still commit conversation so model can self-correct / switch to start* async tools
            setMessageList(sanitizeHistory(toolExecutionResult.conversationHistory()));
            return "工具执行超时（" + TOOL_TIMEOUT_SECONDS + "秒）。"
                    + (retryableBatch ? "只读工具已重试" + MAX_TIMEOUT_RETRIES + "次。" : "副作用工具未自动重试（防重复执行）。")
                    + "可改用 startScrapeWebPage / startDownloadResource / startGeneratePDF + checkAsyncToolTask。";
        }

        // Sanitize observations before they pollute context (Ch3 Sanitizer Layer)
        List<Message> sanitizedHistory = sanitizeHistory(toolExecutionResult.conversationHistory());
        setMessageList(sanitizedHistory);
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(sanitizedHistory);
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals(TERMINATE_TOOL_NAME));
        if (terminateToolCalled) {
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
                if (getConsecutiveFailureGuard() != null) {
                    if (grade == ToolResultClassifier.ResultGrade.NORMAL) {
                        getConsecutiveFailureGuard().recordSuccess();
                    } else {
                        // Ch4 Reflect: explicit critique before next Think
                        com.yupi.yuaiagent.agent.loop.StepReflector.reflectIfNeeded(
                                grade, results, getMessageList());
                        getConsecutiveFailureGuard().recordFailure(grade.name() + ":" + results);
                        if (getConsecutiveFailureGuard().shouldStop()) {
                            setState(AgentState.FINISHED);
                            escalateOnConsecutiveFailure();
                            return getConsecutiveFailureGuard().stopMessage();
                        }
                    }
                } else if (grade != ToolResultClassifier.ResultGrade.NORMAL) {
                    com.yupi.yuaiagent.agent.loop.StepReflector.reflectIfNeeded(
                            grade, results, getMessageList());
                }
            } catch (Exception e) {
                log.warn("[ToolCallAgent] result classification failed, skipping: {}", e.getMessage());
            }
        } else if (getConsecutiveFailureGuard() != null) {
            getConsecutiveFailureGuard().recordSuccess();
        }

        log.debug(results);
        return results;
    }

    private List<Message> sanitizeHistory(List<Message> history) {
        if (history == null || history.isEmpty() || observationSanitizer == null) {
            return history;
        }
        List<Message> out = new ArrayList<>(history.size());
        for (Message m : history) {
            if (m instanceof ToolResponseMessage trm) {
                List<ToolResponseMessage.ToolResponse> cleaned = trm.getResponses().stream()
                        .map(r -> new ToolResponseMessage.ToolResponse(
                                r.id(), r.name(), observationSanitizer.sanitize(r.responseData())))
                        .collect(Collectors.toList());
                out.add(new ToolResponseMessage(cleaned, trm.getMetadata()));
            } else {
                out.add(m);
            }
        }
        return out;
    }

    private static boolean lastResponsesTimedOut(ToolExecutionResult result) {
        if (result == null || result.conversationHistory() == null || result.conversationHistory().isEmpty()) {
            return false;
        }
        Message last = CollUtil.getLast(result.conversationHistory());
        if (!(last instanceof ToolResponseMessage trm)) {
            return false;
        }
        return trm.getResponses().stream()
                .anyMatch(r -> r.responseData() != null && r.responseData().contains("timed out"));
    }

    /**
     * Park for human when consecutive failures trip the fuse (optional HITL).
     */
    private void escalateOnConsecutiveFailure() {
        if (getHumanHandoffService() == null || !StrUtil.isNotBlank(getChatId())) {
            return;
        }
        try {
            var guard = getConsecutiveFailureGuard();
            String reason = "consecutive_tool_failures";
            String summary = guard != null ? guard.stopMessage() : "连续工具失败";
            getHumanHandoffService().park(
                    getChatId(),
                    StrUtil.blankToDefault(getUserId(), "anonymous"),
                    null,
                    reason,
                    summary);
            log.warn("[ToolCallAgent] HITL parked after consecutive failures chatId={}", getChatId());
        } catch (Exception e) {
            log.warn("[ToolCallAgent] HITL escalate skipped: {}", e.getMessage());
        }
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

    private ToolCallback[] toolsForLlm() {
        return PermittedToolFilter.filter(
                accessDecisionService,
                AgentCodeResolver.resolve(getName()),
                availableTools);
    }

    /**
     * Defense in depth: even if the model invents a hidden tool name, refuse execution.
     */
    private boolean isToolAllowed(String toolName) {
        if (ToolNameMatcher.isAlwaysAllowed(toolName)) {
            return true;
        }
        if (accessDecisionService == null) {
            return false;
        }
        String agentCode = AgentCodeResolver.resolve(getName());
        while (true) {
            int current = toolCallCount.get();
            boolean allowed = accessDecisionService.check(AccessDecisionContext.builder()
                    .agentCode(agentCode)
                    .toolName(toolName)
                    .userId(getUserId())
                    .requestId(getChatId())
                    .currentToolCallCount(current)
                    .build());
            if (!allowed) {
                return false;
            }
            if (toolCallCount.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
}
