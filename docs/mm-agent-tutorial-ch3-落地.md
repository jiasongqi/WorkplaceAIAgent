# 多模态 Agent 教程落地笔记（mm_agent_tutorial Ch3 → WorkPilot）

> 来源：[第 3 章 Tool Call：工具调用设计与编排](https://zsc.github.io/mm_agent_tutorial/chapter3.html)  
> 落地日期：2026-07-29  
> 原则：吸收 Schema 工程、并行 Fan-out、Observation 清洗、副作用幂等、传引用、Submit-Poll；不做 Browser/VLA 像素点击。

---

## 1. 对照结论

| 教程概念 | WorkPilot 原有能力 | 本次落地 |
|---------|-------------------|---------|
| Schema Engineering | `@Tool` 描述偏短 | **WHEN/DO NOT/RETURNS 契约化描述** |
| 工具边界（防 Tool Confusion） | search / scrape / RAG 边界弱 | **互斥边界写进 Schema + RagTool** |
| 串行 ReAct | `ToolCallAgent` | 已具备 |
| Parallel Fan-out | 整批串行 `executeToolCalls` | **`ParallelToolCallingSupport`** |
| Async Submit-Poll | 仅 30s 硬超时 | **`start*` + `checkAsyncToolTask`** |
| 错误当反馈 | `ToolResultClassifier` | 已具备 |
| Observation 清洗 | 硬截断 3000 | **`ObservationSanitizer`** |
| 幂等副作用 | HITL resume 幂等 | **`ToolIdempotencyStore` + 只读才超时重试** |
| HITL 高危工具 | `approvalId` 流程 | 已具备（≈练习 4） |
| 传引用（大文件） | 全文塞 Context | **`file_id` + `readFileChunk`** |

---

## 2. 优先级与改动清单

### P0 — Schema 即 Prompt

**问题**：模型读的是 JSON Schema / description，不是 Java 实现；短描述导致乱用工具。

**实现**：为下列工具补齐「何时用 / 何时不用 / 返回什么」：

| 工具 | 路径 |
|------|------|
| `searchWeb` | `tools/WebSearchTool.java` |
| `scrapeWebPage` / `startScrapeWebPage` | `tools/WebScrapingTool.java` |
| `readFile` / `readFileChunk` / `writeFile` | `tools/FileOperationTool.java` |
| `downloadResource` / `startDownloadResource` | `tools/ResourceDownloadTool.java` |
| `executeTerminalCommand` | `tools/TerminalOperationTool.java` |
| `generatePDF` / `startGeneratePDF` | `tools/PDFGenerationTool.java` |
| `checkAsyncToolTask` | `tools/AsyncToolStatusTool.java` |
| `doTerminate` | `tools/TerminateTool.java` |
| `searchKnowledgeBase` | `rag/RagTool.java` |

**边界速查**：

| 场景 | 用 |
|------|-----|
| 实时外网事实 | `searchWeb` |
| 已有 URL 要正文 | `scrapeWebPage`（慢则 `startScrapeWebPage`） |
| 知识库内文档 | `searchKnowledgeBase` |
| 沙箱本地文件 | `readFile` → 大文件用 `readFileChunk` |
| 交付 PDF | `generatePDF` / `startGeneratePDF` |

---

### P1 — 同轮多 Tool 真并行

**问题**：模型一次输出多个 tool call 时，Spring AI `DefaultToolCallingManager` 串行执行，N 次延迟无法压缩。

**实现**：

| 组件 | 路径 | 行为 |
|------|------|------|
| `ParallelToolCallingSupport` | `agent/ParallelToolCallingSupport.java` | ≥2 个 call 时 `CompletableFuture` 扇出；单 call 仍走同一路径 |
| `ToolCallAgent.act()` | 替换 `toolCallingManager.executeToolCalls` | 每工具独立 30s 超时 |

---

### P1 — Observation Sanitizer

**问题**：抓取/文件结果含 HTML、Base64、超长文本，冲垮 Context（教程练习 6）。

**实现**：`guard/ObservationSanitizer.java` — 去标签 / 省略 Base64 / 压缩空白 / 超长截断并注入 `[System Note: ...]`。  
在 `ToolCallAgent.act()` 写入历史前对每条 ToolResponse 清洗。

---

### P1 — 副作用幂等 + 超时重试策略

**问题**：超时重试可能导致写文件/下载/终端执行两次。

**实现**：

| 组件 | 路径 | 行为 |
|------|------|------|
| `ToolIdempotencyStore` | `tools/ToolIdempotencyStore.java` | payload 指纹 + TTL 缓存成功结果 |
| `ToolSideEffectPolicy` | `tools/ToolSideEffectPolicy.java` | 只读工具可超时重试；副作用工具不自动重试 |
| 接入 | write / download / PDF / terminal | 同指纹命中则 replay |

配置：

```yaml
app.tools.idempotency-ttl-seconds: ${TOOL_IDEMPOTENCY_TTL_SECONDS:600}
```

---

### P2 — file_id + read_chunk（传引用）

**问题**：大文件全文进参数/Observation → Token 爆炸。

**实现**：

| 组件 | 行为 |
|------|------|
| `FileHandleStore` | 注册 `file_id` ↔ 内容 |
| `readFile` | 超过 2000 字符只返回 preview + `file_id` |
| `readFileChunk` | 按行读取（0-based startLine，maxLines≤200） |

---

### P2 — 长任务 Submit-Poll

**问题**：>30s 任务挂死 Agent 步 → 模型以为失败并重试。

**实现**：

| 组件 | 路径 |
|------|------|
| `AsyncToolTaskService` | `tools/async/AsyncToolTaskService.java` |
| `startScrapeWebPage` / `startDownloadResource` / `startGeneratePDF` | 立即返回 `taskId` |
| `checkAsyncToolTask` | 轮询 RUNNING / COMPLETED / FAILED |

配置：

```yaml
app.tools.async-task-ttl-seconds: ${TOOL_ASYNC_TASK_TTL_SECONDS:3600}
```

---

## 3. 未做（诚实边界）

- Browser 像素点击 / SoM 坐标系对齐（练习 5）— 产品非 VLA
- Observation 小模型智能摘要 — 仍以规则清洗 + 硬截断为主；Compress 档继续用现有 `TokenBudgetManager`
- 跨节点分布式幂等（仅进程内 ConcurrentHashMap）

---

## 4. 验证

```bash
# JDK 21
mvn "-Dtest=ObservationSanitizerTest,ToolSideEffectPolicyTest,ToolIdempotencyStoreTest,FileHandleStoreTest,TerminalOperationToolTest,PDFGenerationToolTest" test
```

手动：`/api/ai/manus/chat` 触发多搜索并行；大文件 `readFile` 应返回 `file_id`；副作用超时不应自动重跑。

---

## 相关代码

- `agent/ToolCallAgent.java` · `agent/ParallelToolCallingSupport.java`
- `tools/*` · `tools/async/AsyncToolTaskService.java`
- `guard/ObservationSanitizer.java`
- 面试口述：`docs/interview-perception-goal-reliability.md`（Ch3 增补节）
- 功能层：`docs/FEATURES.md` L2 / L26
