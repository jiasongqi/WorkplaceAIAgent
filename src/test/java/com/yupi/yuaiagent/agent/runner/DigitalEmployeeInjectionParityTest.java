package com.yupi.yuaiagent.agent.runner;

import com.yupi.yuaiagent.agent.OrchestratorAgent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DigitalEmployeeInjectionParityTest {

    @Test
    void digitalEmployeeNoteIsStable() {
        assertThat(OrchestratorAgent.DIGITAL_EMPLOYEE_NOTE).contains("【数字员工助手】");
    }
}
