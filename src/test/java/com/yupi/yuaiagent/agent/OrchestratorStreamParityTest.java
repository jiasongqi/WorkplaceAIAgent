package com.yupi.yuaiagent.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorStreamParityTest {

    @Test
    void defaultRouteModeKeepsLegacySwitch() {
        assertThat(OrchestratorDispatch.modeFor(AgentIntent.RESUME, null, OrchestratorDispatch.RouteMode.OFF))
                .isEqualTo(OrchestratorDispatch.RouteMode.OFF);
        assertThat(OrchestratorDispatch.modeFor(AgentIntent.CONSULTATION, java.util.Map.of("CONSULTATION", "primary"),
                OrchestratorDispatch.RouteMode.OFF))
                .isEqualTo(OrchestratorDispatch.RouteMode.PRIMARY);
    }
}
