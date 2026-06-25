# Bugfix Requirements Document

## Introduction

本文档记录了一批 P0 级安全与稳定性缺陷的修复需求。这些问题直接影响系统安全性（未授权访问）、数据准确性（计数翻倍）、服务可用性（无熔断降级）和成本可控性（无 Token 预算约束）。如果不修复，攻击者可以无鉴权操作文档和 AI 端点，生产环境中 DashScope/MCP 超时将导致服务雪崩，且单次对话成本完全不可控。

涉及的主要组件：
- `DocumentController`（文档管理接口）
- `AiController`（AI 对话接口）
- `OrchestratorAppService`（编排应用服务）
- `OrchestratorAgent` → 子 Agent 调用链路
- `TokenBudgetManager`（已实现但未集成到 Orchestrator 层）

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN 未认证用户请求 `/document/upload`、`/document/add`、`/document/list`、`/document/{docId}` 端点 THEN 系统允许请求通过并执行操作，任何人可上传、列举、删除文档

1.2 WHEN 未认证用户请求 `/ai/ai_chat/chat/sync`、`/ai/ai_chat/chat/sse`、`/ai/ai_chat/chat/server_sent_event`、`/ai/ai_chat/chat/sse_emitter` 端点 THEN 系统允许请求通过，无鉴权校验即可进行 AI 对话

1.3 WHEN 未认证用户请求 `/ai/ai_chat/rag/sync`、`/ai/ai_chat/tools/sync`、`/ai/ai_chat/mcp/sync`、`/ai/ai_chat/report/sync`、`/ai/manus/chat` 端点 THEN 系统允许请求通过，无鉴权校验即可使用 RAG/工具/MCP/报告/Manus 功能

1.4 WHEN `OrchestratorAppService.chatStream()` 被调用 THEN 系统执行两次 `usageTracker.track(userId, UsageEventType.CHAT, null, 0)`，每次对话用量计数被重复记录（翻倍）

1.5 WHEN OrchestratorAgent 调用子 Agent（经由 DashScope ChatModel 或 MCP Server）且外部服务超时或不可用 THEN 系统直接抛出未捕获的 Exception，SSE 连接中断，无任何熔断或降级响应

1.6 WHEN 单次对话涉及多轮 Agent 调用（如多 Agent 串行执行、Tool 调用循环） THEN 系统无整体 Token 预算约束，`TokenBudgetManager` 已实现但未集成到 `OrchestratorAppService`/`OrchestratorAgent` 层，单次对话总 Token 消耗不可控

### Expected Behavior (Correct)

2.1 WHEN 未认证用户请求 `/document/upload`、`/document/add`、`/document/list`、`/document/{docId}` 端点 THEN 系统 SHALL 返回 401 未授权错误，拒绝执行操作

2.2 WHEN 未认证用户请求 `/ai/ai_chat/chat/sync`、`/ai/ai_chat/chat/sse`、`/ai/ai_chat/chat/server_sent_event`、`/ai/ai_chat/chat/sse_emitter` 端点 THEN 系统 SHALL 返回 401 未授权错误，拒绝执行 AI 对话

2.3 WHEN 未认证用户请求 `/ai/ai_chat/rag/sync`、`/ai/ai_chat/tools/sync`、`/ai/ai_chat/mcp/sync`、`/ai/ai_chat/report/sync`、`/ai/manus/chat` 端点 THEN 系统 SHALL 返回 401 未授权错误，拒绝执行相关功能

2.4 WHEN `OrchestratorAppService.chatStream()` 被调用 THEN 系统 SHALL 仅执行一次 `usageTracker.track()`，准确记录每次对话的用量

2.5 WHEN OrchestratorAgent 调用子 Agent 且外部服务（DashScope/MCP Server）超时或不可用 THEN 系统 SHALL 捕获异常并返回降级响应（如"服务暂时不可用，请稍后重试"），SSE 连接正常关闭而不是异常中断

2.6 WHEN 单次对话的累计 Token 消耗达到预算上限 THEN 系统 SHALL 通过 `TokenBudgetManager` 在 Orchestrator 层进行预算检查与控制，阻止进一步的 LLM 调用并返回友好提示（如"本次对话 Token 配额已用尽"）

### Unchanged Behavior (Regression Prevention)

3.1 WHEN 已认证用户携带有效 Token 请求 `/document/*` 端点 THEN 系统 SHALL CONTINUE TO 正常执行文档上传、列举、删除操作

3.2 WHEN 已认证用户携带有效 Token 请求 `/ai/*` 下任何端点 THEN 系统 SHALL CONTINUE TO 正常执行 AI 对话、RAG、工具调用、MCP、报告生成等功能

3.3 WHEN `/session/login` 端点被请求 THEN 系统 SHALL CONTINUE TO 无需鉴权即可完成登录并返回 Token

3.4 WHEN `/ai/orchestrator/chat`（已有鉴权）被已认证用户请求 THEN 系统 SHALL CONTINUE TO 正常执行智能路由与流式对话

3.5 WHEN OrchestratorAgent 调用子 Agent 且外部服务正常响应 THEN 系统 SHALL CONTINUE TO 返回正常的 AI 对话结果，不受熔断/降级逻辑影响

3.6 WHEN 单次对话的 Token 消耗未达到预算上限 THEN 系统 SHALL CONTINUE TO 正常执行所有 Agent 调用，不对正常对话产生任何限制或中断

3.7 WHEN `TokenBudgetManager` 在 `ReActAgent` 层对单条 Observation 进行截断/压缩 THEN 系统 SHALL CONTINUE TO 保持现有的 Normal/Compact/Compress 三档策略不变
