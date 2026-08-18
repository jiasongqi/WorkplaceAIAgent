package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.output.TextOutput;
import com.yupi.yuaiagent.agent.runner.UnsupportedConsultationRunner;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.context.ConversationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorDispatchTest {

    @Test
    void shadowComparesRunnerIdentityWithoutCallingLlm() {
        AgentRunnerRegistry registry = new AgentRunnerRegistry(List.of(
                stub("RESUME"),
                new UnsupportedConsultationRunner()
        ));
        var resume = OrchestratorDispatch.shadow(AgentIntent.RESUME, registry);
        assertThat(resume.expectedRunner()).isEqualTo("RESUME");
        assertThat(resume.actualRunner()).isEqualTo("RESUME");
        assertThat(resume.drift()).isFalse();

        var consultation = OrchestratorDispatch.shadow(AgentIntent.CONSULTATION, registry);
        assertThat(consultation.consultationHoldsSession()).isTrue();
        assertThat(consultation.drift()).isFalse();

        var missing = OrchestratorDispatch.shadow(AgentIntent.GENERAL, registry);
        assertThat(missing.drift()).isTrue();
    }

    @Test
    void dataQueryAndDigitalEmployeeNotesAreStable() {
        assertThat(OrchestratorAgent.DATA_QUERY_FALLBACK_NOTE).contains("数据查询");
        assertThat(OrchestratorAgent.DIGITAL_EMPLOYEE_NOTE).contains("数字员工");
    }

    private static AgentRunner stub(String code) {
        return new AgentRunner() {
            @Override
            public String agentCode() {
                return code;
            }

            @Override
            public com.yupi.yuaiagent.agent.output.AgentOutput run(ConversationContext context, String userMessage) {
                return new TextOutput(userMessage, List.of());
            }

            @Override
            public TokenUsage getLastTokenUsage() {
                return TokenUsage.ZERO;
            }
        };
    }
}
