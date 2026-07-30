package com.yupi.yuaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.agent.loop.AgentDepthContext;
import com.yupi.yuaiagent.agent.loop.AgentLoopResult;
import com.yupi.yuaiagent.agent.loop.LoopRunBudget;
import com.yupi.yuaiagent.agent.loop.LoopWrapUp;
import com.yupi.yuaiagent.agent.model.AgentState;
import com.yupi.yuaiagent.guard.ConsecutiveFailureGuard;
import com.yupi.yuaiagent.hitl.HumanHandoffService;
import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceRecorder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * Ch4: maxSteps 触顶走 Wrap-up；嵌套深度受 {@link AgentDepthContext} 限制。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    private String name;

    private String systemPrompt;
    private String nextStepPrompt;

    private AgentState state = AgentState.IDLE;

    private int currentStep = 0;
    private int maxSteps = 10;

    private ChatClient chatClient;

    private List<Message> messageList = new ArrayList<>();

    private TraceContext traceContext;
    private TraceRecorder traceRecorder;

    private String turnGoal;

    private ConsecutiveFailureGuard consecutiveFailureGuard;

    private String chatId;
    private String userId;
    private HumanHandoffService humanHandoffService;

    /** Structured terminal payload for the last run (Ch4). */
    private AgentLoopResult lastLoopResult;

    /** Per-run token budget fuse (Ch4 §2.3). */
    private LoopRunBudget runBudget;

    /** Called once in cleanup() to persist run token usage (e.g. daily quota). */
    private java.util.function.IntConsumer runTokenFinalizer;

    private Executor executor = java.util.concurrent.ForkJoinPool.commonPool();

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public String run(String userPrompt) {
        return AgentDepthContext.runWithDepth(
                () -> doRun(userPrompt),
                () -> {
                    lastLoopResult = AgentLoopResult.failed(
                            AgentDepthContext.denyMessage(AgentDepthContext.DEFAULT_MAX_DEPTH),
                            "", true);
                    return lastLoopResult.summary();
                });
    }

    private String doRun(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        this.state = AgentState.RUNNING;
        this.lastLoopResult = null;
        messageList.add(new UserMessage(userPrompt));
        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Agent execution interrupted for name={}", name);
                    state = AgentState.FINISHED;
                    break;
                }
                if (runBudget != null && runBudget.isExhausted()) {
                    log.warn("[BaseAgent] run token budget exhausted before step {} name={}", i + 1, name);
                    break;
                }
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            if (shouldWrapUp()) {
                String wrap = applyWrapUp(results);
                results.add(wrap);
            } else if (state == AgentState.FINISHED && lastLoopResult == null) {
                lastLoopResult = AgentLoopResult.success(
                        "任务在步数预算内结束",
                        truncateJoin(results, 1500));
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            lastLoopResult = AgentLoopResult.failed(e.getMessage(), truncateJoin(results, 800), false);
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            this.cleanup();
        }
    }

    public void runStream(String userPrompt, SseEmitter externalEmitter) {
        CompletableFuture.runAsync(() -> AgentDepthContext.runWithDepth(() -> {
            runStreamBody(userPrompt, externalEmitter, true);
            return null;
        }, () -> {
            try {
                String deny = AgentDepthContext.denyMessage(AgentDepthContext.DEFAULT_MAX_DEPTH);
                lastLoopResult = AgentLoopResult.failed(deny, "", true);
                externalEmitter.send(SseEmitter.event().name("message").data(deny));
                externalEmitter.send(SseEmitter.event().data("[DONE]"));
                externalEmitter.complete();
            } catch (IOException ignored) {
                externalEmitter.completeWithError(new IllegalStateException(denyMsg()));
            }
            return null;
        }), executor);
    }

    public SseEmitter runStream(String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(300000L);
        CompletableFuture.runAsync(() -> AgentDepthContext.runWithDepth(() -> {
            runStreamBody(userPrompt, sseEmitter, false);
            return null;
        }, () -> {
            try {
                String deny = AgentDepthContext.denyMessage(AgentDepthContext.DEFAULT_MAX_DEPTH);
                lastLoopResult = AgentLoopResult.failed(deny, "", true);
                sseEmitter.send(deny);
                sseEmitter.send("[DONE]");
                sseEmitter.complete();
            } catch (IOException ex) {
                sseEmitter.completeWithError(ex);
            }
            return null;
        }), executor);

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    private void runStreamBody(String userPrompt, SseEmitter emitter, boolean namedEvents) {
        try {
            if (this.state != AgentState.IDLE) {
                send(emitter, namedEvents, "error", "错误：无法从状态运行代理：" + this.state);
                completeDone(emitter, namedEvents);
                return;
            }
            if (StrUtil.isBlank(userPrompt)) {
                send(emitter, namedEvents, "error", "错误：不能使用空提示词运行代理");
                completeDone(emitter, namedEvents);
                return;
            }
        } catch (Exception e) {
            emitter.completeWithError(e);
            return;
        }
        this.state = AgentState.RUNNING;
        this.lastLoopResult = null;
        messageList.add(new UserMessage(userPrompt));
        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Agent stream execution interrupted for name={}", name);
                    state = AgentState.FINISHED;
                    break;
                }
                if (runBudget != null && runBudget.isExhausted()) {
                    log.warn("[BaseAgent] run token budget exhausted before step {} name={}", i + 1, name);
                    break;
                }
                currentStep = i + 1;
                log.info("Executing step {}/{}", currentStep, maxSteps);
                String stepResult = step();
                String result = namedEvents ? stepResult : ("Step " + currentStep + ": " + stepResult);
                results.add(namedEvents ? stepResult : result);
                send(emitter, namedEvents, "message", namedEvents ? stepResult : result);
            }
            if (shouldWrapUp()) {
                String wrap = applyWrapUp(results);
                results.add(wrap);
                send(emitter, namedEvents, "message", wrap);
            } else if (state == AgentState.FINISHED && lastLoopResult == null) {
                lastLoopResult = AgentLoopResult.success(
                        "任务在步数预算内结束",
                        truncateJoin(results, 1500));
            }
            completeDone(emitter, namedEvents);
        } catch (Exception e) {
            state = AgentState.ERROR;
            lastLoopResult = AgentLoopResult.failed(e.getMessage(), truncateJoin(results, 800), false);
            log.error("error executing agent", e);
            try {
                send(emitter, namedEvents, "error", "执行错误：" + e.getMessage());
                completeDone(emitter, namedEvents);
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        } finally {
            this.cleanup();
        }
    }

    private boolean shouldWrapUp() {
        // Budget exhausted without self-terminate (Ch4 Wrap-up); clean Terminate → SUCCESS path
        if (state != AgentState.RUNNING) {
            return false;
        }
        if (runBudget != null && runBudget.isExhausted()) {
            return true;
        }
        return currentStep >= maxSteps;
    }

    private String applyWrapUp(List<String> results) {
        state = AgentState.FINISHED;
        String goal = StrUtil.blankToDefault(turnGoal, name);
        String budgetReason = runBudget != null && runBudget.isExhausted()
                ? runBudget.budgetReasonForWrapUp() : null;
        AgentLoopResult result = LoopWrapUp.wrapUp(goal, results, maxSteps, chatClient, budgetReason);
        this.lastLoopResult = result;
        log.info("[BaseAgent] Wrap-up applied name={} status={} runTokens={}",
                name, result.status(), runBudget != null ? runBudget.getTokensUsed() : 0);
        return result.toUserFacingWrapUp();
    }

    private static void send(SseEmitter emitter, boolean named, String event, String data) throws IOException {
        if (named) {
            emitter.send(SseEmitter.event().name(event).data(data));
        } else {
            emitter.send(data);
        }
    }

    private static void completeDone(SseEmitter emitter, boolean named) throws IOException {
        if (named) {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } else {
            emitter.send("[DONE]");
        }
        emitter.complete();
    }

    private static String denyMsg() {
        return AgentDepthContext.denyMessage(AgentDepthContext.DEFAULT_MAX_DEPTH);
    }

    private static String truncateJoin(List<String> steps, int max) {
        if (steps == null || steps.isEmpty()) {
            return "";
        }
        String joined = String.join("\n", steps);
        return joined.length() <= max ? joined : joined.substring(0, max) + "...";
    }

    public abstract String step();

    /**
     * 清理资源：重置状态、步骤计数和消息历史，确保实例可安全复用。
     * 保留 lastLoopResult 供调用方读取。
     */
    protected void cleanup() {
        if (runBudget != null && runTokenFinalizer != null && runBudget.getTokensUsed() > 0) {
            try {
                runTokenFinalizer.accept(runBudget.getTokensUsed());
            } catch (Exception e) {
                log.warn("[BaseAgent] run token finalizer failed: {}", e.getMessage());
            }
        }
        this.messageList.clear();
        this.state = AgentState.IDLE;
        this.currentStep = 0;
    }
}
