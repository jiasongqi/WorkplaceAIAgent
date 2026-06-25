package com.yupi.yuaiagent.agent.runner;

import com.yupi.yuaiagent.agent.AgentRunner;
import com.yupi.yuaiagent.agent.GeneralCareerAgent;
import com.yupi.yuaiagent.agent.output.AgentOutput;
import com.yupi.yuaiagent.agent.output.TextOutput;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.context.ConversationContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GeneralCareerAgentRunner implements AgentRunner {
    private final GeneralCareerAgent agent;

    @Override
    public AgentOutput run(ConversationContext context, String userMessage) {
        return new TextOutput(agent.chat(userMessage, "default"), java.util.List.of());
    }

    @Override
    public TokenUsage getLastTokenUsage() { return TokenUsage.ZERO; }
}
