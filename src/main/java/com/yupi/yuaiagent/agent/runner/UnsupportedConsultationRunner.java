package com.yupi.yuaiagent.agent.runner;

import com.yupi.yuaiagent.agent.AgentRunner;
import com.yupi.yuaiagent.agent.output.AgentOutput;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.context.ConversationContext;

/**
 * Consultation stays on the dedicated Orchestrator path until session-lock parity lands.
 */
public class UnsupportedConsultationRunner implements AgentRunner {

    @Override
    public String agentCode() {
        return "CONSULTATION";
    }

    @Override
    public boolean holdsSession(String chatId) {
        return chatId != null && !chatId.isBlank();
    }

    @Override
    public AgentOutput run(ConversationContext context, String userMessage) {
        throw new UnsupportedOperationException("CONSULTATION is not on the generic AgentRunner path");
    }

    @Override
    public TokenUsage getLastTokenUsage() {
        return TokenUsage.ZERO;
    }
}
