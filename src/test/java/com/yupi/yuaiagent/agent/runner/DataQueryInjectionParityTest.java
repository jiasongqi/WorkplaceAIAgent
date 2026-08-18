package com.yupi.yuaiagent.agent.runner;

import com.yupi.yuaiagent.agent.GeneralCareerAgent;
import com.yupi.yuaiagent.agent.OrchestratorAgent;
import com.yupi.yuaiagent.context.ConversationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataQueryInjectionParityTest {

    @Test
    void dataQueryAndDigitalEmployeeNotesReachCareerAgent() {
        AtomicReference<String> injection = new AtomicReference<>();
        GeneralCareerAgent agent = mock(GeneralCareerAgent.class);
        when(agent.chat(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            injection.set(inv.getArgument(2));
            return "ok";
        });

        new NoteInjectingCareerRunner("DATA_QUERY", OrchestratorAgent.DATA_QUERY_FALLBACK_NOTE, agent)
                .run(new ConversationContext("", "", List.of(), "c1", "base"), "查报表");
        assertThat(injection.get()).contains("base");
        assertThat(injection.get()).contains("【数据查询说明】");

        new NoteInjectingCareerRunner("DIGITAL_EMPLOYEE", OrchestratorAgent.DIGITAL_EMPLOYEE_NOTE, agent)
                .run(new ConversationContext("", "", List.of(), "c1", ""), "创建数字员工");
        assertThat(injection.get()).contains("【数字员工助手】");
    }
}
