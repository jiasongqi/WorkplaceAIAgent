package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.output.TextOutput;
import com.yupi.yuaiagent.agent.runner.UnsupportedConsultationRunner;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.context.ConversationContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRunnerCoverageTest {

    @Test
    void everyIntentHasRunnerCodeAndConsultationIsExplicitlyUnsupported() {
        List<AgentRunner> runners = new ArrayList<>();
        for (String code : List.of("RESUME", "NEGOTIATION", "ESCAPE", "GENERAL", "DATA_QUERY", "DIGITAL_EMPLOYEE")) {
            runners.add(stub(code));
        }
        runners.add(new UnsupportedConsultationRunner());
        AgentRunnerRegistry registry = new AgentRunnerRegistry(runners);

        for (AgentIntent intent : AgentIntent.values()) {
            String code = OrchestratorDispatch.runnerCode(intent);
            assertThat(registry.get(code)).as(code).isPresent();
        }

        UnsupportedConsultationRunner consultation = new UnsupportedConsultationRunner();
        assertThat(consultation.holdsSession("c1")).isTrue();
        assertThatThrownBy(() -> consultation.run(new ConversationContext("", "", List.of(), "c1", ""), "hi"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static AgentRunner stub(String code) {
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
