# Requirements Document

## Introduction

为 agent_product 项目新增三个防护组件（核心 20% 改造方案），实现工具调用结果分级处理、基于 Embedding 余弦相似度的循环检测，以及按消息角色分级的 Token 预算管理。三个组件以非阻塞方式工作，当检测到异常时向 Agent messageList 注入引导消息，不硬性中断执行流程。新增代码约 270 行，不引入新依赖，不改变现有架构。

## Glossary

- **ToolResultClassifier**: 工具结果分级处理器，位于 `guard/` 包，对工具调用返回值进行四级分类（TIMEOUT / EMPTY / GARBAGE / NORMAL）并决定后续动作
- **EmbeddingLoopDetector**: 基于 Embedding 余弦相似度的循环检测器，位于 `guard/` 包，覆盖所有工具调用，检测重复调用模式
- **TokenBudgetManager**: Token 预算分级管理器，位于 `budget/` 包，从 ChatMemoryManager 解耦为独立类，按消息角色分级管理 Token 预算
- **ToolCallAgent**: 现有的工具调用代理类，具备 CompletableFuture + orTimeout 超时保护机制
- **ChatMemoryManager**: 现有的对话记忆管理器，支持 Token / 轮数压缩策略
- **DashScope_EmbeddingClient**: 阿里云 DashScope 提供的 Embedding 服务客户端，已在项目中可用
- **MessageList**: BaseAgent 维护的 `List<Message>` 对话上下文，用于 LLM 调用
- **Guidance_Message**: 注入 MessageList 的引导消息，为 UserMessage 类型，提示 Agent 调整策略
- **ResultGrade**: ToolResultClassifier 的分类结果枚举，包含 TIMEOUT、EMPTY、GARBAGE、NORMAL 四个值
- **Cosine_Similarity**: 两个向量之间夹角余弦值，范围 [0, 1]，用于衡量工具调用参数的相似度

## Requirements

### Requirement 1: ToolResultClassifier — 工具结果分级分类

**User Story:** As a 系统开发者, I want 工具调用结果被自动分级处理, so that Agent 能够根据结果质量决定重试或切换策略而不被低质量结果误导。

#### Acceptance Criteria

1.1 WHEN a tool execution completes, THE ToolResultClassifier SHALL classify the result into exactly one of four grades: TIMEOUT, EMPTY, GARBAGE, or NORMAL.

1.2 WHEN the tool execution result is produced by a TimeoutException, THE ToolResultClassifier SHALL classify the result as TIMEOUT.

1.3 WHEN the tool execution result is null or contains only whitespace characters, THE ToolResultClassifier SHALL classify the result as EMPTY.

1.4 WHEN the tool execution result is non-empty but its content length is less than 5 characters or consists solely of error stack traces, THE ToolResultClassifier SHALL classify the result as GARBAGE.

1.5 WHEN the tool execution result does not match TIMEOUT, EMPTY, or GARBAGE criteria, THE ToolResultClassifier SHALL classify the result as NORMAL.

1.6 WHEN the ToolResultClassifier classifies a result as TIMEOUT or EMPTY, THE ToolResultClassifier SHALL inject a Guidance_Message into the Agent MessageList suggesting a retry with alternative parameters.

1.7 WHEN the ToolResultClassifier classifies a result as GARBAGE, THE ToolResultClassifier SHALL inject a Guidance_Message into the Agent MessageList suggesting the Agent switch to a different tool or strategy.

1.8 WHEN the ToolResultClassifier classifies a result as NORMAL, THE ToolResultClassifier SHALL take no additional action and allow the Agent to proceed with the result.

1.9 THE ToolResultClassifier SHALL be located in the `com.yupi.yuaiagent.guard` package.

1.10 THE ToolResultClassifier SHALL be a Spring-managed component annotated with `@Component`.

### Requirement 2: EmbeddingLoopDetector — 基于 Embedding 的循环调用检测

**User Story:** As a 系统开发者, I want 重复的工具调用模式被自动检测, so that Agent 不会陷入无限循环浪费 Token 和时间。

#### Acceptance Criteria

2.1 THE EmbeddingLoopDetector SHALL monitor all tool calls made by the Agent, regardless of tool type.

2.2 WHEN a tool call is made, THE EmbeddingLoopDetector SHALL compute an embedding vector of the tool call signature (tool name combined with arguments) using the DashScope_EmbeddingClient.

2.3 WHEN the cosine similarity between the current tool call embedding and any of the recent tool call embeddings exceeds 0.95, THE EmbeddingLoopDetector SHALL identify a loop condition.

2.4 WHEN the EmbeddingLoopDetector identifies a loop condition occurring at least 2 consecutive times, THE EmbeddingLoopDetector SHALL inject a Guidance_Message into the Agent MessageList advising the Agent to try a fundamentally different approach.

2.5 THE EmbeddingLoopDetector SHALL maintain a sliding window of the most recent 10 tool call embeddings per Agent execution session.

2.6 WHEN an Agent execution session ends, THE EmbeddingLoopDetector SHALL clear the embedding history for that session.

2.7 IF the DashScope_EmbeddingClient is unavailable or returns an error, THEN THE EmbeddingLoopDetector SHALL log the error and skip loop detection for that tool call without interrupting the Agent execution.

2.8 THE EmbeddingLoopDetector SHALL be located in the `com.yupi.yuaiagent.guard` package.

2.9 THE EmbeddingLoopDetector SHALL be a Spring-managed component annotated with `@Component`.

### Requirement 3: TokenBudgetManager — 按角色分级 Token 预算管理

**User Story:** As a 系统开发者, I want Token 消耗按消息角色分级管理并在阈值处自动干预, so that Agent 在有限 Token 预算内高效运行而不会意外耗尽配额。

#### Acceptance Criteria

3.1 THE TokenBudgetManager SHALL be a standalone class in the `com.yupi.yuaiagent.budget` package, independent of ChatMemoryManager.

3.2 THE TokenBudgetManager SHALL track Token usage separately for three message roles: SYSTEM, USER, and ASSISTANT.

3.3 WHEN the total Token usage of any single role reaches 65% of the configured budget for that role, THE TokenBudgetManager SHALL inject a Guidance_Message into the Agent MessageList advising the Agent to produce more concise responses.

3.4 WHEN the total Token usage of any single role reaches 85% of the configured budget for that role, THE TokenBudgetManager SHALL trigger summary compression on messages of that role to reclaim Token space.

3.5 WHEN summary compression is triggered, THE TokenBudgetManager SHALL replace older messages of the target role with a condensed summary while preserving the most recent 3 messages of that role.

3.6 THE TokenBudgetManager SHALL accept per-role budget configuration via constructor parameters or Spring configuration properties.

3.7 WHEN the TokenBudgetManager performs summary compression, THE TokenBudgetManager SHALL log the compression event including the role, original Token count, and compressed Token count.

3.8 THE TokenBudgetManager SHALL use the existing TokenBudget record for overall budget definition.

3.9 THE TokenBudgetManager SHALL be a Spring-managed component annotated with `@Component`.

3.10 IF Token counting fails due to an encoding error, THEN THE TokenBudgetManager SHALL log the error and skip the budget check for that invocation without interrupting Agent execution.

### Requirement 4: 集成与非侵入性约束

**User Story:** As a 系统开发者, I want 三个防护组件以非侵入方式集成到现有 Agent 执行流程中, so that 现有代码结构和行为保持稳定。

#### Acceptance Criteria

4.1 THE ToolResultClassifier SHALL integrate with ToolCallAgent by being invoked after tool execution completes in the `act()` method, without modifying the existing timeout protection logic.

4.2 THE EmbeddingLoopDetector SHALL integrate with ToolCallAgent by being invoked before each tool call in the `act()` method.

4.3 THE TokenBudgetManager SHALL integrate with BaseAgent by being invoked before each `think()` call in the execution loop.

4.4 WHEN any guard component detects an issue, THE guard component SHALL inject a Guidance_Message of type UserMessage into the Agent MessageList without throwing exceptions or altering the Agent state.

4.5 IF any guard component encounters an internal error, THEN THE guard component SHALL log the error at WARN level and allow the Agent execution to continue without interruption.

4.6 THE three guard components SHALL NOT introduce any new external dependencies beyond those already present in the project (Spring Boot 3.4.4, Spring AI 1.0.0, alibaba-dashscope).

4.7 THE three guard components SHALL have a combined implementation size of approximately 270 lines of code.

4.8 THE three guard components SHALL NOT modify the existing class hierarchy or architecture of the Agent framework.
