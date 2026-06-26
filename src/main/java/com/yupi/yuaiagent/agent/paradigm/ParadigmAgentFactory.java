package com.yupi.yuaiagent.agent.paradigm;

import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Paradigm Agent Factory — creates paradigm-specific agent instances.
 *
 * <p>This factory centralizes the creation of paradigm agents and ensures
 * they are properly configured with ChatClient and trace components.</p>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParadigmAgentFactory {

    private final ChatClient.Builder chatClientBuilder;

    /**
     * Create a paradigm agent based on the selected paradigm.
     *
     * @param paradigm     selected paradigm
     * @param traceContext trace context (nullable)
     * @param traceRecorder trace recorder (nullable)
     * @return configured paradigm agent
     */
    public BaseParadigmAgent create(AgentParadigm paradigm,
                                     TraceContext traceContext,
                                     TraceRecorder traceRecorder) {
        log.debug("[ParadigmAgentFactory] Creating agent for paradigm: {}", paradigm);

        ChatClient chatClient = chatClientBuilder.build();
        BaseParadigmAgent agent;

        switch (paradigm) {
            case PLAN_AND_SOLVE:
                agent = new PlanAndSolveAgent(chatClient);
                break;

            case REFLECTION:
                agent = new ReflectionAgent(chatClient);
                break;

            case REACT:
            default:
                // For REACT, use the existing ToolCallAgent via Spring context
                // This is handled separately in the integration layer
                throw new UnsupportedOperationException(
                        "REACT paradigm should be handled by existing ToolCallAgent");
        }

        // Set trace context if available
        if (traceContext != null && traceRecorder != null) {
            agent.setTraceContext(traceContext, traceRecorder);
        }

        log.info("[ParadigmAgentFactory] Created {} agent", paradigm.name());
        return agent;
    }

    /**
     * Create a paradigm agent with default trace context.
     *
     * @param paradigm selected paradigm
     * @return configured paradigm agent
     */
    public BaseParadigmAgent create(AgentParadigm paradigm) {
        return create(paradigm, null, null);
    }

    /**
     * Check if a paradigm is supported by this factory.
     *
     * @param paradigm paradigm to check
     * @return true if supported
     */
    public boolean supports(AgentParadigm paradigm) {
        return paradigm == AgentParadigm.PLAN_AND_SOLVE ||
               paradigm == AgentParadigm.REFLECTION;
    }
}
