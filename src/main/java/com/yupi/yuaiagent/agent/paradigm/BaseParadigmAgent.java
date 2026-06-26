package com.yupi.yuaiagent.agent.paradigm;

import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceRecorder;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Base Paradigm Agent — abstract base class for paradigm-specific agents.
 *
 * <p>Provides common functionality for all paradigm agents:</p>
 * <ul>
 *     <li>ChatClient management</li>
 *     <li>Message history</li>
 *     <li>Trace integration</li>
 *     <li>Execution lifecycle</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Data
@Slf4j
public abstract class BaseParadigmAgent {

    // Core components
    private final ChatClient chatClient;
    private final List<Message> messageList = new ArrayList<>();

    // Trace context (optional)
    private TraceContext traceContext;
    private TraceRecorder traceRecorder;

    /**
     * Constructor with ChatClient.
     */
    protected BaseParadigmAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Set trace context for execution tracing.
     */
    public void setTraceContext(TraceContext traceContext, TraceRecorder traceRecorder) {
        this.traceContext = traceContext;
        this.traceRecorder = traceRecorder;
    }

    /**
     * Add a message to the conversation history.
     */
    public void addMessage(Message message) {
        messageList.add(message);
    }

    /**
     * Get the paradigm name.
     */
    public abstract String getParadigmName();

    /**
     * Get the paradigm description.
     */
    public abstract String getParadigmDescription();

    /**
     * Execute the paradigm-specific logic.
     *
     * @return execution result
     */
    public abstract String execute();

    /**
     * Run the agent with the given user prompt.
     *
     * @param userPrompt user's input
     * @return execution result
     */
    public String run(String userPrompt) {
        log.info("[{}] Starting execution with prompt: {}", getParadigmName(),
                userPrompt.substring(0, Math.min(100, userPrompt.length())));

        // Add user message to history
        addMessage(new org.springframework.ai.chat.messages.UserMessage(userPrompt));

        // Execute paradigm-specific logic
        String result = execute();

        log.info("[{}] Execution completed, result length: {}", getParadigmName(), result.length());
        return result;
    }

    // ─── Trace Helpers ─────────────────────────────────────────────────

    /**
     * Start a paradigm-level trace span.
     */
    protected TraceSpan startParadigmTrace(String paradigmName) {
        if (traceContext != null && traceRecorder != null) {
            return traceRecorder.startSpan(traceContext, TraceStepType.SUB_AGENT_EXECUTION,
                    "Paradigm: " + paradigmName);
        }
        return null;
    }

    /**
     * End a paradigm-level trace span.
     */
    protected void endParadigmTrace(TraceSpan span, boolean success) {
        if (span != null && traceRecorder != null) {
            if (success) {
                traceRecorder.endSpan(traceContext, span);
            } else {
                traceRecorder.failSpan(traceContext, span, "Paradigm execution failed");
            }
        }
    }

    /**
     * Start a phase-level trace span.
     */
    protected TraceSpan startPhaseTrace(String phaseName) {
        if (traceContext != null && traceRecorder != null) {
            return traceRecorder.startSpan(traceContext, TraceStepType.SUB_AGENT_EXECUTION,
                    "Phase: " + phaseName);
        }
        return null;
    }

    /**
     * End a phase-level trace span.
     */
    protected void endPhaseTrace(TraceSpan span, boolean success) {
        if (span != null && traceRecorder != null) {
            if (success) {
                traceRecorder.endSpan(traceContext, span);
            } else {
                traceRecorder.failSpan(traceContext, span, "Phase execution failed");
            }
        }
    }

    /**
     * Add a step to the trace.
     */
    protected void addStepToTrace(String stepName, String result) {
        if (traceContext != null && traceRecorder != null) {
            TraceSpan stepSpan = traceRecorder.startSpan(traceContext, TraceStepType.SUB_AGENT_EXECUTION,
                    "Step: " + stepName);
            traceRecorder.putMetadata(stepSpan, "result_preview",
                    result.substring(0, Math.min(200, result.length())));
            traceRecorder.endSpan(traceContext, stepSpan);
        }
    }

    /**
     * Clear message history (for cleanup).
     */
    protected void clearHistory() {
        messageList.clear();
    }
}
