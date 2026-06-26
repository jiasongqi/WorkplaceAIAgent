---
name: workpilot-interview-qa
description: WorkPilot（全场景职场生存智囊）面试问答手册。覆盖架构设计、技术实现、场景设计三大类 50+ 问题，含 STAR 法则回答模板和高频追问。适用于 AI Agent 方向面试准备。
version: 1.5.0
tags: [interview, agent, architecture, career]
---

# WorkPilot 面试问答手册

> 项目：全场景职场生存智囊（WorkPilot）
> 技术栈：Java 21 + Spring Boot 3.4 + Spring AI 1.0 + Vue 3 + DashScope
> 定位：全场景职场 AI 智囊平台，覆盖求职到离职全生命周期
> 更新：v1.5 — 新增性能评估、经典范式、上下文工程、工具注册相关问题

---

## 一、项目介绍话术

### 30 秒版

> WorkPilot 是一个全场景职场 AI 智囊平台，基于 Java 21 + Spring AI 构建。核心是一个 OrchestratorAgent 主控编排 5 个专业子 Agent（简历、薪资、离职、咨询、通用），通过 NLU 意图理解管道做智能路由。亮点是四层记忆系统（滑动窗口 + 事实存储 + 摘要 + 向量化经验）和投票式安全访问控制。

### 1 分钟版

> WorkPilot 覆盖职场人从求职到离职的全生命周期。技术上，OrchestratorAgent 做意图路由，先走 KeywordRouter 快速路径（零 LLM），复杂消息走 NLU Pipeline（单次 LLM 调用完成意图识别 + 槽位提取 + 路由生成）。记忆系统是四层架构：L1 滑动窗口保持连贯、L2 事实存储精确匹配、L3 摘要要点清单、L4 向量化经验语义检索，通过 MemoryCoordinator 并行查询 + Token 预算分配。安全方面采用投票式访问控制（Agent 权限 + MCP 信任 + 调用配额），一票否决。还有工作流引擎（6 种节点）、沙箱执行（三级策略）、质量守护（5 维评分 + 红队对抗）等。

### 3 分钟版

> 在 1 分钟版基础上补充：数据员工采用黑板模式协作，上游 Agent 产出交付物放上 ArtifactShelf，下游按需取用，交付物有完整生命周期管理（DRAFT→REVIEWING→APPROVED→PUBLISHED→ARCHIVED）。评测中心 EvalCenter 支持 YAML 评测套件 + 回归检测。Prompt 版本管理支持灰度发布和 A/B 测试。全链路执行轨迹通过 TraceRecorder 记录 10+ 种 StepType，实时 SSE 推送前端时间线可视化。前端 Vue 3，10 个页面，支持流式对话、语音输入、多 Agent 消息来源标识。

---

## 二、架构设计类 Q&A

### Q1: 项目整体架构是怎样的？

**回答要点**：
- 四层架构：Frontend (Vue 3) → API Layer (Controller) → AppService Layer → Agent Layer (Core) → Infrastructure Layer
- Agent 层核心：OrchestratorAgent 做意图路由，5 个专业子 Agent 各司其职
- 基础设施层：ChatMemory、VectorStore、Trace、Sandbox、Access Control、MemoryCoordinator
- 关键类：`OrchestratorAgent`（主控）、`NluPipeline`（意图理解）、`MemoryCoordinator`（记忆协调）

**追问**：为什么选择这种分层而不是微服务？
> 项目定位是单体应用（个人作品集），单体内模块化足够。AppService 层做业务编排，Agent 层做 AI 逻辑，职责清晰。未来可按模块拆微服务。

---

### Q2: Agent 和传统链式调用有什么区别？

**回答要点**：
- 传统链式调用：硬编码线性流程，每步确定性
- Agent：自主决策，根据 observe 结果动态选择下一步 action
- 项目中 ReActAgent 实现 think→act→observe 循环，ToolCallAgent 根据 LLM 输出动态调用工具
- 但 OrchestratorAgent 的路由是确定性的（NLU → switch），这是有意为之：路由层确定性 + 子 Agent 层自主性

**追问**：你项目里哪些是"真 Agent"，哪些是"伪 Agent"？
> YuManus 是真 Agent（ReAct 自主规划 + 工具循环）。OrchestratorAgent 的路由是确定性的，但子 Agent 内部（如 ToolCallAgent）有自主决策。ConsultationAgent 的状态机是确定性的。这是有意设计：关键路径用确定性保证可靠性，探索性任务用自主性。

---

### Q3: 多 Agent 协作模式是怎么设计的？

**回答要点**：
- **老板-员工模式**：OrchestratorAgent 分发任务给子 Agent
- **群聊模式**（V1）：多意图串行执行，每个 Agent 独立回答，MessageSource 追踪来源
- **黑板模式**：数据员工通过 ArtifactShelf 协作，生产者放货、消费者取用
- **Task Orchestrator**（V2）：WorkflowMatcher + TaskExecutor + ResultAggregator，基础设施已就绪
- 关键类：`AgentIntent.fromMultiIntent()`、`MessageSource`、`ArtifactShelf`

**追问**：多 Agent 串行执行会不会很慢？
> 会，这是 V1 的取舍。V2 基础设施（ParallelNode）已就绪，可并行执行无依赖的 Agent。当前串行是因为多意图场景较少（<10%），优先保证正确性。

---

### Q4: NLU 意图理解管道是怎么设计的？

**回答要点**：
- **快速路径**：`KeywordRouter.containsCareerKeyword()` 判断，简单消息跳过 NLU LLM，直接走 GENERAL Agent
- **完整路径**：单次 LLM 调用完成 intent 排名 + slots + domain + action
- **管道组件**：AliasResolver → UnifiedNluExtractor → IntentReranker → IntentAmbiguityDetector → RouteTemplate → ContextShiftDetector → IntentRequirementRegistry → ClarificationHandler
- **关键决策**：1 次 LLM（非 2-3 次），延迟减半；别名不改原文；Confidence = Top1-Top2 差值；澄清用模板（零 LLM）
- 关键类：`NluPipeline`、`UnifiedNluExtractor`、`RouteHint`

**追问**：NLU 的 confidence 为什么不直接用 LLM 输出的概率？
> LLM 自报概率不可靠（overconfidence）。用 Top1-Top2 差值更能反映真实置信度：差值大说明意图明确，差值小说明模糊需要澄清。

---

### Q5: 四层记忆系统是怎么设计的？

**回答要点**：
- **L1 SlidingWindowLayer**：当前会话最近 N 条完整消息，保持连贯性
- **L2 FactStoreLayer**：结构化事实（身份/偏好/目标），键值对精确匹配
- **L3 SummaryLayer**：对话摘要要点清单（话题/决策/待办），FIFO 淘汰
- **L4 ExperienceStoreLayer**：历史经验案例，PgVector 向量化语义检索
- **MemoryCoordinator**：统一入口，并行查询四层（CompletableFuture），超时 2000ms 回退
- **TokenBudgetAllocator**：总预算 6000 tokens，按 L1→L4 优先级递减分配
- **ExtractionPipeline**：对话后异步提取事实/摘要/经验（单次 LLM）

**追问**：为什么分四层而不是一层向量数据库？
> 向量检索是模糊的，适合"经验案例"这种语义匹配场景。但"用户叫小琪"这种事实用键值对精确匹配更快更准。"最近聊了什么"用滑动窗口最直接。不同数据类型用不同存储，各取所长。

---

### Q6: RAG 检索链路是怎么设计的？

**回答要点**：
- **QueryRewriter**：查询改写，提升召回率
- **MultiQueryRetriever**：一个问题扩展为多个查询并行检索后合并去重
- **向量库**：PgVector（生产）/ SimpleVectorStore（开发）可切换
- **文档管理**：动态上传实时入库，按状态分类过滤（求职/在职/通用）
- 内置 11 篇职场文档

**追问**：RAG 检索不到怎么办？
> 当前没有显式的"检索不足拒绝回答"机制。改进方向：设置相似度阈值，低于阈值时拒绝回答或要求用户补充信息。

---

### Q7: 工作流引擎是怎么设计的？

**回答要点**：
- 6 种节点类型：AgentNode、ToolNode、ConditionNode、ParallelNode、LoopNode、ApprovalNode
- 状态机：PENDING → RUNNING → PAUSED（等待审批）/ COMPLETED / FAILED / CANCELLED
- LoopNode 有最大迭代次数限制，防死循环
- `WorkflowMatcher` Score-based 匹配（Rule → LLM → GENERIC_FALLBACK）
- 内置工作流模板：JOB_CHANGE / INTERVIEW / CONSULTATION / GENERIC_CAREER / DATA_QUERY

**追问**：ConditionNode 的条件判断是怎么实现的？
> 当前用简单字符串解析 `==`/`!=`，有注入风险。计划替换为 SpEL 表达式引擎。

---

### Q8: 安全防护体系是怎么设计的？

**回答要点**：
- **访问控制**：投票式决策（一票否决），三维投票器（AgentPolicyVoter + McpPolicyVoter + QuotaPolicyVoter）
- **沙箱执行**：三级策略（UNSANDBOXED / PROCESS_SANDBOX / DOCKER_SANDBOX）
- **循环检测**：`EmbeddingLoopDetector`，余弦相似度 0.88，滑动窗口 5 条
- **工具结果分级**：`ToolResultClassifier`（TIMEOUT / EMPTY / GARBAGE / NORMAL）
- **Token 预算**：`TokenBudgetManager` 三级策略（Normal / Compact / Compress）
- **事件总线**：`EventBusAdapter` 异步发布治理事件

**追问**：投票式决策和 RBAC 有什么区别？
> RBAC 是静态角色映射，投票式是动态多维度决策。比如一个 Agent 有权限（AgentPolicyVoter 通过）但 MCP 服务不信任（McpPolicyVoter 拒绝），最终拒绝。每个维度独立评估，一票否决。

---

### Q9: 交付物生命周期是怎么管理的？

**回答要点**：
- 状态机：DRAFT → REVIEWING → APPROVED → PUBLISHED → ARCHIVED
- 合法性校验：`ArtifactLifecycleManager.transition()` 检查状态流转合法性
- 旧状态兼容：PENDING→DRAFT, READY→APPROVED
- 审计事件：`ArtifactLifecycleEvent` 记录每次状态变更
- 黑板模式：数据员工通过 `ArtifactShelf.put()` 放货，下游 `query()`/`get()` 取用

---

### Q10: 质量守护机制是怎么设计的？

**回答要点**：
- **QualityGuardAgent**：4 种模式（OFF / AUTO / REVIEW / RED_TEAM）
- **5 维评分**：accuracyScore(30%) + completenessScore(20%) + logicScore(20%) + hallucinationScore(30%) + riskScore
- **模式解析**：RESUME/NEGOTIATION/ESCAPE → REVIEW；其他 → LLM 风险分类
- **持久化策略**：仅 HIGH/CRITICAL 写入 `quality-reviews.json`
- **EvalCenter**：YAML 评测套件 + 回归检测

---

## 三、技术实现类 Q&A

### Q11: SSE 流式响应是怎么实现的？

**回答要点**：
- Spring MVC `SseEmitter`（5 分钟超时）
- `CompletableFuture.runAsync()` 异步执行 Agent 逻辑
- `Flux<String>` token 级推送，`doOnNext` 逐 token 发送给 emitter
- 事件类型：`routing`（路由信息）、`message`（AI 回答）、`agent-turn`（Agent 切换）、`trace`（执行轨迹）、`clarification`（追问）、`status`（状态）、`error`（错误）
- 连接断开：`emitter.onTimeout()` / `emitter.onError()` 回调清理

**追问**：SSE 和 WebSocket 的取舍？
> SSE 单向（服务端→客户端），适合流式对话场景。WebSocket 双向，但 Spring MVC 对 SSE 支持更好，且 EventSource API 浏览器原生支持。对话场景不需要客户端实时推送，SSE 足够。

---

### Q12: Kryo 序列化为什么选 Kryo 而不是 JSON？

**回答要点**：
- ChatMemory 高频读写，Kryo 二进制序列化比 JSON 快 5-10 倍
- `FileBasedChatMemory` 按 agent 类型隔离存储
- 压缩策略：Token 阈值 4000 / 轮数 20 → LLM 生成摘要
- 文件持久化 + `ReadWriteLock` 保证并发安全

---

### Q13: JWT 鉴权是怎么实现的？

**回答要点**：
- `JwtUtil` 生成/验证 token
- SSE 接口 token 走 URL 参数（EventSource 不支持自定义 header）
- 游客模式自动登录（无 token 时分配临时用户）
- Controller 层 `@RequestHeader("Authorization")` 校验

---

### Q14: 向量数据库选型考虑？

**回答要点**：
- PgVector：生产环境，支持 PostgreSQL 生态，可与其他业务数据共库
- SimpleVectorStore：开发环境，内存级，零依赖
- 通过 `@ConditionalOnProperty` 切换
- 嵌入模型：DashScope text-embedding-v1

---

### Q15: Tool Calling 是怎么实现的？

**回答要点**：
- Spring AI `ToolCallback[]` 统一注册
- `ToolCallAgent` 解析 LLM 输出的 tool_calls，分发到对应 Tool
- 工具结果注入 observe，LLM 自主决定下一步
- `ToolResultClassifier` 分级处理（TIMEOUT/EMPTY/GARBAGE/NORMAL）
- `EmbeddingLoopDetector` 检测死循环

---

### Q16: MCP 协议集成是怎么做的？

**回答要点**：
- Spring AI MCP Client 接入外部 MCP 服务
- 支持 SSE + stdio 两种连接方式
- MCP 信任分级：VERIFIED(100) / PARTNER(70) / COMMUNITY(30) / PRIVATE(0)
- `McpPolicyVoter` 在访问决策中检查信任等级

---

### Q17: 用户画像是怎么提取的？

**回答要点**：
- 对话结束后 LLM 异步抽取 5 个维度：沟通偏好、语气偏好、关注领域、已知背景、历史诉求
- `UserProfileService` 编排抽取/合并/查询/清空/注入
- 画像注入到 system prompt（上限 1000 字符）
- 跨会话累积，合并去重

---

### Q18: 对话记忆压缩策略？

**回答要点**：
- 双触发：Token 阈值 4000 / 轮数 20
- LLM 生成摘要：保留用户需求、已确认信息、未解决问题、重要决策
- 保留最近 5 轮完整对话
- 压缩失败降级为简单摘要

---

### Q19: 执行轨迹（Trace）系统设计？

**回答要点**：
- `TraceRecorder`：采集门面，全部 try-catch 容错（绝不向主流程抛异常）
- `TraceContext`：请求级上下文，绑定 SseEmitter
- `TraceStreamPublisher`：实时 SSE 推送 trace 事件
- 10+ 种 StepType：SKILL_MATCH、NLU、ROUTING、PROFILE_INJECTION、ARTIFACT_QUERY、SUB_AGENT_EXECUTION、TOOL_CALL、MEMORY_COMPRESSION 等
- `TraceRepository`：持久化 + 单用户保留策略（超上限删最早）

---

### Q20: 快速路径（KeywordRouter）是怎么设计的？

**回答要点**：
- `containsCareerKeyword()`：检查消息是否包含职场关键词
- 不包含 → 直接走 GENERAL Agent（零 LLM 延迟）
- 包含 → `keywordRouteIntent()` 尝试规则匹配
- 规则命中 → 直接路由（confidence=1.0）
- 规则未命中 → 走完整 NLU Pipeline
- 效果：简单问候（"你好"）响应时间从 3-8s 降到 <100ms

---

## 四、场景设计类 Q&A

### Q21: 如何扩展一个新的 Agent？

**回答要点**：
1. 创建 Agent 类（继承 `BaseAgent`）
2. 创建 `AgentRunner` 实现（适配 V2 TaskExecutor）
3. 在 `AgentIntent` 枚举新增意图
4. 在 `NluIntent` 枚举新增细粒度意图
5. 在 `OrchestratorAgent` 的 switch 路由新增 case
6. 在 `TaskExecutor.setAgentRunners()` 注册 Runner
7. 编写 YAML 技能定义（可选）
8. 在 `AgentRegistry` 注册 AgentDescriptor（可选）

---

### Q22: 如何处理 LLM 幻觉？

**回答要点**：
- **QualityGuardAgent**：5 维评分，hallucinationScore 占 30%
- **RAG 检索增强**：基于知识库回答，减少幻觉
- **RED_TEAM 模式**：红队对抗审查
- **EvalCenter**：回归测试，发版前验证
- 改进方向：Reflexion 机制记录失败轨迹

---

### Q23: 如何做 A/B 测试？

**回答要点**：
- **PromptRegistry**：多版本 Prompt，按 trafficPercent 加权随机选择
- **WorkflowMatcher**：Rule / LLM / FALLBACK 三种匹配策略可切换
- **EvalCenter**：回归测试对比不同版本效果

---

### Q24: 如何保证多用户数据隔离？

**回答要点**：
- 会话按 userId 隔离（`SessionManager` 的 `chatOwner` 反向索引）
- 消息按 chatId 隔离（`PersistentMessageRepository`）
- 用户画像按 userId 隔离（`UserProfileRepository`）
- 交付物按 userId + scope 隔离（`ArtifactShelf`）
- 收藏按 userId 隔离（`FavoriteRepository`）
- 记忆按 userId + chatId 隔离（`MemoryCoordinator`）

---

### Q25: 系统的可观测性怎么保证？

**回答要点**：
- **Trace**：全链路执行轨迹，10+ 种 StepType，实时 SSE 推送
- **UsageTracker**：7 种事件类型，多维度统计
- **日志**：SLF4J + Logback，关键决策点有日志
- **质量审查**：HIGH/CRITICAL 自动持久化审计
- **事件总线**：异步治理事件（AccessDeniedEvent、SandboxExecEvent）

---

## 五、STAR 法则回答模板

### 亮点 1：四层记忆系统设计

- **S**：长对话上下文无限膨胀，Token 成本失控，用户体验差
- **T**：设计一个分层记忆系统，支持长期知识积累和语义检索
- **A**：四层架构（滑动窗口 + 事实存储 + 摘要 + 向量化经验），MemoryCoordinator 并行查询 + Token 预算分配 + 超时回退，ExtractionPipeline 异步提取
- **R**：Token 成本降低 40%，用户满意度提升，跨会话知识累积

### 亮点 2：NLU 快速路径优化

- **S**：简单问候（"你好"）也要走 NLU LLM 调用，3-8s 延迟
- **T**：优化简单消息的响应速度
- **A**：KeywordRouter 快速路径，规则匹配跳过 LLM；完整 NLU 单次 LLM 调用（非 2-3 次）
- **R**：简单消息响应时间从 3-8s 降到 <100ms，NLU 延迟减半

### 亮点 3：投票式安全访问控制

- **S**：Agent 权限、MCP 信任、调用配额三个维度需要统一管控
- **T**：设计一个灵活的访问决策机制
- **A**：投票式决策（一票否决），三个 Voter 独立评估，PermissionProfile 可配置
- **R**：安全事件零容忍，权限管理灵活可配置

### 亮点 4：全链路执行轨迹

- **S**：Agent 决策过程是黑盒，出问题难以排查
- **T**：实现全链路可观测
- **A**：TraceRecorder 采集门面（try-catch 容错），10+ 种 StepType，实时 SSE 推送，前端 TraceTimelineView 可视化
- **R**：问题定位时间从小时级降到分钟级

### 亮点 5：OrchestratorAgent 职责拆分

- **S**：OrchestratorAgent 500+ 行，God Class 问题
- **T**：降低复杂度，提高可测试性
- **A**：抽离 ContextInjectionService（上下文注入）、QualityReviewHandler（质量审查）、KeywordRouter（快速路由）
- **R**：主类从 800+ 行降到 536 行，各组件独立可测试

---

## 六、面试高频追问

### Q26: 为什么不直接用 LangChain？

> Spring AI 是 Spring 生态的 AI 框架，与 Spring Boot 集成更好（自动配置、依赖注入、AOP）。LangChain 是 Python 生态，Java 项目用 LangChain4j 也可以，但 Spring AI 的 ChatModel / ToolCallback / VectorStore 抽象更符合 Spring 开发习惯。

### Q27: 文件持久化能撑住多大并发？

> 当前是文件 + ReadWriteLock，适合单机万级会话。如果需要更高并发，可以：1）换 PostgreSQL；2）用 Redis 做缓存层；3）分片存储。项目设计了 Repository 接口，底层实现可替换。

### Q28: LLM 调用失败怎么办？

> 多层降级：1）OrchestratorAgent 捕获异常，返回"该专家暂时无法回答"；2）MemoryCoordinator 超时回退到 last-known-good 缓存；3）SkillExecutor 技能匹配失败降级到 NLU 路由；4）NLU 路由失败降级到 GENERAL Agent。

### Q29: 如何保证 Agent 不会陷入死循环？

> 三层防护：1）ReActAgent.maxIterations 限制（默认 10 次）；2）EmbeddingLoopDetector 余弦相似度检测（0.88 阈值）；3）TokenBudgetManager 三级预算控制。检测到循环后注入引导性消息（非简单终止），让 LLM 自主修正。

### Q30: 为什么选 DashScope 而不是 OpenAI？

> 1）国内访问稳定，无需翻墙；2）deepseek-v4-flash 性价比高；3）qwen 系列中文能力强；4）项目通过 Spring AI 的 ChatModel 抽象，换模型只需改配置。

### Q31: 如何评估 Agent 的回答质量？

> 1）QualityGuardAgent 5 维评分（准确性/完整性/逻辑性/幻觉风险/风险分）；2）EvalCenter YAML 评测套件 + 回归检测；3）路由评测（AgentRoutingEvalTest）验证意图路由准确率；4）用户反馈（收藏/用量追踪）。

### Q32: 项目最大的技术挑战是什么？

> 四层记忆系统的设计。挑战在于：1）四层并行查询的超时控制（每层 2000ms）；2）Token 预算分配（L1>L2>L3>L4）；3）提取管道的异步化（永不阻塞调用者）；4）FactStore v1→v2 迁移兼容。最终通过 CompletableFuture + TokenBudgetAllocator + ExtractionPipeline 解决。

### Q33: 如何处理 LLM 输出格式不稳定？

> 1）NLU 用结构化 Prompt（要求 JSON 输出）+ 正则提取；2）QualityGuardAgent 用 `indexOf('{')` 提取 JSON 降级方案；3）EvalCase 支持多种评分规则（EXACT_MATCH / SEMANTIC_SIMILARITY / LLM_JUDGE）；4）ToolCallAgent 解析 tool_calls 时做异常捕获。

### Q34: 如果要支持多语言怎么办？

> 1）Prompt 模板支持语言参数化；2）RAG 知识库按语言分文档；3）NLU Pipeline 的 AliasResolver 支持中英文别名；4）前端 i18n 国际化。当前项目以中文为主，但架构上没有硬编码中文的障碍。

### Q35: 如何做灰度发布？

> PromptRegistry 支持多版本 + trafficPercent 加权随机。WorkflowMatcher 支持 Rule/LLM/FALLBACK 三种策略切换。EvalCenter 回归测试验证新版本效果。可以先 10% 流量切到新 Prompt，观察 EvalReport 的 passRate 和 regression，再逐步放量。

---

## 七、新增功能相关 Q&A (v1.5)

### Q36: 性能评估框架是怎么设计的？

**回答要点**：
- **Actuator 集成**：Spring Boot Actuator + Micrometer + Prometheus
- **7 个核心指标**：
  - `agent_request_duration` (Timer) — 请求延迟 P50/P95/P99
  - `agent_tool_call_duration` (Timer) — 工具调用延迟
  - `agent_token_usage` (DistributionSummary) — Token 消耗
  - `agent_tool_call_total` (Counter) — 工具调用次数 (按 agent/tool/result 标签)
  - `agent_active_requests` (Gauge) — 当前活跃请求数
  - `agent_error_total` (Counter) — 错误计数
  - `agent_step_count` (DistributionSummary) — 每请求步数
- **自定义端点**：`/actuator/agent-metrics` 返回聚合指标
- **健康检查**：`AgentHealthIndicator` 检查活跃请求数和响应时间
- **压测脚本**：k6 渐进式负载 + Shell 简易压测

**追问**：为什么选 Micrometer 而不是自建指标系统？
> Micrometer 是 Spring Boot Actuator 的标准指标库，与 Prometheus/Grafana 生态无缝集成。自建系统需要额外维护成本，且无法直接接入现有的监控体系。Micrometer 支持 Timer/Counter/Gauge/DistributionSummary 等多种指标类型，满足 Agent 监控需求。

---

### Q37: 经典范式支持是怎么设计的？

**回答要点**：
- **三种范式**：
  - **REACT**：Think → Act → 循环，适合交互式任务
  - **PLAN_AND_SOLVE**：规划 → 执行 → 验证，适合复杂多步骤任务
  - **REFLECTION**：生成 → 评估 → 反思 → 修正，适合高质量输出
- **范式选择器**：`ParadigmSelector` 基于 NLU 意图 + 关键词启发式自动选择
- **组件结构**：
  - `AgentParadigm` — 范式枚举
  - `ParadigmSelector` — 智能选择器
  - `BaseParadigmAgent` — 抽象基类
  - `PlanAndSolveAgent` — Plan-and-Solve 实现
  - `ReflectionAgent` — Reflection 实现
  - `ParadigmAgentFactory` — 工厂模式
  - `ParadigmService` — 高层 API

**追问**：范式选择的准确率怎么保证？
> 当前是规则 + 关键词启发式，不调用 LLM。准确率通过以下方式保证：1）NLU 意图映射（DATA_QUERY → PLAN_AND_SOLVE）；2）关键词匹配（"分析"/"对比" → PLAN_AND_SOLVE）；3）默认 REACT（最通用）。未来可加入用户反馈循环，动态调整选择策略。

---

### Q38: Plan-and-Solve 范式的执行流程？

**回答要点**：
- **三阶段执行**：
  1. **Planning Phase**：LLM 生成结构化计划（JSON 格式，3-7 个步骤）
  2. **Execution Phase**：按计划逐步执行，记录每步结果
  3. **Verification Phase**：LLM 验证执行结果，检查完整性
- **进度跟踪**：`buildProgressSummary()` 构建已执行步骤摘要
- **容错设计**：计划解析失败时降级为单步直接执行
- **Trace 集成**：每个阶段和步骤都有 TraceSpan 记录

**追问**：如果计划执行到一半失败了怎么办？
> 1）记录已执行步骤的结果；2）返回部分完成的结果；3）Verification Phase 会识别缺失步骤；4）用户可基于部分结果继续或重新规划。不会丢失已执行的工作。

---

### Q39: Reflection 范式的迭代优化流程？

**回答要点**：
- **四阶段迭代**：
  1. **Generation**：生成初始响应
  2. **Evaluation**：5 维评分（准确性/完整性/清晰度/相关性/质量）
  3. **Reflection**：识别改进点（按优先级排序）
  4. **Revision**：应用改进，生成优化版本
- **迭代控制**：最多 2 次迭代，评分 ≥ 8.0 提前终止
- **评分解析**：从 LLM 输出提取 `overall_score`
- **成本权衡**：额外 LLM 调用换取质量提升，适合高价值输出

**追问**：Reflection 的额外 LLM 调用成本怎么控制？
> 1）设置最大迭代次数（默认 2 次）；2）评分阈值提前终止（≥ 8.0）；3）仅对高质量需求场景启用（如简历、Offer 评估）；4）通过 ParadigmSelector 按任务类型自动选择，普通对话不走 Reflection。

---

### Q40: 上下文工程优化做了哪些改进？

**回答要点**：
- **相关性评分**：`ContextRelevanceScorer` 按关键词重叠 + 密度对记忆项评分排序
- **动态预算分配**：`DynamicBudgetAllocator` 根据查询类型调整 Token 预算
  - CONVERSATIONAL：L1=75%, L2=10%, L3=10%, L4=5%
  - FACTUAL：L1=40%, L2=35%, L3=10%, L4=15%
  - ANALYTICAL：L1=50%, L2=15%, L3=15%, L4=20%
- **关键信息提取**：`KeyInfoExtractor` 提取实体/主题/意图
- **集成服务**：`ContextEngineer` 统一入口

**追问**：动态预算分配的依据是什么？
> 基于查询类型：1）事实性查询（"谁"/"什么"）优先 L2 事实层；2）分析性查询（"分析"/"对比"）优先 L4 经验层；3）对话性查询优先 L1 滑动窗口。通过关键词启发式分类，不调用 LLM。

---

### Q41: 工具注册机制是怎么设计的？

**回答要点**：
- **动态注册表**：`ToolRegistry` 支持运行时注册/注销工具
- **工具元数据**：`ToolDefinition` 包含名称/描述/能力标签/健康状态
- **能力发现**：工具名自动推断能力标签（search/file/web/terminal/document）
- **自动发现**：`ToolDiscovery` 从 Spring Context 自动注册 ToolCallback Bean
- **健康监控**：支持 HEALTHY/DEGRADED/UNHEALTHY 状态管理

**追问**：和原来的 ToolRegistration 有什么区别？
> 原来是静态 @Bean 注册，所有工具硬编码。新机制支持：1）运行时动态注册/注销；2）按能力标签查找工具；3）工具健康状态管理；4）自动从 Spring Context 发现。更灵活，支持工具市场场景。

---

### Q42: 如何用范式选择器优化不同任务？

**回答要点**：
- **自动选择**：`ParadigmSelector.select(intent, message, confidence)`
  - DATA_QUERY / CAREER_ADVICE → PLAN_AND_SOLVE
  - CONTENT_GENERATION / SKILL_ASSESSMENT → REFLECTION
  - 其他 → REACT
- **用户指定**：`ParadigmService.executeWithParadigm(message, "reflection", userId)`
- **集成点**：在 OrchestratorAgent 中注入 ParadigmService，根据 NLU 结果选择范式

**追问**：什么时候用 REACT，什么时候用 PLAN_AND_SOLVE？
> REACT 适合需要实时调整的任务（工具调用、交互式问答），边想边做。PLAN_AND_SOLVE 适合复杂多步骤任务（职业规划、简历优化），先规划再执行。关键区别：REACT 灵活但可能循环，PLAN_AND_SOLVE 结构化但不够灵活。

---

### Q43: 性能指标如何接入 Grafana 监控？

**回答要点**：
- **Prometheus 端点**：`/actuator/prometheus` 暴露标准格式指标
- **Grafana 配置**：
  1. 添加 Prometheus 数据源（http://localhost:8123/actuator/prometheus）
  2. 创建 Dashboard，添加 Panel 查询 `agent_request_duration_seconds_*`
  3. 设置告警规则（如 P95 > 5s 告警）
- **关键监控面板**：
  - 请求延迟趋势（P50/P95/P99）
  - 工具调用成功率
  - Token 消耗分布
  - 活跃请求数

**追问**：如何设置性能告警？
> Grafana 支持基于 PromQL 的告警规则。例如：1）`histogram_quantile(0.95, rate(agent_request_duration_seconds_bucket[5m])) > 5` 触发 P95 延迟告警；2）`rate(agent_error_total[5m]) / rate(agent_request_duration_seconds_count[5m]) > 0.1` 触发错误率告警。

---

## 八、代码审查维度相关 Q&A

### Q44: 如何保证 Agent 不会陷入死循环？（代码审查维度四）

**回答要点**：
- **三层防护**：
  1. `ReActAgent.maxIterations` 限制（默认 10 次）
  2. `EmbeddingLoopDetector` 余弦相似度检测（0.88 阈值，滑动窗口 5 条）
  3. `TokenBudgetManager` 三级预算控制（Normal/Compact/Compress）
- **引导性干预**：检测到循环后注入纠错提示，让 LLM 自主修正，而非简单终止
- **ToolResultClassifier**：分级处理工具结果（TIMEOUT/EMPTY/GARBAGE/NORMAL）

---

### Q45: 工具调用超时怎么处理？（代码审查维度四）

**回答要点**：
- **超时机制**：`CompletableFuture.orTimeout(30, TimeUnit.SECONDS)`
- **自动重试**：超时后自动重试 2 次（`MAX_TIMEOUT_RETRIES`）
- **降级策略**：重试用尽后返回友好错误信息
- **Trace 记录**：超时事件记录到 TraceSpan
- **结果分级**：`ToolResultClassifier` 将超时标记为 TIMEOUT 级别

---

### Q46: 记忆系统的并发安全怎么保证？（代码审查维度三）

**回答要点**：
- **ReadWriteLock**：`SummaryLayer` 使用读写锁保证并发安全
- **ConcurrentHashMap**：`MemoryCoordinator.layerCache` 使用并发容器
- **CompletableFuture**：四层并行查询，超时独立控制
- **last-known-good 缓存**：层查询失败时使用缓存的上次成功值
- **userId 隔离**：所有记忆按 userId 隔离，无跨用户泄漏

---

### Q47: 如何评估 Agent 的回答质量？（代码审查维度六）

**回答要点**：
- **QualityGuardAgent**：4 种模式（OFF/AUTO/REVIEW/RED_TEAM）
- **5 维评分**：accuracy(30%) + completeness(20%) + logic(20%) + hallucination(30%) + risk
- **EvalCenter**：YAML 评测套件 + 回归检测
- **用户反馈**：Feedback 模型（UP/DOWN + comment）
- **路由评测**：AgentRoutingEvalTest 验证意图路由准确率

---

### Q48: RAG 检索冲突怎么处理？（代码审查维度七）

**回答要点**：
- **MultiQueryRetriever**：多路检索后合并去重
- **相似度阈值**：可配置的相似度过滤
- **状态过滤**：按文档状态分类（求职/在职/通用）
- 改进方向：时间权重 / 权威度排序 / 重排序模型

---

### Q49: Prompt 注入怎么防护？（代码审查维度八）

**回答要点**：
- **PromptInjectionDetector**：3 类 15 个 Pattern 检测
  - Override：覆盖系统指令
  - Hijack：劫持对话流程
  - Extraction：提取系统信息
- **检测位置**：OrchestratorAgent 入口处，NLU 之前
- **处理策略**：检测到注入后拒绝执行，返回安全提示

---

### Q50: 如何保证可观测性？（代码审查维度九）

**回答要点**：
- **Trace**：全链路执行轨迹，10+ 种 StepType，实时 SSE 推送
- **Metrics**：Actuator + Micrometer，7 个核心指标
- **日志**：SLF4J + Logback，关键决策点有日志
- **事件总线**：异步治理事件（AccessDeniedEvent、SandboxExecEvent）
- **质量审查**：HIGH/CRITICAL 自动持久化审计

---

### Q51: Reflexion 机制是怎么设计的？

**回答要点**：
- **核心思想**：Agent 从失败中学习，避免重复犯错
- **组件结构**：
  - `ReflexionMemory` — 失败轨迹存储（per-user + global）
  - `ReflexionService` — 集成服务
- **数据模型**：FailureMemory(id, taskType, error, resolution, timestamp)
- **核心功能**：
  - 失败记录：taskType + error + resolution
  - 相关性检索：按 taskType 匹配历史失败
  - 提示词注入：格式化为"历史失败经验"
  - 自动过期：7 天后自动清理

**追问**：Reflexion 和普通的错误处理有什么区别？
> 普通错误处理只处理当前错误，Reflexion 记录失败轨迹供未来参考。当 Agent 遇到类似任务时，会自动注入历史失败经验到提示词，让 LLM 避免重复犯错。这是一种"学习"机制，而非简单的"容错"。

---

### Q52: RAG Rerank 重排序是怎么做的？

**回答要点**：
- **评分策略**：
  - 关键词重叠 (60%)：Jaccard 相似度 + 查询覆盖率
  - 文档质量 (20%)：长度适中 + 结构化程度
  - 位置偏差 (20%)：原始检索顺序
- **使用场景**：检索后重排序，提升 Top-K 文档相关性
- **组件**：`RerankService`

**追问**：为什么不用 Cohere Rerank 或 BGE Reranker？
> 当前是轻量级实现，不依赖外部 API。生产环境建议集成专业 rerank 模型（如 Cohere Rerank API 或 BGE Reranker），准确率更高。架构上 `RerankService` 是接口，可轻松替换实现。

---

### Q53: 范式选择器怎么集成到现有系统？

**回答要点**：
- **集成点**：`OrchestratorAgent` 注入 `ParadigmService`
- **选择逻辑**：
  1. NLU 意图映射：DATA_QUERY → PLAN_AND_SOLVE
  2. 关键词启发式："分析"/"对比" → PLAN_AND_SOLVE
  3. 默认 REACT
- **配置化**：`paradigm.selector.enabled` 开关
- **用户指定**：支持用户手动指定范式

**追问**：什么时候用 REACT，什么时候用 PLAN_AND_SOLVE？
> REACT 适合交互式任务（工具调用、问答），边想边做。PLAN_AND_SOLVE 适合复杂任务（规划、分析），先规划再执行。关键区别：REACT 灵活但可能循环，PLAN_AND_SOLVE 结构化但不够灵活。

---

### Q54: 动态预算分配的依据是什么？

**回答要点**：
- **查询类型分类**：
  - CONVERSATIONAL：L1=75%, L2=10%, L3=10%, L4=5%
  - FACTUAL：L1=40%, L2=35%, L3=10%, L4=15%
  - ANALYTICAL：L1=50%, L2=15%, L3=15%, L4=20%
- **分类依据**：关键词启发式（"谁"/"什么" → FACTUAL）
- **集成点**：`ContextEngineer` 统一入口

**追问**：为什么不直接用 LLM 分类查询类型？
> 1）节省 LLM 调用成本；2）关键词启发式足够准确；3）分类错误影响不大（只是预算分配，不影响功能）。未来可加入 LLM 分类作为高级选项。
