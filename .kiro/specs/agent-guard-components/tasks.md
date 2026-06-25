# Implementation Plan: Agent Guard Components

## Overview

Implement three lightweight guard components (ToolResultClassifier, EmbeddingLoopDetector, TokenBudgetManager) as Spring `@Component` beans that integrate non-invasively into the existing Agent execution loop. Each component injects guidance messages into the Agent MessageList when anomalies are detected, without interrupting execution flow. Total ~270 lines of production code, no new dependencies.

## Tasks

- [x] 1. Create ToolResultClassifier component
  - [x] 1.1 Create `src/main/java/com/yupi/yuaiagent/guard/ToolResultClassifier.java`
    - Define `ResultGrade` enum with TIMEOUT, EMPTY, GARBAGE, NORMAL values
    - Implement `classify(String result, boolean isTimeout)` pure classification logic
    - Implement `classifyAndGuide(String result, boolean isTimeout, List<Message> messageList)` with guidance injection
    - Implement `isStackTrace()` helper checking for 2+ stack trace markers
    - Annotate with `@Component` and `@Slf4j`
    - Wrap guidance injection in try-catch, log at WARN level on failure
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 4.4, 4.5_

  - [ ]* 1.2 Write property tests for ToolResultClassifier
    - **Property 1: Classification Partitioning** — For any tool result and timeout flag, classify() returns exactly one ResultGrade; four conditions are mutually exclusive and exhaustive
    - **Property 2: Guidance Injection for Non-NORMAL Grades** — For TIMEOUT/EMPTY/GARBAGE results, classifyAndGuide() appends exactly one UserMessage to the list
    - **Property 3: No Guidance for NORMAL Grade** — For NORMAL results, classifyAndGuide() does not modify the message list
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 4.4**
    - Create `src/test/java/com/yupi/yuaiagent/guard/ToolResultClassifierPropertyTest.java` using jqwik
    - Use `@ForAll` String generators with null, blank, short, stack-trace-like, and normal inputs
    - Minimum 100 iterations per property

- [x] 2. Create EmbeddingLoopDetector component
  - [x] 2.1 Create `src/main/java/com/yupi/yuaiagent/guard/EmbeddingLoopDetector.java`
    - Define constants: WINDOW_SIZE=10, SIMILARITY_THRESHOLD=0.95, CONSECUTIVE_THRESHOLD=2
    - Implement per-session `ConcurrentHashMap<String, Deque<float[]>>` sliding window storage
    - Implement per-session `ConcurrentHashMap<String, Integer>` consecutive loop counter
    - Implement `checkLoop(String sessionId, String toolName, String arguments, List<Message> messageList)`
    - Implement `clearSession(String sessionId)` to remove session state
    - Implement package-private `static double cosineSimilarity(float[] a, float[] b)`
    - Wrap all logic in try-catch, log at WARN on failure, skip detection on error
    - Annotate with `@Component` and `@Slf4j`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 4.4, 4.5_

  - [ ]* 2.2 Write property tests for EmbeddingLoopDetector (cosine similarity)
    - **Property 4: Cosine Similarity Loop Threshold** — For any two vectors A and B, similarity match occurs iff cosineSimilarity(A,B) > 0.95
    - **Validates: Requirements 2.3**
    - Create `src/test/java/com/yupi/yuaiagent/guard/EmbeddingLoopDetectorPropertyTest.java` using jqwik
    - Generate random float[] arrays with `@ForAll` and verify threshold boundary behavior

  - [ ]* 2.3 Write property tests for EmbeddingLoopDetector (window and counter)
    - **Property 5: Consecutive Loop Count Threshold** — Guidance injected only when consecutive matches >= 2
    - **Property 6: Sliding Window Size Invariant** — Window contains at most 10 entries after any number of calls
    - **Validates: Requirements 2.4, 2.5**
    - Add tests to `EmbeddingLoopDetectorPropertyTest.java` using mock EmbeddingModel
    - Generate sequences of tool calls and verify window size cap and counter behavior

- [x] 3. Create TokenBudgetManager component
  - [x] 3.1 Create `src/main/java/com/yupi/yuaiagent/budget/TokenBudgetManager.java`
    - Define constants: WARN_THRESHOLD=0.65, COMPRESS_THRESHOLD=0.85, PRESERVE_RECENT=3
    - Implement constructor with per-role budget parameters (systemBudget, userBudget, assistantBudget)
    - Implement default no-arg constructor with defaults (2000, 4000, 4000)
    - Implement `checkBudget(List<Message> messageList)` grouping messages by role
    - Implement `checkRole()` with 65% warning injection and 85% compression trigger
    - Implement `compress()` replacing older messages with summary, preserving last 3
    - Implement `estimateTokens()` and `groupByRole()` helpers
    - Wrap all logic in try-catch, log at WARN on failure
    - Annotate with `@Component` and `@Slf4j`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 4.4, 4.5_

  - [ ]* 3.2 Write property tests for TokenBudgetManager
    - **Property 7: Token Budget 65% Warning Threshold** — When role tokens are [65%, 85%) of budget, exactly one guidance message is injected
    - **Property 8: Token Budget 85% Compression Threshold** — When role tokens >= 85% and role has > 3 messages, compression removes older messages
    - **Property 9: Compression Preserves Recent Messages** — After compression, the most recent 3 messages of the compressed role remain
    - **Validates: Requirements 3.3, 3.4, 3.5**
    - Create `src/test/java/com/yupi/yuaiagent/budget/TokenBudgetManagerPropertyTest.java` using jqwik
    - Generate random message lists with controlled token counts relative to budgets

- [x] 4. Checkpoint - Verify all components compile and tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Integrate guard components into Agent execution flow
  - [x] 5.1 Add guard configuration to `application.yml`
    - Add `agent.guard` section with token budget per-role settings
    - Add configuration for embedding loop detector thresholds (optional override)
    - Use environment variable placeholders with sensible defaults
    - _Requirements: 3.6, 4.6_

  - [x] 5.2 Modify `ToolCallAgent.java` to integrate ToolResultClassifier and EmbeddingLoopDetector
    - Add `@Autowired(required = false)` fields for ToolResultClassifier and EmbeddingLoopDetector
    - In `act()`, before tool execution: call `embeddingLoopDetector.checkLoop()` with session ID, tool name, and arguments extracted from `toolCallChatResponse`
    - In `act()`, after tool execution: detect if result came from TimeoutException and call `toolResultClassifier.classifyAndGuide()` with result text and timeout flag
    - Ensure existing timeout protection and trace logic remain unchanged
    - _Requirements: 4.1, 4.2, 4.4, 4.5, 4.6, 4.8_

  - [x] 5.3 Integrate TokenBudgetManager into BaseAgent execution loop
    - Add `@Autowired(required = false)` field for TokenBudgetManager in BaseAgent or ToolCallAgent
    - Invoke `tokenBudgetManager.checkBudget(messageList)` before each `think()` call in the step loop
    - Ensure no modification to the existing class hierarchy
    - _Requirements: 4.3, 4.4, 4.5, 4.8_

- [x] 6. Final checkpoint - Full integration verification
  - Ensure all tests pass, ask the user if questions arise.
  - Verify all three guard components load as Spring beans in the application context
  - Confirm combined production code is approximately 270 lines

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- All components use `@Autowired(required = false)` for non-invasive integration — if a bean is unavailable the guard is simply skipped
- The project already has jqwik on the classpath (`.jqwik-database` exists in project root)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "3.1"] },
    { "id": 1, "tasks": ["1.2", "2.2", "2.3", "3.2"] },
    { "id": 2, "tasks": ["5.1"] },
    { "id": 3, "tasks": ["5.2", "5.3"] }
  ]
}
```
