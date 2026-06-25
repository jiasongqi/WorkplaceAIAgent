package com.yupi.yuaiagent.agent.runner;

import com.yupi.yuaiagent.agent.AgentRunner;
import com.yupi.yuaiagent.agent.ResumeAgent;
import com.yupi.yuaiagent.agent.output.AgentOutput;
import com.yupi.yuaiagent.agent.output.TextOutput;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.context.ConversationContext;
import lombok.RequiredArgsConstructor;

/**
 * AgentRunner adapter for ResumeAgent.
 */
@RequiredArgsConstructor
public class ResumeAgentRunner implements AgentRunner {

    private final ResumeAgent resumeAgent;

    @Override
    public AgentOutput run(ConversationContext context, String userMessage) {
        String answer = resumeAgent.chat(userMessage, "default");
        return new TextOutput(answer, java.util.List.of());
    }

    @Override
    public TokenUsage getLastTokenUsage() {
        return TokenUsage.ZERO;
    }
}
