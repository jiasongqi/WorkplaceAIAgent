package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.output.TextOutput;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.context.ConversationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunnerTokenUsageTest {

    @Test
    void lastTokenUsageIsRecordedFromRun() {
        AgentRunner runner = new AgentRunner() {
            private TokenUsage last = TokenUsage.ZERO;

            @Override
            public String agentCode() {
                return "DATA_QUERY";
            }

            @Override
            public com.yupi.yuaiagent.agent.output.AgentOutput run(ConversationContext context, String userMessage) {
                last = new TokenUsage(12, 12, 30);
                return new TextOutput("ok", List.of());
            }

            @Override
            public TokenUsage getLastTokenUsage() {
                return last;
            }
        };
        runner.run(new ConversationContext("", "", List.of(), "c", ""), "query kpi");
        assertThat(runner.getLastTokenUsage().totalTokens()).isEqualTo(42);
    }
}
