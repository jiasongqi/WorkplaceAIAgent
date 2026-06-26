package com.yupi.yuaiagent.agent.paradigm;

import com.yupi.yuaiagent.nlu.NluIntent;
import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Paradigm Service — orchestrates paradigm selection and agent execution.
 *
 * <p>This service provides a high-level API for executing tasks using
 * the optimal reasoning paradigm based on task characteristics.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Automatic paradigm selection
 * String result = paradigmService.execute("分析我的职业发展路径", userId);
 *
 * // User-specified paradigm
 * String result = paradigmService.executeWithParadigm("写一篇总结", "reflection", userId);
 * }</pre>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParadigmService {

    private final ParadigmSelector paradigmSelector;
    private final ParadigmAgentFactory agentFactory;

    /**
     * Execute a task with automatic paradigm selection.
     *
     * @param message user message
     * @param userId  user ID (for logging)
     * @return execution result
     */
    public String execute(String message, String userId) {
        return execute(message, userId, null, 0.0, null);
    }

    /**
     * Execute a task with automatic paradigm selection and trace context.
     *
     * @param message       user message
     * @param userId        user ID
     * @param intent        NLU intent (nullable)
     * @param confidence    NLU confidence (0.0 if unknown)
     * @param traceContext   trace context (nullable)
     * @return execution result
     */
    public String execute(String message, String userId, NluIntent intent,
                           double confidence, TraceContext traceContext) {
        // 1. Select paradigm
        AgentParadigm paradigm = paradigmSelector.select(intent, message, confidence);
        log.info("[ParadigmService] Selected paradigm: {} for user={}, intent={}",
                paradigm, userId, intent);

        // 2. Create and execute agent
        return executeWithParadigm(message, paradigm, userId, traceContext);
    }

    /**
     * Execute a task with user-specified paradigm.
     *
     * @param message      user message
     * @param paradigmCode paradigm code (e.g., "react", "plan_and_solve", "reflection")
     * @param userId       user ID
     * @return execution result
     */
    public String executeWithParadigm(String message, String paradigmCode, String userId) {
        return executeWithParadigm(message, paradigmCode, userId, null);
    }

    /**
     * Execute a task with user-specified paradigm and trace context.
     *
     * @param message      user message
     * @param paradigmCode paradigm code
     * @param userId       user ID
     * @param traceContext  trace context (nullable)
     * @return execution result
     */
    public String executeWithParadigm(String message, String paradigmCode, String userId,
                                       TraceContext traceContext) {
        AgentParadigm paradigm = AgentParadigm.fromCode(paradigmCode);
        return executeWithParadigm(message, paradigm, userId, traceContext);
    }

    /**
     * Execute a task with a specific paradigm.
     *
     * @param message      user message
     * @param paradigm     paradigm to use
     * @param userId       user ID
     * @param traceContext  trace context (nullable)
     * @return execution result
     */
    private String executeWithParadigm(String message, AgentParadigm paradigm,
                                        String userId, TraceContext traceContext) {
        log.info("[ParadigmService] Executing with paradigm: {} for user={}", paradigm, userId);

        // For REACT paradigm, use existing ToolCallAgent
        if (paradigm == AgentParadigm.REACT) {
            log.info("[ParadigmService] REACT paradigm delegates to existing ToolCallAgent");
            // This should be handled by the caller (e.g., AiChatAgent)
            return null;
        }

        // For other paradigms, create and execute paradigm agent
        try {
            BaseParadigmAgent agent = agentFactory.create(paradigm, traceContext, null);
            String result = agent.run(message);

            log.info("[ParadigmService] Execution completed with paradigm: {}, result length: {}",
                    paradigm, result.length());
            return result;

        } catch (Exception e) {
            log.error("[ParadigmService] Execution failed with paradigm {}: {}",
                    paradigm, e.getMessage(), e);
            return "执行错误：" + e.getMessage();
        }
    }

    /**
     * Get paradigm selection explanation (for debugging/transparency).
     *
     * @param message    user message
     * @param intent     NLU intent
     * @param confidence NLU confidence
     * @return explanation string
     */
    public String explainSelection(String message, NluIntent intent, double confidence) {
        AgentParadigm paradigm = paradigmSelector.select(intent, message, confidence);
        return paradigmSelector.explain(paradigm, intent, message);
    }
}
