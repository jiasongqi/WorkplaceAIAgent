package com.yupi.yuaiagent.agent.loop;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoopCh4GuardsTest {

    @Test
    void depthLimitBlocksBeyondMax() {
        assertEquals(0, AgentDepthContext.current());
        String denied = AgentDepthContext.runWithDepth(1,
                () -> AgentDepthContext.runWithDepth(1,
                        () -> "inner",
                        () -> "DENIED"),
                () -> "outer-denied");
        assertEquals("DENIED", denied);
        assertEquals(0, AgentDepthContext.current());
    }

    @Test
    void wrapUpTemplateWithoutChatClient() {
        AgentLoopResult r = LoopWrapUp.wrapUp("写报告", List.of("Step 1: drafted"), 10, null);
        assertEquals(AgentLoopResult.Status.PARTIAL_SUCCESS, r.status());
        assertTrue(r.toUserFacingWrapUp().contains("收尾"));
        assertFalse(r.incompleteItems().isEmpty());
    }

    @Test
    void wrapUpWithTokenBudgetReason() {
        AgentLoopResult r = LoopWrapUp.wrapUp("写报告", List.of("Step 1: drafted"), 10, null,
                "单次运行 Token 上限 25000 已用尽");
        assertTrue(r.incompleteItems().get(0).contains("Token"));
    }

    @Test
    void completionClaimWithoutToolIsFlagged() {
        String warn = CompletionClaimGuard.checkUnsupportedClaim(
                List.of(new AssistantMessage("思考中")),
                "邮件已发送给客户");
        assertNotNull(warn);
        assertTrue(warn.contains("Tool Output"));
    }

    @Test
    void completionClaimWithSuccessToolOk() {
        var toolMsg = new ToolResponseMessage(List.of(
                new ToolResponseMessage.ToolResponse("1", "writeFile", "File written successfully to: a.txt")));
        String warn = CompletionClaimGuard.checkUnsupportedClaim(
                List.of(toolMsg),
                "文件已写入完成");
        assertNull(warn);
    }

    @Test
    void stepReflectAppendsOnGarbage() {
        var list = new java.util.ArrayList<org.springframework.ai.chat.messages.Message>();
        StepReflector.reflectIfNeeded(
                com.yupi.yuaiagent.guard.ToolResultClassifier.ResultGrade.GARBAGE,
                "请登录后查看",
                list);
        assertEquals(1, list.size());
        assertTrue(list.get(0).getText().contains("Step Reflect"));
    }
}
