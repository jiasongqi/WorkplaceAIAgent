# agent_product 项目评审报告

> 评审日期: 2026-06-10
> 评审范围: 后端全量代码 + 架构设计 + 配置
> 评分: **8.8 / 10**（当前）→ 修复后预估 **9.5 / 10**

---

## 一、总体评价

项目架构清晰，NLU V4.2 Pipeline 设计优秀（单次 LLM 调用），Multi-Agent 路由 + Trace 体系 + 质量守卫形成了完整的 Agent 平台。Controller 层大部分保持了 thin adapter 风格，AppService 分层合理。文件存储方案（无 DB）适合当前阶段，渐进增强思路正确。

**核心定位**: 从 Agent Demo → Agent Operating System（Agent OS）的演进路径已经清晰。

---

## 二、问题清单

### 🔴 P0 — 必须修复（安全/稳定性/成本）

---

#### 🔴-1. DocumentController 无鉴权 — 任何人可上传/删除文档

- **文件**: `controller/DocumentController.java`
- **问题**: `uploadDocument` / `addDocument` / `deleteDocument` / `listDocuments` 四个接口均无 token/Authorization 校验
- **影响**: 匿名用户可上传恶意文件、删除知识库文档
- **修复**: 注入 `AuthService`，所有写操作加 `authenticate()`

#### 🔴-2. AiController 多个端点无鉴权

- **文件**: `controller/AiController.java` (L49-163)
- **问题**:
  - `/ai_chat/chat/sync`, `/ai_chat/chat/sse` — 无鉴权
  - `/ai_chat/rag/sync`, `/ai_chat/tools/sync` — 无鉴权
  - `/ai_chat/mcp/sync`, `/ai_chat/report/sync` — 无鉴权
  - `/manus/chat` — 无鉴权
- **影响**: 任何人可消耗 DashScope API 额度
- **修复**: 至少给同步接口加 `AuthService.authenticate()`，`/manus/chat` 的 SseEmitter 端点同理

#### 🔴-3. OrchestratorAppService 重复调用 usageTracker.track()

- **文件**: `service/OrchestratorAppService.java` (L60 + L63)
- **问题**: 完全相同的代码出现了两次：
  ```java
  usageTracker.track(userId, UsageEventType.CHAT, null, 0);
  ```
- **影响**: 每次对话计数翻倍，用量统计失真
- **修复**: 删除其中一行

#### 🔴-4. Agent 调用链路缺少熔断/降级

- **现状链路**:
  ```
  User → Orchestrator → Agent → LLM → Tool
  ```
- **问题**: 如果 DashScope 超时 / MCP Server 挂掉 / Tool 执行失败，直接 `throw Exception`，整个 Agent 流程失败，无降级策略
- **影响**: 一个外部依赖故障导致整个对话不可用
- **修复方案**: 引入 Resilience4j，统一熔断/重试/超时：
  ```
  需要加熔断的组件:
  ├── LlmGateway        — @Retry + @CircuitBreaker (DashScope 超时降级到备用模型)
  ├── ToolExecutor       — @TimeLimiter (单工具执行超时)
  ├── MCPExecutor        — @CircuitBreaker (MCP Server 不可用时降级)
  └── QualityGuardAgent  — @TimeLimiter (质量审查超时不阻断主流程)
  ```
- **降级策略示例**:
  | 组件 | 故障 | 降级行为 |
  |------|------|----------|
  | DashScope 超时 | 3次失败 | 降级到 Ollama 本地模型 |
  | MCP Server 不可用 | 5xx | 跳过工具调用，纯对话模式 |
  | Tool 执行超时 | >30s | 返回"工具暂时不可用"，继续对话 |
  | QualityGuard 超时 | >10s | 跳过质量审查，正常返回 |

#### 🔴-5. 多 Agent 没有 Token Budget — 成本失控风险

- **问题**: 用户一次复杂请求可能触发 Planner → DataAgent → ReportAgent → ReviewAgent，上下文越来越长（20k → 40k → 80k token），直接烧钱
- **影响**: 单次对话成本不可控，恶意用户可刷爆 API 额度
- **修复方案**: 新增 `ConversationBudgetManager`：
  ```
  ConversationBudgetManager
  ├── trackSessionTokens(chatId, agentType, tokens)
  ├── checkBudget(chatId) → BudgetStatus { OK, WARNING, EXCEEDED }
  ├── autoCompress(chatId) — 超阈值自动压缩上下文
  └── enforceLimit(chatId) — 硬限截断，返回提示
  ```
- **阈值建议**:
  | 级别 | Token 数 | 行为 |
  |------|----------|------|
  | 正常 | < 8,000 | 无操作 |
  | 警告 | 8,000 - 20,000 | 自动压缩历史上下文 |
  | 硬限 | > 20,000 | 截断 + 提示用户"请开新会话" |
  | 单次请求 | > 4,000 | 拒绝，提示"消息过长" |

---

### 🟠 P1 — 建议修复（架构/可维护性）

---

#### 🟠-1. ProfileController 鉴权风格不一致

- **文件**: `controller/ProfileController.java` (L57-60)
- **问题**: 自己写 `extractUserId()` 方法做鉴权，其他 Controller 统一用 `AuthService.authenticate()`。且鉴权失败返回 `Response.failed(401)` 而非抛 `BusinessException`
- **修复**: 统一使用 `AuthService`，鉴权失败走 `GlobalExceptionHandler`

#### 🟠-2. SessionController 每个方法重复鉴权代码

- **文件**: `controller/SessionController.java`
- **问题**: 12 个端点，每个都有相同的 3 行：
  ```java
  @RequestParam token + @RequestHeader authHeader
  + authService.authenticate(token, authHeader)
  ```
- **修复**: 用 Spring `HandlerInterceptor` 或 `@AuthenticationPrincipal` 统一注入 userId，Controller 只接收已认证的 userId

#### 🟠-3. AiController 暴露 4 种 SSE 模式 — 过度 API

- **文件**: `controller/AiController.java` (L49-88)
- **问题**: `/ai_chat/chat` 有 4 个变体：`sync` / `sse(Flux)` / `server_sent_event(Flux<SSE>)` / `sse_emitter`，前端只用一种，其余是 demo 代码
- **修复**: 保留 `sync` + `orchestrator/chat(SseEmitter)`，删除或移到 `/demo/` 路径下

#### 🟠-4. YuManus 每次请求 new 实例 — 应改为 Prototype Bean

- **文件**: `controller/AiController.java` (L116)
- **问题**: `new YuManus(allTools, dashscopeChatModel)` 在 Controller 中
- **修复**: 改为 `@Scope("prototype")` Bean，注入 `ObjectProvider<YuManus>`

#### 🟠-5. GlobalExceptionHandler 缺少 HTTP 状态码映射

- **文件**: `exception/GlobalExceptionHandler.java` (L22-26)
- **问题**: `handleBusiness()` 没有 `@ResponseStatus`，所有 `BusinessException` 都返回 200（body 里带 code），前端可能依赖 HTTP 状态码做拦截（如 401 跳登录）
- **修复**: 根据 `e.getCode()` 动态设置 `HttpStatus`，或至少对 401/403 设置 `@ResponseStatus`

#### 🟠-6. OrchestratorAgent 构造函数 22 个参数 — 需要 AgentRuntime 统一

- **文件**: `agent/OrchestratorAgent.java` (L98-122)
- **问题**: 构造函数注入 22 个依赖，职责过重。包含：NLU、路由、Trace、质量守卫、画像、交付物、记忆管理、技能执行、5 个子 Agent
- **修复方案**: 抽取 `AgentRuntime` 统一生命周期：
  ```
  AgentRuntime 统一生命周期:
  ├── beforeExecute()   — 预处理（鉴权、Budget 检查）
  ├── loadMemory()      — 加载上下文（Session + CrossAgent）
  ├── execute()         — 核心执行（子类实现）
  ├── saveMemory()      — 持久化记忆
  ├── recordTrace()     — 记录轨迹
  └── afterExecute()    — 后处理（画像更新、质量审查）

  AbstractAgent implements AgentRuntime:
  ├── ResumeAgent extends AbstractAgent
  ├── NegotiationAgent extends AbstractAgent
  ├── EscapeAgent extends AbstractAgent
  ├── GeneralCareerAgent extends AbstractAgent
  └── ConsultationAgent extends AbstractAgent
  ```
- **收益**: OrchestratorAgent 从 22 参数降到 ~8 参数（只持有 Runtime + Router + 子 Agent Map）

#### 🟠-7. Tool 缺少统一执行框架 — ToolExecutor

- **现状**: 工具调用散落在各处，未来 MCP 一接入 100+ Tool 会炸
- **修复方案**: 新增 `ToolExecutor`：
  ```
  ToolExecutor
  ├── execute(toolId, args, context) → ToolResult
  ├── 内部流程:
  │   ├── 1. 权限检查 (是否有权调用该工具)
  │   ├── 2. Quota 检查 (调用频次限制)
  │   ├── 3. 审计日志 (谁在什么时候调了什么)
  │   ├── 4. Trace 记录 (纳入 TraceSpan)
  │   ├── 5. 超时控制 (@TimeLimiter)
  │   ├── 6. 执行工具
  │   └── 7. 异常转换 (统一错误格式)
  └── 支持:
      ├── Spring AI Function Calling
      ├── MCP Tool
      └── Plugin Tool
  ```
- **收益**: 未来 MCP / Function Calling / Plugin 全部统一入口

#### 🟠-8. Memory 没有分层 — 未来 RAG 进来会重构

- **现状**: 有 `ChatMemoryManager` + `CrossAgentMemory`，但都是单层
- **修复方案**: 设计三层 Memory 体系：
  ```
  MemoryManager
  ├── WorkingMemory        — 当前对话上下文（最近 N 轮）
  │   └── ChatMemoryManager (已有)
  ├── SessionMemory        — 会话级持久化（完整对话历史）
  │   └── PersistentMessageRepository (已有)
  └── LongTermMemory       — 长期记忆（用户画像、知识沉淀）
      ├── UserProfileService (已有)
      └── VectorStore / RAG (已有 PGVector)
  ```
- **收益**: RAG 进来时只需接入 LongTermMemory 层，不用重构

---

### 🟡 P2 — 可优化（代码质量/企业级特性）

---

#### 🟡-1. application.yml 品牌名不一致

- **文件**: `application.yml` (L3)
- **问题**: `spring.application.name = "yu-ai-agent"`，品牌名是 WorkPilot
- **修复**: 改为 `workpilot` 或 `agent-product`

#### 🟡-2. JWT 默认密钥硬编码在配置中

- **文件**: `application.yml` (L68)
- **问题**: `jwt.secret` 有默认值，生产如果忘配环境变量会用弱密钥
- **修复**: 生产 profile 中去掉默认值，启动时强制检查

#### 🟡-3. 缺少请求参数校验注解

- **问题**: 没有使用 `@Valid` / `@NotNull` / `@Size` 等 Jakarta Validation
- **修复**: 在 `@RequestBody` 参数上加 `@Valid`，DTO 中加约束注解

#### 🟡-4. 缺少 CORS 配置

- **问题**: 前端是独立 Vue 项目，但后端没有 CORS 配置
- **修复**: 添加全局 CORS 配置，限制 `allowed-origins`

#### 🟡-5. TestApiKey.java 在 main source 目录

- **文件**: `src/main/java/.../demo/invoke/TestApiKey.java`
- **问题**: 测试代码混入生产代码目录
- **修复**: 移到 `src/test/java/`

#### 🟡-6. TraceController 业务逻辑偏重

- **文件**: `controller/TraceController.java` (L55-63, L115-121)
- **问题**: `canAccess()` 权限判断在 Controller 中，且直接调用 `traceRepository`
- **修复**: 抽取 `TraceAppService`

#### 🟡-7. AiController 注入了底层 AI 依赖

- **文件**: `controller/AiController.java` (L30-33)
- **问题**: Controller 直接持有 `ToolCallback[]` 和 `ChatModel`，只有 `/manus/chat` 用到
- **修复**: 这些依赖应该在 Agent 层

#### 🟡-8. spring.ai.ollama 配置存在但未使用

- **文件**: `application.yml` (L22-24)
- **问题**: 配置了 ollama `gemma3:1b`，但 `AgentConfig` 全部用 `dashscopeChatModel`
- **修复**: 如果不用，注释掉避免启动时连接 `localhost:11434` 失败

#### 🟡-9. Trace 缺少 Replay 能力

- **现状**: `TraceRecorder` + `TraceRepository` 已经很好，但只能"看"不能"重放"
- **修复方案**: 未来增加 Agent Replay：
  ```
  TraceReplayService
  ├── replay(traceId) → 重新执行相同的 Agent 链路
  ├── replayFrom(traceId, stepId) → 从某一步开始重放
  └── compare(traceIdA, traceIdB) → 对比两次执行差异
  ```
- **价值**: 排查 Agent 问题时可以精确复现，类似 LangSmith / OpenAI Tracing

#### 🟡-10. 缺少 Prompt Version 管理

- **现状**: 所有 Prompt 硬编码在 Java 代码中（`YuManus.SYSTEM_PROMPT`、各 Agent 的 prompt 模板）
- **问题**: 未来 prompt 迭代时无法知道哪个版本在生效
- **修复方案**: 新增 `PromptRegistry`：
  ```
  PromptRegistry
  ├── getPrompt(key, version) → String
  ├── getLatest(key) → String
  └── 存储:
      prompt_key        | version | content
      intent_router     | v3      | "You are..."
      report_writer     | v5      | "Generate..."
      quality_guard     | v2      | "Review..."
  ```
- **收益**: 支持 A/B 测试、prompt 回滚、效果对比

---

## 三、亮点（做得好的地方）

| 维度 | 亮点 |
|------|------|
| NLU | V4.2 Pipeline — 单次 LLM 调用 + 模板澄清，设计精良 |
| 分层 | OrchestratorAppService 验证/鉴权/委派，Controller thin adapter |
| 响应 | `Response<T>` + `ResultCode` 统一响应体 |
| 异常 | `BusinessException` 工厂方法 (`notLoggedIn`/`forbidden`/`badRequest`/`notFound`) |
| 存储 | 文件存储方案简洁，渐进增强到 DB 的路径清晰 |
| Trace | Span/Context/Recorder/Repository 四层，完整可观测 |
| 记忆 | `CrossAgentMemory` 解决多 Agent 切换记忆丢失 |
| 质量 | QualityGuard 红队模式 + 风险阻断，生产级设计 |
| 交付物 | ArtifactShelf 货架模式，Agent 间结果传递优雅 |

---

## 四、修复优先级路线图

### Phase 1 — 安全 + 稳定（本周）
```
🔴-1  DocumentController 加鉴权
🔴-2  AiController 同步接口加鉴权
🔴-3  删除重复 usageTracker.track()
🟠-1  ProfileController 统一 AuthService
🟠-5  GlobalExceptionHandler 加 HTTP 状态码映射
```

### Phase 2 — 成本控制 + 可靠性（下周）
```
🔴-4  引入 Resilience4j 熔断/降级
🔴-5  ConversationBudgetManager Token 预算
🟡-2  JWT 密钥生产环境强制配置
🟡-4  CORS 配置
```

### Phase 3 — 架构升级（本月内）
```
🟠-6  抽取 AgentRuntime + AbstractAgent
🟠-7  统一 ToolExecutor
🟠-8  Memory 三层分层
🟠-3  清理 demo SSE 端点
🟠-4  YuManus 改为 Prototype Bean
```

### Phase 4 — 企业级特性（下月）
```
🟡-9  Trace Replay
🟡-10 PromptRegistry
🟡-6  TraceController 抽取 AppService
🟡-7  Controller 清理底层依赖注入
```

---

## 五、评分演进

| 阶段 | 评分 | 说明 |
|------|------|------|
| 当前 | **8.8 / 10** | 架构优秀，安全/成本有漏洞 |
| Phase 1 完成 | **9.1 / 10** | 鉴权 + 异常处理修复 |
| Phase 2 完成 | **9.3 / 10** | 熔断 + Token 预算，生产就绪 |
| Phase 3 完成 | **9.5 / 10** | AgentRuntime + ToolExecutor + Memory 分层 |
| Phase 4 完成 | **9.7 / 10** | 企业级 Agent OS 架构 |

---

## 六、总结

这个项目已经不是 Demo Agent 了。从方案演进来看（NLU V4、Multi-Agent、Trace、QualityGuard、Artifact），已经在往 **Agent Operating System** 的方向走。

下一阶段的核心目标：
1. **安全兜底** — 鉴权 + 熔断 + 预算（Phase 1-2）
2. **架构抽象** — AgentRuntime + ToolExecutor + MemoryManager（Phase 3）
3. **企业级能力** — Replay + Prompt Version + 统一鉴权（Phase 4）

最终目标：接近 LangChain / CrewAI / OpenAI Agents SDK / Microsoft AutoGen 这一类企业级 Agent Runtime 的架构水平。
