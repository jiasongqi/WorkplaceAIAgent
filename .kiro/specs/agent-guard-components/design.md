# Design Document: Agent Guard Components

## Overview

本设计为 agent_product 引入三个轻量级防护组件：**ToolResultClassifier**、**EmbeddingLoopDetector** 和 **TokenBudgetManager**。三者均为 Spring-managed `@Component` Bean，以非侵入方式嵌入 `ToolCallAgent.act()` 和 `BaseAgent` think 循环，通过注入 `UserMessage` 类型的引导消息（Guidance Message）来调整 Agent 行为，不硬性阻断执行流程。

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        BaseAgent.run()                        │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ for (step : maxSteps)                                   │ │
│  │   ┌──────────────────────┐                              │ │
│  │   │ TokenBudgetManager   │◀── invoked before think()    │ │
│  │   │  .checkBudget(msgs)  │                              │ │
│  │   └──────────────────────┘                              │ │
│  │            │                                            │ │
│  │   ┌──────────────────────┐                              │ │
│  │   │ ReActAgent.think()   │                              │ │
│  │   └──────────────────────┘                              │ │
│  │            │                                            │ │
│  │   ┌──────────────────────┐                              │ │
│  │   │ EmbeddingLoopDetector│◀── invoked before tool exec  │ │
│  │   │  .checkLoop(call)    │                              │ │
│  │   └──────────────────────┘                              │ │
│  │            │                                            │ │
│  │   ┌──────────────────────┐                              │ │
│  │   │ ToolCallAgent.act()  │                              │ │
│  │   │  (tool execution)    │                              │ │
│  │   └──────────────────────┘                              │ │
│  │            │                                            │ │
│  │   ┌──────────────────────┐                              │ │
│  │   │ ToolResultClassifier │◀── invoked after tool exec   │ │
│  │   │  .classify(result)   │                              │ │
│  │   └──────────────────────┘                              │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. ToolResultClassifier (`com.yupi.yuaiagent.guard`)

**Responsibility:** 对工具执行返回值进行四级分类，根据分类结果决定是否向 MessageList 注入引导消息。

```java
package com.yupi.yuaiagent.guard;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Component
public class ToolResultClassifier {

    public enum ResultGrade { TIMEOUT, EMPTY, GARBAGE, NORMAL }

    // Stack trace indicators
    private static final String[] STACK_TRACE_MARKERS = {
        "at ", "Exception", "Caused by:", ".java:", "Traceback"
    };

    /**
     * Classify a tool result and optionally inject guidance into messageList.
     *
     * @param result      the raw tool result (null allowed)
     * @param isTimeout   whether the result was produced by a TimeoutException
     * @param messageList the agent's message list for guidance injection
     * @return the classification grade
     */
    public ResultGrade classifyAndGuide(String result, boolean isTimeout, List<Message> messageList) {
        ResultGrade grade = classify(result, isTimeout);
        try {
            injectGuidance(grade, messageList);
        } catch (Exception e) {
            log.warn("[ToolResultClassifier] guidance injection failed: {}", e.getMessage());
        }
        return grade;
    }

    /**
     * Pure classification logic (no side effects).
     */
    public ResultGrade classify(String result, boolean isTimeout) {
        if (isTimeout) {
            return ResultGrade.TIMEOUT;
        }
        if (result == null || result.isBlank()) {
            return ResultGrade.EMPTY;
        }
        String trimmed = result.strip();
        if (trimmed.length() < 5 || isStackTrace(trimmed)) {
            return ResultGrade.GARBAGE;
        }
        return ResultGrade.NORMAL;
    }

    private boolean isStackTrace(String content) {
        int matchCount = 0;
        for (String marker : STACK_TRACE_MARKERS) {
            if (content.contains(marker)) {
                matchCount++;
            }
        }
        // At least 2 stack-trace markers present → likely a pure stack trace
        return matchCount >= 2;
    }

    private void injectGuidance(ResultGrade grade, List<Message> messageList) {
        switch (grade) {
            case TIMEOUT, EMPTY -> messageList.add(new UserMessage(
                "[Guard] 工具返回结果为空或超时，请尝试更换参数重试，或使用其他工具完成任务。"));
            case GARBAGE -> messageList.add(new UserMessage(
                "[Guard] 工具返回了低质量结果（过短或错误堆栈），建议切换到其他工具或策略。"));
            case NORMAL -> { /* no action */ }
        }
    }
}
```

**Lines:** ~55

---

### 2. EmbeddingLoopDetector (`com.yupi.yuaiagent.guard`)

**Responsibility:** 利用 DashScope EmbeddingModel 对工具调用签名（name + arguments）计算向量，通过余弦相似度检测重复调用模式。

```java
package com.yupi.yuaiagent.guard;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EmbeddingLoopDetector {

    private static final int WINDOW_SIZE = 10;
    private static final double SIMILARITY_THRESHOLD = 0.95;
    private static final int CONSECUTIVE_THRESHOLD = 2;

    private final EmbeddingModel embeddingModel;

    // Per-session state: sessionId -> sliding window of embeddings
    private final Map<String, Deque<float[]>> sessionWindows = new ConcurrentHashMap<>();
    // Per-session consecutive loop counter
    private final Map<String, Integer> consecutiveLoopCounts = new ConcurrentHashMap<>();

    public EmbeddingLoopDetector(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Check if the current tool call forms a loop pattern.
     * Inject guidance if consecutive loop threshold is met.
     *
     * @param sessionId   unique session identifier
     * @param toolName    name of the tool being called
     * @param arguments   tool arguments as a string
     * @param messageList the agent's message list for guidance injection
     */
    public void checkLoop(String sessionId, String toolName, String arguments, List<Message> messageList) {
        try {
            String signature = toolName + ":" + arguments;
            float[] embedding = computeEmbedding(signature);
            if (embedding == null) return;

            Deque<float[]> window = sessionWindows.computeIfAbsent(sessionId, k -> new ArrayDeque<>());

            boolean loopDetected = window.stream()
                    .anyMatch(prev -> cosineSimilarity(prev, embedding) > SIMILARITY_THRESHOLD);

            if (loopDetected) {
                int count = consecutiveLoopCounts.merge(sessionId, 1, Integer::sum);
                if (count >= CONSECUTIVE_THRESHOLD) {
                    messageList.add(new UserMessage(
                        "[Guard] 检测到重复工具调用模式，请尝试完全不同的方法来解决问题。"));
                    log.info("[LoopDetector] loop guidance injected for session {}", sessionId);
                }
            } else {
                consecutiveLoopCounts.put(sessionId, 0);
            }

            // Maintain sliding window
            if (window.size() >= WINDOW_SIZE) {
                window.pollFirst();
            }
            window.addLast(embedding);
        } catch (Exception e) {
            log.warn("[EmbeddingLoopDetector] error during loop check, skipping: {}", e.getMessage());
        }
    }

    /**
     * Clear session state when execution ends.
     */
    public void clearSession(String sessionId) {
        sessionWindows.remove(sessionId);
        consecutiveLoopCounts.remove(sessionId);
    }

    /**
     * Cosine similarity between two vectors. Package-private for testing.
     */
    static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    private float[] computeEmbedding(String text) {
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("[EmbeddingLoopDetector] embedding computation failed: {}", e.getMessage());
            return null;
        }
    }
}
```

**Lines:** ~95

---

### 3. TokenBudgetManager (`com.yupi.yuaiagent.budget`)

**Responsibility:** 按 SYSTEM / USER / ASSISTANT 三种消息角色分级跟踪 Token 消耗，在 65% 阈值注入精简引导，在 85% 阈值触发摘要压缩。

```java
package com.yupi.yuaiagent.budget;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TokenBudgetManager {

    private static final double WARN_THRESHOLD = 0.65;
    private static final double COMPRESS_THRESHOLD = 0.85;
    private static final int PRESERVE_RECENT = 3;

    // Approximate token count: 1 token ≈ 1.5 Chinese chars or 4 English chars
    private static final double CHARS_PER_TOKEN = 2.5;

    private final long systemBudget;
    private final long userBudget;
    private final long assistantBudget;

    public TokenBudgetManager() {
        // Default budgets derived from TokenBudget.maxPromptTokens split across roles
        this(2000, 4000, 4000);
    }

    public TokenBudgetManager(long systemBudget, long userBudget, long assistantBudget) {
        this.systemBudget = systemBudget;
        this.userBudget = userBudget;
        this.assistantBudget = assistantBudget;
    }

    /**
     * Check token budgets per role and inject guidance or trigger compression.
     *
     * @param messageList the agent's message list (mutable)
     */
    public void checkBudget(List<Message> messageList) {
        try {
            Map<String, List<Message>> byRole = groupByRole(messageList);

            checkRole("SYSTEM", byRole.getOrDefault("SYSTEM", List.of()), systemBudget, messageList);
            checkRole("USER", byRole.getOrDefault("USER", List.of()), userBudget, messageList);
            checkRole("ASSISTANT", byRole.getOrDefault("ASSISTANT", List.of()), assistantBudget, messageList);
        } catch (Exception e) {
            log.warn("[TokenBudgetManager] budget check failed, skipping: {}", e.getMessage());
        }
    }

    private void checkRole(String role, List<Message> roleMessages, long budget, List<Message> messageList) {
        long tokenCount = estimateTokens(roleMessages);
        double ratio = (double) tokenCount / budget;

        if (ratio >= COMPRESS_THRESHOLD) {
            compress(role, roleMessages, tokenCount, messageList);
        } else if (ratio >= WARN_THRESHOLD) {
            messageList.add(new UserMessage(
                "[Guard] " + role + " 消息已使用 " + Math.round(ratio * 100) + "% Token 预算，请更加精简。"));
        }
    }

    /**
     * Replace older messages of the target role with a summary, preserving the most recent N.
     */
    private void compress(String role, List<Message> roleMessages, long originalTokens, List<Message> messageList) {
        if (roleMessages.size() <= PRESERVE_RECENT) return;

        // Identify messages to compress (all except last PRESERVE_RECENT of that role)
        List<Message> toCompress = roleMessages.subList(0, roleMessages.size() - PRESERVE_RECENT);
        String summary = buildSummary(toCompress);

        // Remove compressed messages from the main list
        messageList.removeAll(toCompress);
        // Insert summary as a SystemMessage at the beginning
        messageList.add(0, new SystemMessage("[摘要-" + role + "] " + summary));

        long compressedTokens = estimateTokens(List.of(new SystemMessage(summary)));
        log.info("[TokenBudgetManager] compressed {} role: {} tokens → {} tokens",
                role, originalTokens, compressedTokens);
    }

    private String buildSummary(List<Message> messages) {
        return messages.stream()
                .map(Message::getText)
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.length() > 100 ? t.substring(0, 100) + "..." : t)
                .collect(Collectors.joining(" | ", "对话摘要: ", ""));
    }

    long estimateTokens(List<Message> messages) {
        return messages.stream()
                .map(Message::getText)
                .filter(t -> t != null)
                .mapToLong(t -> Math.round(t.length() / CHARS_PER_TOKEN))
                .sum();
    }

    private Map<String, List<Message>> groupByRole(List<Message> messages) {
        return messages.stream().collect(Collectors.groupingBy(m -> {
            if (m instanceof SystemMessage) return "SYSTEM";
            if (m instanceof UserMessage) return "USER";
            if (m instanceof AssistantMessage) return "ASSISTANT";
            return "OTHER";
        }, Collectors.toCollection(ArrayList::new)));
    }
}
```

**Lines:** ~105

---

## Integration Points

### ToolCallAgent.act() Modifications

```java
// In ToolCallAgent.act(), after tool execution and before returning results:

// 1. Before tool execution — loop detection
if (embeddingLoopDetector != null) {
    String toolNames = /* extract from toolCallChatResponse */;
    String toolArgs = /* extract from toolCallChatResponse */;
    embeddingLoopDetector.checkLoop(sessionId, toolNames, toolArgs, getMessageList());
}

// 2. After tool execution — result classification
if (toolResultClassifier != null) {
    boolean isTimeout = /* from CompletionException catch */;
    toolResultClassifier.classifyAndGuide(results, isTimeout, getMessageList());
}
```

### BaseAgent execution loop modification

```java
// In BaseAgent.run() / runStream(), before calling step():
if (tokenBudgetManager != null) {
    tokenBudgetManager.checkBudget(messageList);
}
```

## Data Models

### ResultGrade Enum

| Value   | Condition                                                     |
|---------|---------------------------------------------------------------|
| TIMEOUT | Tool execution ended due to TimeoutException                  |
| EMPTY   | Result is null or contains only whitespace                    |
| GARBAGE | Result is non-empty but < 5 chars, or solely stack traces    |
| NORMAL  | None of the above conditions match                            |

### Session Embedding Window

```
sessionId → Deque<float[]> (max size 10)
sessionId → int consecutiveLoopCount
```

### Token Budget Configuration

| Role      | Default Budget | 65% Threshold | 85% Threshold |
|-----------|---------------|---------------|---------------|
| SYSTEM    | 2000 tokens   | 1300 tokens   | 1700 tokens   |
| USER      | 4000 tokens   | 2600 tokens   | 3400 tokens   |
| ASSISTANT | 4000 tokens   | 2600 tokens   | 3400 tokens   |

## Error Handling

All three components follow the same error handling pattern:

1. Wrap all logic in try-catch at the top level
2. Log errors at WARN level with `[ComponentName]` prefix
3. Never throw exceptions to the caller
4. Skip the guard action on error — allow Agent execution to continue uninterrupted

```java
try {
    // guard logic
} catch (Exception e) {
    log.warn("[ComponentName] operation failed, skipping: {}", e.getMessage());
}
```

## Dependencies

No new dependencies. Uses only:
- `org.springframework.ai.embedding.EmbeddingModel` (already provided by `alibaba-dashscope` starter)
- `org.springframework.ai.chat.messages.*` (Spring AI core)
- `org.springframework.stereotype.Component` (Spring Framework)
- Standard Java collections and math

## Testing Strategy

### Unit Tests (Example-Based)

- **ToolResultClassifier**: Specific examples for each grade (null → EMPTY, "ab" → GARBAGE, valid 10-char string → NORMAL)
- **EmbeddingLoopDetector**: Session clear behavior, error handling when EmbeddingModel throws
- **TokenBudgetManager**: Configuration via constructor, log output verification during compression, encoding error handling

### Property-Based Tests (jqwik)

All property-based tests use the project's existing **jqwik** dependency (as indicated by `.jqwik-database` in the project root). Minimum 100 iterations per property.

- **Property 1–3**: Generate random strings (null, whitespace, short, stack-trace-like, normal) and verify classification partitioning and guidance injection behavior
- **Property 4**: Generate random float vectors and verify cosine similarity threshold behavior
- **Property 5–6**: Generate sequences of embedding vectors and verify consecutive count and window size invariants
- **Property 7–9**: Generate random message lists with varying token counts relative to budgets and verify threshold behaviors and compression invariants

### Integration Tests

- Verify ToolResultClassifier is invoked in `ToolCallAgent.act()` after tool execution
- Verify EmbeddingLoopDetector is invoked before tool execution in `act()`
- Verify TokenBudgetManager is invoked before `think()` in the execution loop
- Verify guard components load as Spring beans in the application context

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Classification Partitioning

*For any* tool result (including null, whitespace-only, short strings, stack traces, and normal strings) and timeout flag combination, the `classify()` method SHALL return exactly one of the four `ResultGrade` values, and the four classification conditions are mutually exclusive and exhaustive.

**Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**

### Property 2: Guidance Injection for Non-NORMAL Grades

*For any* tool result classified as TIMEOUT, EMPTY, or GARBAGE, calling `classifyAndGuide()` SHALL append exactly one `UserMessage` to the provided message list, and the message list size SHALL increase by exactly one.

**Validates: Requirements 1.6, 1.7, 4.4**

### Property 3: No Guidance for NORMAL Grade

*For any* tool result classified as NORMAL, calling `classifyAndGuide()` SHALL NOT modify the provided message list — the list size and contents SHALL remain unchanged.

**Validates: Requirements 1.8**

### Property 4: Cosine Similarity Loop Threshold

*For any* two embedding vectors A and B, the `EmbeddingLoopDetector` SHALL identify a similarity match if and only if `cosineSimilarity(A, B) > 0.95`.

**Validates: Requirements 2.3**

### Property 5: Consecutive Loop Count Threshold

*For any* sequence of tool call embeddings in a session, a guidance message SHALL be injected into the message list only when the number of consecutive similarity matches (cosine > 0.95) reaches or exceeds 2.

**Validates: Requirements 2.4**

### Property 6: Sliding Window Size Invariant

*For any* number of tool calls N submitted to a session, the embedding window SHALL contain at most 10 entries — specifically `min(N, 10)` entries representing the most recent tool calls.

**Validates: Requirements 2.5**

### Property 7: Token Budget 65% Warning Threshold

*For any* message list where a single role's estimated token count is ≥ 65% but < 85% of that role's configured budget, calling `checkBudget()` SHALL inject exactly one guidance message advising concise responses.

**Validates: Requirements 3.3**

### Property 8: Token Budget 85% Compression Threshold

*For any* message list where a single role's estimated token count is ≥ 85% of that role's configured budget and that role has more than 3 messages, calling `checkBudget()` SHALL trigger compression that removes older messages of that role.

**Validates: Requirements 3.4**

### Property 9: Compression Preserves Recent Messages

*For any* message list of a role with N > 3 messages that triggers compression, the most recent 3 messages of that role SHALL remain in the message list after compression.

**Validates: Requirements 3.5**
