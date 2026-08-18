package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.runner.UnsupportedConsultationRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultationSessionLockTest {

    @Test
    void consultationRunnerHoldsSessionAndIsNotOnGenericPath() {
        UnsupportedConsultationRunner runner = new UnsupportedConsultationRunner();
        assertThat(runner.holdsSession("chat-1")).isTrue();
        assertThat(runner.holdsSession(" ")).isFalse();
        assertThat(OrchestratorDispatch.runnerCode(AgentIntent.CONSULTATION)).isEqualTo("CONSULTATION");
    }
}
