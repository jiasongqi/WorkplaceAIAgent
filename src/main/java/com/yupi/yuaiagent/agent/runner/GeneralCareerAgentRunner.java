package com.yupi.yuaiagent.agent.runner;

import com.yupi.yuaiagent.agent.AgentRunner;
import com.yupi.yuaiagent.agent.GeneralCareerAgent;
import com.yupi.yuaiagent.agent.output.AgentOutput;
import com.yupi.yuaiagent.agent.output.TextOutput;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.context.ConversationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class GeneralCareerAgentRunner implements AgentRunner {
    private final GeneralCareerAgent agent;

    @Override
    public String agentCode() {
        return "GENERAL";
    }

    @Override
    public AgentOutput run(ConversationContext context, String userMessage) {
        String chatId = StringUtils.hasText(context.chatId()) ? context.chatId() : "default";
        String injection = context.injection() != null ? context.injection() : "";
        return new TextOutput(agent.chat(userMessage, chatId, injection), java.util.List.of());
    }

    @Override
    public TokenUsage getLastTokenUsage() { return TokenUsage.ZERO; }
}
