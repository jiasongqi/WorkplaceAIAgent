package com.yupi.yuaiagent.agent.goal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoalAnchorTest {

    @Test
    void prefersActiveGoalOverTurnMessage() {
        String block = GoalAnchor.buildBlock("完成简历优化", "随便聊聊", "RESUME");
        assertThat(block).contains(GoalAnchor.MARKER);
        assertThat(block).contains("完成简历优化");
        assertThat(block).contains("RESUME");
        assertThat(block).doesNotContain("随便聊聊");
    }

    @Test
    void fallsBackToTurnMessage() {
        assertThat(GoalAnchor.resolveGoal(null, "帮我改简历")).isEqualTo("帮我改简历");
    }

    @Test
    void stepReminderContainsMarker() {
        assertThat(GoalAnchor.stepReminder("修 bug")).contains(GoalAnchor.MARKER);
        assertThat(GoalAnchor.isGoalReminder(GoalAnchor.stepReminder("x"))).isTrue();
    }
}
