package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.output.AgentOutput;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.context.ConversationContext;

/**
 * Agent execution interface — Agent only sees ConversationContext, not RuntimeContext.
 *
 * @author jsq
 */
public interface AgentRunner {

    /**
     * Execute the agent with the given context and user message.
     */
    AgentOutput run(ConversationContext context, String userMessage);

    /**
     * Returns token usage from the last run() call.
     */
    TokenUsage getLastTokenUsage();

    default String agentCode() {
        return getClass().getSimpleName();
    }

    default boolean supportsStreaming() {
        return false;
    }

    default boolean holdsSession(String chatId) {
        return false;
    }

    default void runStream(ConversationContext context, String userMessage,
                           java.util.function.Consumer<String> sink) {
        AgentOutput output = run(context, userMessage);
        if (sink != null) {
            sink.accept(output == null ? "" : output.summary());
        }
    }
}
