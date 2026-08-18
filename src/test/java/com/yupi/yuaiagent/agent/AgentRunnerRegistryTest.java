package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.output.TextOutput;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.context.ConversationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRunnerRegistryTest {

    @Test
    void rejectsDuplicateAgentCodes() {
        assertThatThrownBy(() -> new AgentRunnerRegistry(List.of(stub("RESUME"), stub("RESUME"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESUME");
    }

    @Test
    void indexesByAgentCode() {
        AgentRunnerRegistry registry = new AgentRunnerRegistry(List.of(stub("RESUME"), stub("GENERAL")));
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.get("RESUME")).isPresent();
        assertThat(registry.get("missing")).isEmpty();
    }

    static AgentRunner stub(String code) {
        return new AgentRunner() {
            @Override
            public String agentCode() {
                return code;
            }

            @Override
            public com.yupi.yuaiagent.agent.output.AgentOutput run(ConversationContext context, String userMessage) {
                return new TextOutput(code, List.of());
            }

            @Override
            public TokenUsage getLastTokenUsage() {
                return TokenUsage.ZERO;
            }
        };
    }
}
