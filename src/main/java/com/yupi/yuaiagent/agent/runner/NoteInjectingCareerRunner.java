package com.yupi.yuaiagent.agent.runner;

import com.yupi.yuaiagent.agent.AgentRunner;
import com.yupi.yuaiagent.agent.GeneralCareerAgent;
import com.yupi.yuaiagent.agent.output.AgentOutput;
import com.yupi.yuaiagent.agent.output.TextOutput;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.context.ConversationContext;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class NoteInjectingCareerRunner implements AgentRunner {

    private final String agentCode;
    private final String note;
    private final GeneralCareerAgent agent;
    private final AtomicReference<TokenUsage> lastUsage = new AtomicReference<>(TokenUsage.ZERO);

    public NoteInjectingCareerRunner(String agentCode, String note, GeneralCareerAgent agent) {
        this.agentCode = agentCode;
        this.note = note == null ? "" : note;
        this.agent = agent;
    }

    @Override
    public String agentCode() {
        return agentCode;
    }

    @Override
    public AgentOutput run(ConversationContext context, String userMessage) {
        String chatId = context != null && StringUtils.hasText(context.chatId()) ? context.chatId() : "default";
        String injection = context != null && context.injection() != null ? context.injection() : "";
        String combined = injection.isBlank() ? note : injection + "\n" + note;
        String answer = agent.chat(userMessage, chatId, combined);
        long prompt = (userMessage == null ? 0 : userMessage.length()) + combined.length();
        long completion = answer == null ? 0 : answer.length();
        lastUsage.set(new TokenUsage(prompt, prompt, completion));
        return new TextOutput(answer, List.of());
    }

    public String note() {
        return note;
    }

    @Override
    public TokenUsage getLastTokenUsage() {
        return lastUsage.get();
    }
}
