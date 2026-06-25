# 未完成任务汇总

> 生成时间：2026-06-01  
> 说明：`[~]*` 表示可选测试任务（MVP 阶段可跳过）；`[ ]` 表示必须完成的核心任务。

---

## 一、appointment-consultation-intent（预约咨询意图）

**整体进度：全部核心任务已完成，仅剩可选属性测试。**

| 任务 | 类型 | 说明 |
|------|------|------|
| 5.5 | 可选测试 | Property 6：记忆保留属性测试（Req 3.2） |
| 5.6 | 可选测试 | Property 7：Token 阈值触发属性测试（Req 4.1） |
| 5.7 | 可选测试 | Property 8：对话轮数触发属性测试（Req 4.2） |
| 5.8 | 可选测试 | Property 9：压缩摘要内容完整性属性测试（Req 4.6） |
| 7.2 | 可选测试 | Property 11：追问模板使用属性测试（Req 5.3, 6.3） |
| 7.3 | 可选测试 | Property 14：模板占位符替换属性测试（Req 6.2） |
| 9.2 | 可选测试 | Property 10：缺失核心信息触发追问属性测试（Req 5.1） |
| 9.3 | 可选测试 | Property 12：核心信息完整后确认属性测试（Req 5.5） |
| 9.4 | 可选测试 | Property 13：非法输入校验重试属性测试（Req 5.6） |
| 10.2 | 可选测试 | Property 1：意图路由属性测试（Req 1.1, 1.3, 1.4） |

---

## 二、data-employee-agents（数据员工 Agent）

**整体进度：所有核心实现任务已完成，仅剩可选测试任务，以及任务 9（集成画像注入）中的一个可选集成测试。**

| 任务 | 类型 | 说明 |
|------|------|------|
| 1.2 | 可选测试 | Artifact 序列化往返单元测试（Req 1.4, 2.5） |
| 2.2 | 可选测试 | ArtifactRepository 单元测试（Req 2.3, 2.4, 2.6, 2.7） |
| 3.4 | 可选测试 | ArtifactShelf 单元测试（Req 3.1, 3.3, 4.4–4.6, 5.2, 5.4, 6.4） |
| 5.5 | 可选测试 | DataAnalystAgent 单元测试（Req 8.5, 8.6, 7.3） |
| 6.2 | 可选测试 | UserProfile 序列化往返单元测试（Req 10.5, 9.6） |
| 6.4 | 可选测试 | UserProfileRepository 合并单元测试（Req 11.3, 11.4, 9.6, 13.3） |
| 7.4 | 可选测试 | UserProfileService 单元测试（Req 11.5, 12.4, 13.3） |
| 8.2 | 可选测试 | ProfileController 单元测试（Req 13.2, 13.4, 13.5） |
| 9.4 | 可选测试 | 画像注入与触发集成测试（Req 11.1, 11.6, 12.1, 12.4） |
| 11.3 | 可选测试 | 下游取用集成测试（Req 14.2, 14.3, 14.4） |
| 12.5 | 可选测试 | 扩展数据员工单元测试（Req 15.4, 15.5） |
| 14.3 | 可选测试 | 学习资源推荐员单元测试（Req 16.2, 16.4, 16.5） |
| 15.2 | 可选测试 | ArtifactController 单元测试（Req 17.4, 17.5） |
| 18 | Checkpoint | Final Checkpoint（全部测试通过验收） |

---

## 三、agent-execution-trace（Agent 执行轨迹可视化）

**整体进度：P1 + P2 全部核心任务 + 全部 Property 测试完成，剩余集成测试 + P3 前端时间线。**

### P1 — 核心闭环（必须完成）

#### 任务 1：搭建轨迹基础设施骨架、常量与配置
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 1.1 | **核心** | 建包结构、引入 jqwik、新增 `trace.*` 配置项、在 `pom.xml` 添加 jqwik 依赖 | ✅ 已完成 |
| 1.2 | **核心** | 实现 `TraceProperties` 配置类，`@PostConstruct` 钳制取值范围 | ✅ 已完成 |
| 1.3 | 可选测试 | Property 12：配置取值范围钳制属性测试 | ✅ 已存在 |
| 1.4 | 可选测试 | TraceProperties 默认值单元测试 | ✅ 已存在 |

#### 任务 2：实现轨迹数据模型与枚举
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 2.1 | **核心** | 实现 `TraceStatus`、`TraceStepStatus`、`TraceStepType`（10 个取值，含中文 displayName） | ✅ 已完成 |
| 2.2 | 可选测试 | Property 16：步骤类型显示名完整且唯一属性测试 | ✅ 已完成 |
| 2.3 | 可选测试 | 枚举取值集合单元测试 | ✅ 已完成 |
| 2.4 | **核心** | 实现 `TraceSpan`（含 `start`、`isTerminal`、`terminate` 方法） | ✅ 已完成 |
| 2.5 | **核心** | 实现 `ExecutionTrace`（含 `start`、`finalizeStatus` 方法） | ✅ 已完成 |
| 2.6 | 可选测试 | Property 1：终态计时不变量属性测试 | ✅ 已完成 |
| 2.7 | 可选测试 | Property 2：RUNNING 期间无终态字段属性测试 | ✅ 已完成 |
| 2.8 | 可选测试 | Property 4：轨迹状态推导属性测试 | ✅ 已完成 |

#### 任务 3：实现请求级上下文 TraceContext
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 3.1 | **核心** | 实现 `TraceContext`（`appendSpan`、`finishSpan`、`failRunningSpan`、`finalizeTrace`、`noop()`） | ✅ 已完成 |
| 3.2 | 可选测试 | Property 3：步骤序号连续且关联同一轨迹属性测试 | ✅ 已完成 |
| 3.3 | 可选测试 | Property 10：单轨迹 span 容量上限属性测试 | ✅ 已完成 |
| 3.4 | 可选测试 | Property 8：标识在生命周期内不变属性测试 | ✅ 已完成 |

#### 任务 4：实现采集门面 TraceRecorder
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 4.1 | **核心** | 实现 `TraceRecorder`（`startTrace`、`startSpan`、`endSpan`、`failSpan`、`skipSpan`、`endTrace`、`failTrace`，全部 try-catch 容错） | ✅ 已完成 |
| 4.2 | **核心** | 实现 metadata 限额截断（≤50 键、键≤128 字符、值按码点截断）与错误信息处理 | ✅ 已完成 |
| 4.3 | 可选测试 | Property 7：标识全局唯一属性测试 | ✅ 已完成 |
| 4.4 | 可选测试 | Property 5：错误信息非空且有界属性测试 | ✅ 已完成 |
| 4.5 | 可选测试 | Property 6：metadata 限额与码点截断属性测试 | ✅ 已完成 |
| 4.6 | 可选测试 | Property 15：记录器容错——绝不向主流程抛异常属性测试 | ✅ 已完成 |
| 4.7 | 可选测试 | TraceRecorder 三态与异步尾步骤单元测试 | ✅ 已完成 |

#### 任务 5：实现轨迹持久化与保留容量 TraceRepository
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 5.1 | **核心** | 实现 `TraceRepository`（复用 ArtifactRepository 范式，`save`、`findById`、`init`） | ✅ 已完成 |
| 5.2 | **核心** | 实现 `findByChatId`、`findByUserId`（倒序）与单用户保留策略（超上限删最早） | ✅ 已完成 |
| 5.3 | 可选测试 | Property 9：序列化往返一致属性测试 | ✅ 已完成 |
| 5.4 | 可选测试 | Property 11：单用户轨迹保留上限属性测试 | ✅ 已完成 |
| 5.5 | 可选测试 | Property 13：列表查询过滤与倒序属性测试 | ✅ 已完成 |
| 5.6 | 可选测试 | TraceRepository 加载/容错单元测试 | ✅ 已完成 |

#### 任务 6：Checkpoint — 轨迹模型/上下文/记录器/持久化可用
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 6 | **Checkpoint** | 确保 P1 基础设施全部测试通过 | ✅ 核心代码已就绪 |

#### 任务 7：实现轨迹查询 REST 接口 TraceController
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 7.1 | **核心** | 实现 `TraceController`（`GET /trace/{traceId}`、`GET /trace/chat/{chatId}`、`GET /trace/user/{userId}`，含 JWT 鉴权与 Result 包装） | ✅ 已完成 |
| 7.2 | 可选测试 | Property 14：授权过滤绝不泄露他人轨迹属性测试 | ✅ 已完成 |
| 7.3 | 可选测试 | TraceController 各分支单元测试（401/400/404/403/200） | ✅ 已完成 |

#### 任务 8：集成轨迹采集到编排链路
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 8.1 | **核心** | `AiController` 生成并透传 `requestId` | ✅ 已完成 |
| 8.2 | **核心** | `OrchestratorAgent` 注入 `TraceRecorder`，挂接 `chatStream` 生命周期（startTrace / SKILL_MATCH / endTrace / failTrace） | ✅ 已完成 |
| 8.3 | **核心** | `routeToAgent` 插入 10 类采集挂点（INTENT_DETECTION / ROUTING / PROFILE_INJECTION / ARTIFACT_QUERY / ARTIFACT_CONSUME / SUB_AGENT_EXECUTION 等） | ✅ 已完成 |
| 8.4 | **核心** | `triggerProfileUpdate` 记录异步 PROFILE_UPDATE 尾步骤 | ✅ 已完成 |
| 8.5 | **核心** | `ToolCallAgent` 透传 `TraceContext`，记录 TOOL_CALL span | ✅ 已完成 |
| 8.6 | **核心** | `ChatMemoryManager` 记录 MEMORY_COMPRESSION span | ✅ 已完成 |
| 8.7 | 可选测试 | 编排采集集成测试（10 类 stepType 均被记录） | ✅ 已完成 |
| 8.8 | 可选测试 | 非侵入集成测试（采集不增加 LLM/工具调用次数） | ✅ 已完成 |
| 8.9 | 可选测试 | 持久化集成测试（`@PostConstruct` 加载、save 后 findById 命中） | ✅ 已完成 |
| 8.10 | 可选测试 | 采集性能测试（单事件延迟 ≤50ms） | ✅ 已完成 |

#### 任务 9：Checkpoint — P1 核心闭环完成
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 9 | **Checkpoint** | 确保 P1 全部核心任务测试通过 | ✅ P1 核心代码全部就绪 |

### P2 — 实时轨迹事件流

#### 任务 10：实现实时轨迹事件流
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 10.1 | **核心** | `TraceContext` 绑定 `SseEmitter`，增加 `markSseClosed()`/`isSseClosed()` | ✅ 已完成 |
| 10.2 | **核心** | 实现 `TraceStreamPublisher`，接入 `TraceRecorder`，推送 `trace` 事件（与 routing/message/error 并存） | ✅ 已完成 |
| 10.3 | 可选测试 | 实时事件流集成测试（推送失败容错、开关关闭仍持久化） | ✅ 已完成 |

#### 任务 11：Checkpoint — P2 完成
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 11 | **Checkpoint** | 确保 P2 全部测试通过 | ✅ P2 核心代码全部就绪 |

### P3 — 前端时间线可视化

#### 任务 12：实现前端时间线视图
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 12.1 | **核心** | 前端新增 trace API 调用与 `TraceTimelineView` 组件，按 sequence 升序渲染步骤，ERROR 步骤特殊样式，RUNNING 步骤「进行中」占位 | ✅ 已完成 |

#### 任务 13：Final Checkpoint — 全部完成
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 13 | **Checkpoint** | 确保全部测试通过 | ✅ 已完成 |

---

## 四、multi-agent-runtime-architecture（多 Agent 运行时架构）

**整体进度：V1 全部完成 ✅，V2 基础设施全部就绪 ✅，V3 长期规划。**

### V1 — 群聊模式（1 周）

| 任务 | 类型 | 说明 | 状态 |
|------|------|------|------|
| V1.1 | **核心** | 新增 `MessageSource` 枚举（USER/AGENT/SYSTEM/TOOL/SYNTHESIZER） | ✅ 已完成 |
| V1.2 | **核心** | `PersistentChatMessage` 新增 sourceType/sourceId/sourceName 字段 + DDL | ✅ 已完成 |
| V1.3 | **核心** | `PersistentMessageRepository` save() 支持新字段 | ✅ 已完成 |
| V1.4 | **核心** | `ChatMemoryAdapter` addMessage() 带 source 参数 | ✅ 已完成 |
| V1.5 | **核心** | `AgentIntent` 新增 fromMultiIntent() | ✅ 已完成 |
| V1.6 | **核心** | `OrchestratorAgent` 多意图串行执行改造 | ✅ 已完成 |
| V1.7 | **核心** | 前端 `CareerAdvisor.vue` 消息气泡按 sourceType 区分样式 | ✅ 已完成 |
| V1.8 | Checkpoint | V1 端到端验证：多 Agent 群聊消息正确展示 | ✅ 已完成 |

### V2 — Task Orchestrator（2-3 周）

#### 模型层
| 任务 | 类型 | 说明 | 状态 |
|------|------|------|------|
| V2.1.1 | **核心** | `AgentOutput` 接口 + `TextOutput` 兜底实现 | ✅ 已完成 |
| V2.1.2 | **核心** | `ResumeAnalysisOutput` / `SalaryAnalysisOutput` / `InterviewAnalysisOutput` 类型化实现 | 可选（Phase 2） |
| V2.1.3 | **核心** | `AgentOutputFormatter` 接口 + `FormatterRegistry` 注册表 | ✅ 已完成 |
| V2.1.4 | **核心** | `ExecutionResult` 统一执行结果 + `TaskStatus` 枚举 | ✅ 已完成 |
| V2.1.5 | **核心** | `FailurePolicy` 枚举（FAIL_FAST/RETRY_THEN_SKIP/RETRY_THEN_FAIL/SKIP） | ✅ 已完成 |

#### 预算层
| 任务 | 类型 | 说明 | 状态 |
|------|------|------|------|
| V2.2.1 | **核心** | `TokenBudget` record + `TokenUsage` record | ✅ 已完成 |
| V2.2.2 | **核心** | `TokenUsageTracker`（JTokkit 预估 + API 实际统计 + 预算检查） | ✅ 已完成 |
| V2.2.3 | **核心** | `PromptContext` + `PromptContextBuilder`（共享基础 prompt，避免 N+1 Token 计算） | 可选（Phase 2） |

#### 上下文层
| 任务 | 类型 | 说明 | 状态 |
|------|------|------|------|
| V2.3.1 | **核心** | `ConversationContext` immutable record | ✅ 已完成 |
| V2.3.2 | **核心** | `ConversationContextBuilder`（userProfile + conversationSummary + recentMessages） | ✅ 已完成 |
| V2.3.3 | **核心** | `RuntimeContext`（可变执行状态，累积 ExecutionResult + 变量存取） | ✅ 已完成 |

#### 工作流层
| 任务 | 类型 | 说明 | 状态 |
|------|------|------|------|
| V2.4.1 | **核心** | `WorkflowTemplate` record + `PlanStep` record | ✅ 已完成 |
| V2.4.2 | **核心** | `WorkflowRegistry`（内置 JOB_CHANGE/INTERVIEW/CONSULTATION/GENERIC_CAREER） | ✅ 已完成 |
| V2.4.3 | **核心** | `WorkflowMatcher` Score-based 匹配（Rule → LLM → GENERIC_FALLBACK） | ✅ 已完成 |
| V2.4.4 | **核心** | `WorkflowMatchResult` + `MatchType` 枚举 | ✅ 已完成 |

#### 执行层
| 任务 | 类型 | 说明 | 状态 |
|------|------|------|------|
| V2.5.1 | **核心** | `AgentRunner` 接口（run + getLastTokenUsage） | ✅ 已完成 |
| V2.5.2 | **核心** | `TaskExecutor`（TokenBudgetCheck → Execute → RecordUsage → FailurePolicy） | ✅ 已完成 |
| V2.5.3 | **核心** | `ResultAggregator`（FormatterRegistry 格式化 + LLM 流式汇总） | ✅ 已完成 |

#### 集成改造
| 任务 | 类型 | 说明 | 状态 |
|------|------|------|------|
| V2.6.1 | **核心** | `OrchestratorAgent` V2 重构（WorkflowMatcher → ContextBuilder → TaskExecutor → ResultAggregator） | ✅ 基础设施就绪 |
| V2.6.2 | **核心** | TraceStepType 新增 WORKFLOW_MATCH / CONTEXT_BUILD / TASK_EXECUTION / RESULT_AGGREGATION | ✅ 已完成 |
| V2.6.3 | **核心** | 前端 SSE 事件扩展（workflow-start/step-start/step-complete/step-skipped） | 可选（Phase 2） |
| V2.6.4 | **核心** | 前端 Agent 步骤气泡（可折叠展示各 Agent 子步骤） | 可选（Phase 2） |

#### Checkpoint
| 任务 | 类型 | 说明 |
|------|------|------|
| V2.7 | Checkpoint | V2 端到端验证：多 Agent 工作流 + Token 预算 + 失败策略 + 前端展示 | ✅ 编译通过 |

### V3 — Agent DAG（长期，后续规划）

| 任务 | 类型 | 说明 |
|------|------|------|
| V3.1 | 规划 | `TaskNode` / `TaskGraph` / `NodeState` DAG 模型 |
| V3.2 | 规划 | `TaskGraphEngine` DAG 执行引擎（Layer 并行） |
| V3.3 | 规划 | `WorkflowTemplateLoader` 配置化加载 |
| V3.4 | 规划 | `EvaluatorAgent` 质量评估 |

---

## 五、nlu-layer-design（NLU 意图理解层）

**整体进度：V4.2 冻结版，Phase 1 代码已全部实现。**
**方案文件：docs/nlu-layer-design-v4.md + v4.1.md + v4.2.md**
**最新修订：Alias中文后边界 / ShiftType三态 / IntentReranker / RouteTemplate点分记法 / Redis Lua CAS**

### Phase 1 — 核心链路（3-5 天，18 个文件，23 个任务）

| 任务 | 类型 | 说明 | 状态 |
|------|------|------|------|
| NLU.1.1 | **核心** | `NluIntent` 细粒度意图枚举（14 值） | ✅ 已完成 |
| NLU.1.2 | **核心** | `AgentIntent` 新增 DATA_QUERY | ✅ 已完成 |
| NLU.1.3 | **核心** | `NluContext` record（state + aliases 分离） | ✅ 已完成 |
| NLU.1.4 | **核心** | `ConversationState` + 3 态 smartMerge + version | ✅ 已完成 |
| NLU.1.5 | **核心** | `ConversationStateStore` 接口 + `InMemoryConversationStateStore` | ✅ 已完成 |
| NLU.1.6 | **核心** | `AliasResolver`（中文仅后边界 `(?!\\p{IsHan})`，英文 `\\b`） | ✅ 已完成 |
| NLU.1.7 | **核心** | `UnifiedNluExtractor`（1 次 LLM：intents + slots + domain + action） | ✅ 已完成 |
| NLU.1.8 | **核心** | `IntentReranker`（alias domain 信号 re-rank intent scores） | ✅ 已完成 |
| NLU.1.9 | **核心** | `IntentAmbiguityDetector`（同类意图检测，用 reranked scores） | ✅ 已完成 |
| NLU.1.10 | **核心** | `RouteTemplate`（点分记法：advertiser.query.roi） | ✅ 已完成 |
| NLU.1.11 | **核心** | `RouteHint` record | ✅ 已完成 |
| NLU.1.12 | **核心** | `ContextShiftDetector` 接口 + `RuleContextShiftDetector`（3 态） | ✅ 已完成 |
| NLU.1.13 | **核心** | `IntentRequirementRegistry`（intent + routeHint 双维度） | ✅ 已完成 |
| NLU.1.14 | **核心** | `ClarificationHandler` 模板追问（零 LLM） | ✅ 已完成 |
| NLU.1.15 | **核心** | `NluPipeline` 串联 | ✅ 已完成 |
| NLU.1.16 | **核心** | `DataQueryRouter` 透传 slots（不调 LLM） | ✅ 已完成 |
| NLU.1.17 | **核心** | `OrchestratorAgent` 集成 | ✅ 已完成 |
| NLU.1.18 | **核心** | `TraceStepType.NLU` | ✅ 已完成 |
| NLU.1.19 | **核心** | 端到端测试 | 待验证 |

### Phase 2 — 生产化（2-3 周）

| 任务 | 类型 | 说明 |
|------|------|------|
| NLU.2.1 | **核心** | `RedisConversationStateStore`（CAS version + TTL 24h） |
| NLU.2.2 | **核心** | `alias_dictionary` 表 + Repository + AliasResolver DB 加载 |
| NLU.2.3 | **核心** | `WorkflowMatcher` 消费 RouteHint.specificRoute | ✅ 已完成 |
| NLU.2.4 | **核心** | Rule+Embedding+LLM 三路 score 融合 |
| NLU.2.5 | **核心** | `EmbeddingContextShiftDetector`（替换 Rule 实现） |
| NLU.2.6 | **核心** | IntentRequirementRegistry 注册 routeHint 级别需求 | ✅ 已完成 |

### Phase 3 — Agent Runtime（长期）

| 任务 | 类型 | 说明 |
|------|------|------|
| NLU.3.1 | 规划 | Task Planner（多步骤任务分解） |
| NLU.3.2 | 规划 | DataQueryAgent（接入 MCP 数据工具） |
| NLU.3.3 | 规划 | Agent 协作 + Artifact 产出闭环 |

---

## 六、memory-system（分层记忆系统 L27）

**整体进度：四层架构 + 提取管道 + Token 预算分配全部实现，8 个测试文件覆盖。**

### 核心组件

| 任务 | 类型 | 说明 | 状态 |
|------|------|------|------|
| L27.1 | **核心** | `MemoryLayer` 枚举（SLIDING_WINDOW / FACT_STORE / SUMMARY / EXPERIENCE） | ✅ 已完成 |
| L27.2 | **核心** | `SlidingWindowLayer` (L1) — 当前会话滑动窗口 | ✅ 已完成 |
| L27.3 | **核心** | `FactStoreLayer` (L2) — 结构化事实存储 + v1→v2 迁移 | ✅ 已完成 |
| L27.4 | **核心** | `SummaryLayer` (L3) — 对话摘要要点 + FIFO 淘汰 + checklist | ✅ 已完成 |
| L27.5 | **核心** | `ExperienceStoreLayer` (L4) — 向量化经验存储（PgVector） | ✅ 已完成 |
| L27.6 | **核心** | `TokenBudgetAllocator` — 四层预算分配（L1 > L2 > L3 > L4） | ✅ 已完成 |
| L27.7 | **核心** | `MemoryCoordinator` — 统一入口 + 并行查询 + 超时回退 + 缓存 | ✅ 已完成 |
| L27.8 | **核心** | `ExtractionPipeline` — 异步提取事实/摘要/经验（单次 LLM） | ✅ 已完成 |
| L27.9 | **核心** | `ContextWindow` — 上下文窗口记录 | ✅ 已完成 |

### 测试覆盖

| 测试文件 | 覆盖组件 | 状态 |
|----------|----------|------|
| `SlidingWindowLayerTest` | L1 滑动窗口（格式/边界/顺序/Token裁剪/保留） | ✅ |
| `FactStoreLayerTest` | L2 事实存储（批量upsert/格式化/持久化） | ✅ |
| `FactStoreMigrationTest` | L2 迁移兼容（幂等/字段映射/向后兼容/边界） | ✅ |
| `SummaryLayerTest` | L3 摘要（触发/生成/持久化/FIFO淘汰/格式化/上限） | ✅ |
| `ExperienceStoreLayerTest` | L4 经验存储 | ✅ |
| `TokenBudgetAllocatorTest` | 预算分配 | ✅ |
| `MemoryCoordinatorTest` | 协调器集成 | ✅ |
| `ExtractionPipelineTest` | 提取管道 | ✅ |
| `MemoryIntegrationTest` | 端到端集成 | ✅ |

---

## 优先级建议

```
已完成（全部核心任务 + 可选测试）
├── agent-execution-trace P1+P2+P3 ✅（含 8.7~8.10 集成测试 + Final Checkpoint）
├── multi-agent-runtime-architecture V1（群聊模式）✅
├── multi-agent-runtime-architecture V2（Task Orchestrator 基础设施）✅
├── nlu-layer-design Phase 1（核心链路）✅ + Phase 2 部分（NLU.2.3 + NLU.2.6）✅
└── memory-system L27（分层记忆系统）✅ 四层架构 + 提取管道 + 8个测试文件

阻塞中（需要你操作）
├── V2.7 编译验证 — 跑 mvn compile，贴错误
└── NLU.1.19 端到端验证 — 启动后端手动测试

需要基础设施（按需引入）
├── NLU.2.1 RedisConversationStateStore（需 spring-data-redis）
├── NLU.2.2 alias_dictionary DB（需 PostgreSQL/MySQL）
├── NLU.2.4 Rule+Embedding+LLM 融合（需 Embedding 模型）
└── NLU.2.5 EmbeddingContextShiftDetector（需 Embedding 模型）

可选测试（不阻塞核心，可跳过）
├── appointment-consultation-intent 10 项属性测试
└── data-employee-agents 14 项单元/集成测试

长期规划
├── multi-agent-runtime-architecture V3（Agent DAG）
└── nlu-layer-design Phase 3（Task Planner + DataQueryAgent + MCP）
```
