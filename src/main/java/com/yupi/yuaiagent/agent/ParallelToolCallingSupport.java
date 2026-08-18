package com.yupi.yuaiagent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Parallel Fan-out for independent tool calls in one assistant turn (Ch3 Parallel Tool Use).
 */
@Slf4j
public final class ParallelToolCallingSupport {

    private ParallelToolCallingSupport() {
    }

    public static ToolExecutionResult execute(
            Prompt prompt,
            ChatResponse chatResponse,
            ToolCallback[] availableTools,
            Executor executor,
            long timeoutSeconds) {
        return execute(prompt, chatResponse, availableTools, executor, timeoutSeconds, name -> true);
    }

    public static ToolExecutionResult execute(
            Prompt prompt,
            ChatResponse chatResponse,
            ToolCallback[] availableTools,
            Executor executor,
            long timeoutSeconds,
            Predicate<String> allowTool) {

        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
        Map<String, ToolCallback> byName = indexByName(availableTools);
        Predicate<String> allow = allowTool == null ? name -> true : allowTool;

        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>(toolCalls.size());

        if (toolCalls.size() <= 1) {
            for (AssistantMessage.ToolCall tc : toolCalls) {
                responses.add(invokeOne(tc, byName, timeoutSeconds, allow));
            }
        } else {
            log.info("[ParallelTools] fan-out {} tool calls", toolCalls.size());
            @SuppressWarnings("unchecked")
            CompletableFuture<ToolResponseMessage.ToolResponse>[] futures =
                    new CompletableFuture[toolCalls.size()];
            for (int i = 0; i < toolCalls.size(); i++) {
                AssistantMessage.ToolCall tc = toolCalls.get(i);
                futures[i] = CompletableFuture.supplyAsync(
                        () -> invokeOne(tc, byName, timeoutSeconds, allow), executor);
            }
            for (int i = 0; i < futures.length; i++) {
                AssistantMessage.ToolCall tc = toolCalls.get(i);
                try {
                    responses.add(futures[i].get(timeoutSeconds + 2, TimeUnit.SECONDS));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String msg = cause instanceof TimeoutException
                            ? "Error: tool timed out after " + timeoutSeconds + "s"
                            : "Error: tool fan-out failed: " + cause.getMessage();
                    responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), msg));
                    log.warn("[ParallelTools] join failed for {}: {}", tc.name(), msg);
                }
            }
        }

        ToolResponseMessage toolResponseMessage = new ToolResponseMessage(responses);
        List<Message> history = new ArrayList<>(prompt.getInstructions());
        history.add(new AssistantMessage(
                assistantMessage.getText() == null ? "" : assistantMessage.getText(),
                assistantMessage.getMetadata() == null ? Map.of() : assistantMessage.getMetadata(),
                assistantMessage.getToolCalls()));
        history.add(toolResponseMessage);

        return ToolExecutionResult.builder()
                .conversationHistory(history)
                .returnDirect(false)
                .build();
    }

    private static ToolResponseMessage.ToolResponse invokeOne(
            AssistantMessage.ToolCall tc,
            Map<String, ToolCallback> byName,
            long timeoutSeconds,
            Predicate<String> allowTool) {
        String name = tc.name();
        String args = tc.arguments() == null ? "{}" : tc.arguments();
        if (allowTool != null && !allowTool.test(name)) {
            log.warn("[ParallelTools] permission denied for tool={}", name);
            return new ToolResponseMessage.ToolResponse(tc.id(), name,
                    "Error: permission denied for tool " + name
                            + ". This agent is not allowed to call it. Choose an allowed tool or continue without it.");
        }
        ToolCallback callback = byName.get(name);
        if (callback == null) {
            return new ToolResponseMessage.ToolResponse(tc.id(), name,
                    "Error: tool not registered: " + name + ". Do not invent tool names.");
        }
        try {
            // Run with interruptible timeout on calling thread's future
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> callback.call(args));
            String data = future.orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();
            return new ToolResponseMessage.ToolResponse(tc.id(), name, data == null ? "" : data);
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
                return new ToolResponseMessage.ToolResponse(tc.id(), name,
                        "Error: tool timed out after " + timeoutSeconds + "s");
            }
            return new ToolResponseMessage.ToolResponse(tc.id(), name,
                    "Error executing tool: " + cause.getMessage());
        } catch (Exception e) {
            return new ToolResponseMessage.ToolResponse(tc.id(), name,
                    "Error executing tool: " + e.getMessage());
        }
    }

    private static Map<String, ToolCallback> indexByName(ToolCallback[] availableTools) {
        Map<String, ToolCallback> map = new HashMap<>();
        if (availableTools == null) {
            return map;
        }
        for (ToolCallback cb : availableTools) {
            if (cb == null || cb.getToolDefinition() == null) {
                continue;
            }
            String n = cb.getToolDefinition().name();
            if (n != null) {
                map.put(n, cb);
            }
        }
        return map;
    }
}
