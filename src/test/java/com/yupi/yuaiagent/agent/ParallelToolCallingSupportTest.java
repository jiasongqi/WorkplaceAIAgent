package com.yupi.yuaiagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParallelToolCallingSupportTest {

    @Test
    void deniedToolIsNotExecuted() {
        ToolCallback terminal = callback("executeTerminalCommand");
        ChatResponse chatResponse = chatResponseWithTool("id-1", "executeTerminalCommand");

        ToolExecutionResult result = ParallelToolCallingSupport.execute(
                new Prompt(List.of()),
                chatResponse,
                new ToolCallback[]{terminal},
                Runnable::run,
                5,
                name -> false);

        verify(terminal, never()).call(anyString());
        Message last = result.conversationHistory().get(result.conversationHistory().size() - 1);
        assertTrue(last instanceof ToolResponseMessage);
        String data = ((ToolResponseMessage) last).getResponses().get(0).responseData();
        assertTrue(data.contains("permission denied"));
    }

    @Test
    void allowedToolIsExecuted() {
        ToolCallback search = callback("searchWeb");
        when(search.call(anyString())).thenReturn("ok");
        ChatResponse chatResponse = chatResponseWithTool("id-2", "searchWeb");

        ToolExecutionResult result = ParallelToolCallingSupport.execute(
                new Prompt(List.of()),
                chatResponse,
                new ToolCallback[]{search},
                Runnable::run,
                5,
                name -> true);

        verify(search).call(anyString());
        ToolResponseMessage trm = (ToolResponseMessage) result.conversationHistory()
                .get(result.conversationHistory().size() - 1);
        assertTrue(trm.getResponses().get(0).responseData().contains("ok"));
    }

    private static ChatResponse chatResponseWithTool(String id, String toolName) {
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall(id, "function", toolName, "{}");
        AssistantMessage assistant = new AssistantMessage("call", Map.of(), List.of(call));
        return new ChatResponse(List.of(new Generation(assistant)));
    }

    private static ToolCallback callback(String name) {
        ToolCallback cb = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn(name);
        when(cb.getToolDefinition()).thenReturn(def);
        return cb;
    }
}
