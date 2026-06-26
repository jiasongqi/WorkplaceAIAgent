# WorkPilot 项目功能 Wiki

> 更新日期：2026-06-26 (v1.5 — Hello-Agents 优化完成)  
> 品牌名：WorkPilot（职场生存智囊）  
> 技术底座：Java 21 + Spring Boot 3.4 + Spring AI 1.0 + Vue 3 + DashScope

---

## 目录

- [一、产品定位](#一产品定位)
- [二、系统架构总览](#二系统架构总览)
- [三、核心功能模块](#三核心功能模块)
- [四、Agent 体系](#四agent-体系)
- [五、NLU 意图理解管道](#五nlu-意图理解管道)
- [六、知识与记忆系统](#六知识与记忆系统)
- [七、工具与执行](#七工具与执行)
- [八、协作与产出](#八协作与产出)
- [九、安全与治理](#九安全与治理)
- [十、前端页面](#十前端页面)
- [十一、API 接口速查](#十一api-接口速查)
- [十二、数据存储](#十二数据存储)
- [十三、配置清单](#十三配置清单)
- [十四、评测与质量](#十四评测与质量)
- [十五、Agent 注册与 Prompt 管理](#十五agent-注册与-prompt-管理)

---

## 一、产品定位

WorkPilot 是一个全场景职场 AI 智囊平台，覆盖职场人从求职到离职的全生命周期：

| 场景 | 能力 | 负责 Agent |
|------|------|-----------|
| 求职准备 | 简历优化、面试模拟、STAR 法则 | ResumeAgent |
| 薪资谈判 | 市场薪资调研、谈薪策略、Offer 评估 | NegotiationAgent |
| 离职规划 | 离职信撰写、交接清单、劳动权益 | EscapeAgent |
| 预约咨询 | 信息收集追问、企业日历创建 | ConsultationAgent |
| 通用职场 | 人际关系、压力管理、职业规划 | GeneralCareerAgent |
| 复杂任务 | 联网搜索、PDF 生成、代码执行 | YuManus (超级智能体) |
| 沟通助手 | 情感顾问、恋爱问题解答 | LoveMaster（隐藏路由） |

---

## 二、系统架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend (Vue 3)                         │
│  Home · CareerAdvisor · SuperAgent · Knowledge · Artifacts ...   │
│  Favorites · Usage · TraceDetail · CompareView · LoveMaster      │
└───────────────────────────────┬─────────────────────────────────┘
                                │ SSE / REST (JWT Auth)
┌───────────────────────────────┼─────────────────────────────────┐
│                         API Layer                                │
│  AiController · SessionController · DocumentController · ...     │
├───────────────────────────────┼─────────────────────────────────┤
│                      AppService Layer                            │
│  OrchestratorAppService · SessionAppService · FavoriteAppService │
├───────────────────────────────┼─────────────────────────────────┤
│                      Agent Layer (Core)                          │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  OrchestratorAgent (主控)                                │    │
│  │    ├─ KeywordRouter (快速路径，零LLM)                     │    │
│  │    ├─ NluPipeline (意图理解，1次LLM)                      │    │
│  │    ├─ SkillExecutor (技能匹配)                            │    │
│  │    ├─ WorkflowMatcher (工作流匹配)                        │    │
│  │    ├─ TaskExecutor (V2任务执行)                           │    │
│  │    ├─ ResultAggregator (结果聚合)                         │    │
│  │    ├─ ContextInjectionService (上下文注入)                │    │
│  │    └─ QualityReviewHandler (质量审查)                     │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │  专业子 Agent: Resume · Negotiation · Escape · General   │    │
│  │  AgentRunner 适配层: ResumeAgentRunner · Negotiation...  │    │
│  │  数据员工: DataAnalyst · CareerCoach · ProfileCurator     │    │
│  └─────────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────────┤
│                     Infrastructure Layer                         │
│  ChatMemory · VectorStore · Trace · Sandbox · Access Control     │
│  Artifact · UserProfile · EventBus · Quality Guard               │
│  MemoryCoordinator · EvalCenter · AgentRegistry · PromptRegistry │
└─────────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层 | 技术 |
|----|------|
| 后端框架 | Java 21, Spring Boot 3.4, Spring AI 1.0 |
| AI 模型 | DashScope (deepseek-v4-flash / qwen 系列), Ollama (本地) |
| 向量数据库 | PgVector / 内存 SimpleVectorStore |
| 流式通信 | SSE (SseEmitter + Reactor Flux) |
| 前端 | Vue 3, Vite, Vue Router, Axios, marked.js |
| 序列化 | Jackson (JSON), Kryo (ChatMemory 高性能) |
| 安全 | JWT, 投票式访问决策, MCP 信任分级 |

---

## 三、核心功能模块

### 3.1 基础对话 (L0)

| 能力 | 入口 | 说明 |
|------|------|------|
| 同步对话 | `GET /ai/ai_chat/chat/sync` | 单次请求-响应 |
| SSE 流式 | `GET /ai/ai_chat/chat/sse` | token 级实时推送 |
| SseEmitter | `GET /ai/ai_chat/chat/sse_emitter` | Spring MVC 原生 |
| 对话记忆 | 自动 | Kryo 序列化持久化，按 chatId 隔离 |

### 3.2 RAG 知识库 (L1)

- 内置 11 篇职场文档（求职、在职、晋升、离职、谈薪、钝感力等）
- Multi-Query 多路召回 + QueryRewriter 查询改写
- 按文档状态分类过滤（求职/在职/通用）
- 支持动态上传文档实时入库

### 3.3 技能系统 (L9)

YAML 声明式技能，启动时热加载：

| 技能 | 触发关键词 | 功能 |
|------|-----------|------|
| interview-prep | 面试、求职 | 面试问题预测 + STAR 回答模板 |
| salary-research | 薪资、谈薪、待遇 | 市场薪资分析报告 |
| resignation-letter | 离职、辞职、交接 | 离职申请 + 交接清单生成 |

技能优先级高于意图路由，命中即直接作答（零 LLM 路由延迟）。

### 3.4 预约咨询 (L6)

状态机驱动的多轮信息收集：

```
INITIAL → COLLECTING_INFO → CONFIRMING → CREATING_APPOINTMENT → COMPLETED
```

- 自然语言智能提取（"我叫小琪" → 姓名: 小琪）
- 对接飞书/钉钉企业日历
- 确认阶段可自由提问，LLM 回答后继续引导

### 3.5 会话管理

- 三态生命周期：ACTIVE → ARCHIVED → DELETED（软删除）
- 会话归档/恢复/回收站
- 跨会话搜索（加权评分 + 时间衰减）
- 消息历史持久化（ULID 有序 ID）

### 3.6 收藏系统 (L11)

- 消息快照收藏，原消息删除后依然保留
- 会话删除时自动标记 orphan
- 独立管理，支持增删查

### 3.7 数据导入导出 (L13)

- ZIP 全量备份（sessions + messages + favorites）
- 导入时自动处理 chatId 冲突
- 一键下载 / 上传恢复

### 3.8 用量追踪 (L12)

7 种事件类型：CHAT、RAG、TOOL_CALL、DOCUMENT_UPLOAD、EXPORT、COMPARE、QUALITY_REVIEW

统计维度：总事件数、按类型/Agent 分组、近 7 天日趋势、总耗时。

### 3.9 推理效率追踪 (新增)

`AgentEfficiencyTracker` 追踪每个 Agent 的推理效率：

| 指标 | 说明 |
|------|------|
| avgSteps | 平均执行步数（越少越好） |
| avgTokens | 平均 Token 消耗 |
| avgToolCalls | 平均工具调用次数 |
| completionRate | 任务完成率 |
| avgLatencyMs | 平均延迟 |

### 3.10 用户反馈系统 (新增)

- `Feedback` 模型：userId、chatId、messageId、rating(UP/DOWN)、comment、agentType、intent
- `FeedbackRepository`：文件持久化 + 统计（approvalRate per agent）
- `FeedbackController`：POST /feedback（提交）、GET /feedback/stats（统计）

---

## 四、Agent 体系

### 4.1 主控编排 (OrchestratorAgent)

```
用户消息
  │
  ├─ Prompt Injection 检测（PromptInjectionDetector）
  │
  ├─ KeywordRouter: 快速路径（零LLM，规则匹配）
  │     ├─ 命中 → 直接路由到对应 Agent
  │     └─ 未命中 → 走 NLU Pipeline
  │
  ├─ SkillExecutor: 技能匹配（命中则直答）
  │
  ├─ NluPipeline: 意图理解（1次LLM）
  │     ├─ 需要澄清 → 返回追问
  │     └─ 明确意图 → 路由
  │
  ├─ ContextInjectionService: 上下文注入
  │     ├─ 用户画像注入
  │     ├─ 交付物注入
  │     ├─ 跨 Agent 历史注入
  │     └─ L27 分层记忆注入
  │
  ├─ DynamicPromptProvider: 动态 System Prompt
  │     └─ 根据 intent 选择最优 prompt 模板
  │
  ├─ 单意图: 分发给对应 Agent
  │
  ├─ 多意图: 串行执行 + agent-turn SSE 事件
  │
  ├─ QualityReviewHandler: 质量审查（异步）
  │
  ├─ AgentEfficiencyTracker: 推理效率追踪
  │
  └─ MemoryCoordinator.onTurnCompleted(): 触发记忆提取
```

**关键设计**：
- **快速路径**：`KeywordRouter.containsCareerKeyword()` 判断是否走 NLU，简单问候/模糊消息直接走 GENERAL Agent，避免 3-8s DashScope 延迟
- **职责拆分**：`ContextInjectionService`（上下文注入）、`QualityReviewHandler`（质量审查）从 OrchestratorAgent 抽离，降低 God Class 复杂度
- **V2 桥接**：`AgentRunner` 适配层将 V1 Agent 包装为 V2 `TaskExecutor` 可消费的 Runner

### 4.2 专业 Agent

| Agent | 特色能力 | RAG | 工具调用 |
|-------|---------|-----|---------|
| ResumeAgent | 求职文档 RAG + 查询改写 | ✅ | ❌ |
| NegotiationAgent | 联网搜索市场薪资 | ❌ | ✅ |
| EscapeAgent | PDF 交接清单生成 | ❌ | ✅ |
| GeneralCareerAgent | 温暖共情 + 职业规划 | ❌ | ❌ |
| ConsultationAgent | 状态机追问 + 日历 | ❌ | ✅ |

### 4.3 AgentRunner 适配层 (`agent/runner/`)

将 V1 Agent 适配为 V2 `AgentRunner` 接口，供 `TaskExecutor` 统一调度：

| Runner | 适配 Agent |
|--------|-----------|
| `ResumeAgentRunner` | ResumeAgent |
| `NegotiationAgentRunner` | NegotiationAgent |
| `EscapeAgentRunner` | EscapeAgent |
| `GeneralCareerAgentRunner` | GeneralCareerAgent |

### 4.4 超级智能体 (YuManus)

ReAct 自主规划型 Agent，支持自主拆解复杂任务：

```
BaseAgent → ReActAgent（思考-行动循环）→ ToolCallAgent → YuManus
```

### 4.5 数据员工 Agent

黑板模式协作，异步产出交付物：

| 数据员工 | 产出 |
|---------|------|
| DataAnalystAgent | 数据分析报告 |
| CareerCoachAgent | 岗位辅导方案 |
| ProfileCuratorAgent | 用户画像整理 |
| PromotionPlannerAgent | 晋升路径规划 |
| LearningResourceRecommenderAgent | 学习资源推荐 |

**数据模型**：
- `ProductionContext` — 执行上下文（userId、chatId、source、memoryAgentType、documentContent）
- `ProductionResult` — 加工结果（success、artifact、errorMessage）
- `AnalysisReport` — 结构化分析报告（summary、keyFindings、metrics、recommendations）
- `AnalysisSource` — 输入来源枚举（CONVERSATION / UPLOADED_DOCUMENT）

### 4.6 质量守护 (QualityGuardAgent)

- 模式：OFF / AUTO / REVIEW（单次审查）/ RED_TEAM（红队对抗）
- 5 维评分：准确性(30%) + 完整性(20%) + 逻辑性(20%) + 幻觉风险(30%) + 风险分
- HIGH/CRITICAL 审查自动持久化审计

---

## 五、NLU 意图理解管道

```
用户消息
  │
  ├─ KeywordRouter 快速路径（零LLM）
  │     ├─ containsCareerKeyword() = false → GENERAL
  │     └─ keywordRouteIntent() 命中 → 直接路由
  │
  └─ NluPipeline 完整路径（1次LLM）
        → AliasResolver（别名元数据，不改原文）
        → UnifiedNluExtractor（1次LLM：intent排名 + slots + domain + action）
        → IntentReranker（alias domain re-rank）
        → IntentAmbiguityDetector（同类意图检测）
        → RouteTemplate（点分记法路由）
        → ContextShiftDetector（3态：FOLLOW_UP/ENTITY_SWITCH/NEW_QUERY）
        → ConversationState.smartMerge
        → IntentRequirementRegistry（槽位需求校验）
        → ClarificationHandler（模板追问，零LLM）
        → NluResult
```

关键设计决策：
- 快速路径：简单消息跳过 NLU LLM 调用，避免 3-8s 延迟
- 1 次 LLM 调用完成全部意图理解（延迟减半）
- 别名只输出元数据（不改原文）
- Confidence = Top1-Top2 差值
- 澄清用模板（零额外 LLM）
- `DataQueryRouter` 透传 slots，不调 LLM

---

## 六、知识与记忆系统

### 6.1 RAG 检索链

```
用户问题 → QueryRewriter → MultiQueryRetriever（3路扩展）→ VectorStore → 合并去重 → LLM
                     ↓
              HyDERetriever（假设文档嵌入）→ 生成假设答案 → 用答案做向量检索
```

**HyDE（Hypothetical Document Embedding）**：先让 LLM 生成一个"假设答案"，用这个答案去做向量检索，比用问题检索更准——因为假设答案与实际文档共享更多语义相似性。

**RagTool**：RAG 解耦为可复用 Tool，任何 Agent 都可调用（不再硬编码在 ResumeAgent 里）。支持相似度阈值、topK、状态过滤、HyDE 开关。

### 6.2 对话记忆

- ChatMemoryManager 统一管理（按 agent 类型隔离）
- FileBasedChatMemory (Kryo 高性能序列化)
- 压缩策略：Token 阈值 4000 / 轮数 20 → LLM 生成摘要，保留最近 5 轮

### 6.3 用户画像

- 对话结束后 LLM 异步抽取画像维度
- 维度：沟通偏好、语气偏好、关注领域、已知背景、历史诉求
- 画像注入到每次对话的 system prompt（上限 1000 字符）
- 跨会话累积，合并去重

### 6.4 分层记忆系统（L27 MemoryCoordinator）

四层记忆架构，`MemoryCoordinator` 作为唯一入口，并行查询 + Token 预算裁剪：

```
用户消息
  │
  ▼
MemoryCoordinator.assembleContext(userId, chatId, agentType)
  │
  ├─ CompletableFuture 并行查询四层（超时 2000ms 回退）
  │     ├─ L1 SlidingWindowLayer — 当前会话最近 N 条完整消息
  │     ├─ L2 FactStoreLayer — 结构化事实（身份/偏好/目标，键值对）
  │     ├─ L3 SummaryLayer — 近期对话要点清单（话题/决策/待办）
  │     └─ L4 ExperienceStoreLayer — 历史经验案例（向量化语义检索）
  │
  ├─ TokenBudgetAllocator — 按优先级分配预算（L1 > L2 > L3 > L4）
  │
  └─ 组合为 SystemMessage 注入对话
```

| 组件 | 职责 |
|------|------|
| `MemoryCoordinator` | 统一入口，并行查询 + 超时回退 + last-known-good 缓存 |
| `SlidingWindowLayer` (L1) | 当前会话滑动窗口，保持连贯性 |
| `FactStoreLayer` (L2) | 结构化用户事实，精确匹配（含迁移兼容） |
| `SummaryLayer` (L3) | 对话摘要要点清单（FIFO 淘汰 + checklist） |
| `ExperienceStoreLayer` (L4) | 历史经验向量化存储，语义模糊匹配 |
| `TokenBudgetAllocator` | 四层预算分配（优先级裁剪） |
| `ExtractionPipeline` | 对话完成后异步提取事实/摘要/经验（单次 LLM） |
| `MemoryLayer` | 层级枚举（SLIDING_WINDOW / FACT_STORE / SUMMARY / EXPERIENCE） |
| `ContextWindow` | 上下文窗口记录 |

**提取管道**：对话完成后 `ExtractionPipeline` 异步运行，单次 LLM 调用同时提取事实、摘要和经验，写入对应层。永不阻塞调用者，所有异常内部捕获。

**容错设计**：每层查询超时独立（默认 2000ms），失败时使用 `layerCache` 中的 last-known-good 数据。总预算 6000 tokens，按 L1→L4 优先级递减分配。

**集成点**：`OrchestratorAgent` 在每次对话完成后调用 `memoryCoordinator.onTurnCompleted()` 触发提取管道。

**程序性记忆（ProceduralMemory）**：记录工具调用模式（成功率/延迟/意图关联），让 Agent 逐渐学会用户习惯。支持按用户/全局统计，可推荐最优工具。

**事实保留压缩（FactPreservingCompressor）**：压缩对话历史时自动提取并保留用户关键事实（姓名/联系方式/公司/职位/偏好），确保压缩不丢失用户明确说过的信息。

---

## 七、工具与执行

### 7.1 内置工具

| 工具 | 类 | 说明 |
|------|----|------|
| WebSearchTool | 联网搜索 | SearchAPI 驱动 |
| WebScrapingTool | 网页抓取 | Jsoup 正文提取 |
| FileOperationTool | 文件读写 | 本地文件操作 |
| ResourceDownloadTool | 资源下载 | HTTP 资源获取 |
| TerminalOperationTool | 终端命令 | Shell 执行 |
| PDFGenerationTool | PDF 生成 | iText + 亚洲字体 |
| TerminateTool | 终止标记 | Agent 主动结束 |

### 7.2 MCP 外部服务

- kiro-image-search-mcp-server（图片搜索）
- 支持 SSE + stdio 两种连接方式
- MCP 信任分级（VERIFIED/PARTNER/COMMUNITY/PRIVATE）

### 7.3 沙箱执行 (L19)

| 策略 | 说明 |
|------|------|
| UNSANDBOXED | 安全工具直接执行 |
| PROCESS_SANDBOX | 本地进程 5 层防护 |
| DOCKER_SANDBOX | Docker 容器完全隔离 |

### 7.4 工作流引擎 (L18)

6 种节点类型：AgentNode、ToolNode、ConditionNode、ParallelNode、LoopNode、ApprovalNode

---

## 八、协作与产出

### 8.1 交付物货架（Blackboard Pattern）

```
Agent ──put(READY)──► ArtifactShelf ──query/get──► 下游 Agent / 前端
```

- 作用域：USER_PROFILE（跨会话）/ TASK（会话级）
- 生命周期：DRAFT → REVIEWING → APPROVED → PUBLISHED → ARCHIVED

### 8.2 交付物生命周期管理 (L24)

`ArtifactLifecycleManager` 控制状态流转合法性并记录审计事件：

```
DRAFT → REVIEWING（提交审核）
REVIEWING → APPROVED（审核通过）
REVIEWING → DRAFT（审核拒绝，含原因）
APPROVED → PUBLISHED（发布）
任意 → ARCHIVED（归档）
```

- 合法性校验 + 旧状态兼容（PENDING→DRAFT, READY→APPROVED）
- `ArtifactLifecycleEvent` 审计事件记录

### 8.3 执行轨迹 (Trace)

- 全链路记录：10+ 种 StepType
- 实时 SSE 推送 trace 事件
- REST API 查询（按 traceId/chatId/userId）
- 前端 TraceTimelineView 时间线可视化

---

## 九、安全与治理

### 9.1 认证授权

- JWT 无状态认证
- SSE 接口 token 走 URL 参数（EventSource 限制）
- 游客模式自动登录

### 9.2 访问控制 (L20)

投票式决策（一票否决）：
- AgentPolicyVoter — Agent 权限画像
- McpPolicyVoter — MCP 信任等级
- QuotaPolicyVoter — 调用配额

### 9.3 安全防护 (L26)

| 组件 | 功能 |
|------|------|
| EmbeddingLoopDetector | 循环检测（余弦相似度 0.88） |
| ToolResultClassifier | 工具结果分级（TIMEOUT/EMPTY/GARBAGE/NORMAL） |
| TokenBudgetManager | Token 预算分级（Normal/Compact/Compress） |
| PromptInjectionDetector | Prompt 注入检测（Override/Hijack/Extraction 三类 15 个 Pattern） |
| ProceduralMemory | 程序性记忆（工具调用模式追踪，成功率/延迟/意图关联） |
| McpAuditLog | MCP 工具调用审计日志（环形缓冲 1000 条） |

### 9.4 事件总线 (L25)

异步治理事件发布，不阻塞主流程：AccessDeniedEvent、SandboxExecEvent

---

## 十、前端页面

| 页面 | 路由 | 说明 |
|------|------|------|
| 工作台 | `/` | 温暖首页，快捷场景入口 |
| 职场顾问 | `/chat/career` | 多 Agent 智能对话（主页面） |
| 超级智能体 | `/chat/super` | Manus 独立界面 + 执行进度 |
| 知识库 | `/knowledge` | 文档管理（上传/删除） |
| 交付物 | `/artifacts` | 交付物浏览 |
| 收藏 | `/favorites` | 收藏消息管理 |
| 用量 | `/usage` | 使用统计仪表盘 |
| 轨迹详情 | `/trace/:traceId` | 执行轨迹时间线 |
| 管理后台 | `/admin` | 管理员视图 |
| Agent 对比 | `/compare` | Agent 对比测试 |
| 沟通助手 | `/love-master` | AI 恋爱大师（隐藏路由，向后兼容） |

### 前端特色功能

- 实时流式对话（token 级推送）
- 多 Agent 消息来源标识
- 语音输入（Web Speech API）
- 文件上传到 RAG
- 用户画像查看/清空
- 轨迹时间线实时展示
- 质量审查评分显示
- 交付物侧栏预览

---

## 十一、API 接口速查

| 分类 | 方法 | 路径 | 鉴权 |
|------|------|------|------|
| 智能路由对话 | GET | `/ai/orchestrator/chat` | JWT |
| Manus 超级智能体 | GET | `/ai/manus/chat` | - |
| 基础对话 | GET | `/ai/ai_chat/chat/sync\|sse\|sse_emitter` | - |
| RAG 对话 | GET | `/ai/ai_chat/rag/sync` | - |
| 工具对话 | GET | `/ai/ai_chat/tools/sync` | - |
| 结构化报告 | GET | `/ai/ai_chat/report/sync` | - |
| 文档管理 | POST/GET/DELETE | `/document/*` | - |
| 会话管理 | ALL | `/session/*` | JWT |
| 收藏 | POST/DELETE/GET | `/favorite/*` | JWT |
| 用户画像 | GET/DELETE | `/profile/me` | JWT |
| 交付物 | GET | `/artifact/*` | JWT |
| 轨迹查询 | GET | `/trace/*` | JWT |
| 用量统计 | GET | `/usage/stats` | JWT |
| 导入导出 | GET/POST | `/export/*` | JWT |
| 健康检查 | GET | `/health` | - |
| 用户反馈 | POST | `/feedback` | - |
| 反馈统计 | GET | `/feedback/stats` | - |

---

## 十二、数据存储

| 数据 | 存储方式 | 位置 |
|------|---------|------|
| 会话列表 | JSON | `./tmp/sessions/sessions.json` |
| 消息 | JSON (per chatId) | `./tmp/sessions/messages/{chatId}.json` |
| 对话记忆 | Kryo | `./tmp/chat-memory/{agent}/{chatId}.kryo` |
| 预约 | JSON | `./tmp/appointments/` |
| 交付物 | JSON | `./tmp/artifacts/artifacts.json` |
| 用户画像 | JSON | `./tmp/user-profiles/` |
| 收藏 | JSON | `./tmp/artifacts/favorites.json` |
| 质量审查 | JSON | `./tmp/artifacts/quality-reviews.json` |
| 用量事件 | JSON (append-only) | `./tmp/artifacts/usage-events.json` |
| 执行轨迹 | JSON | `./tmp/traces/` |
| 向量库 | PgVector / 内存 | PostgreSQL / SimpleVectorStore |
| 用户反馈 | JSON | `./tmp/feedback/feedback.json` |
| 程序性记忆 | 内存 | `ProceduralMemory`（ConcurrentHashMap） |
| MCP 审计日志 | 内存（环形缓冲） | `McpAuditLog`（最近 1000 条） |
| Agent 描述符 | YAML | `classpath:agents/*.yaml` |
| 权限画像 | YAML | `classpath:permissions/*.yaml` |
| 评测用例 | YAML | `classpath:eval/*.yaml` |

---

## 十三、配置清单

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `spring.ai.dashscope.chat.options.model` | deepseek-v4-flash | 主模型 |
| `server.port` | 8123 | 服务端口 |
| `jwt.secret` | 环境变量 | JWT 密钥 |
| `calendar.provider` | FEISHU | 日历服务商 |
| `chat.memory.compression.token-threshold` | 4000 | 压缩阈值 |
| `chat.memory.compression.turn-threshold` | 20 | 轮数阈值 |
| `sandbox.require-docker` | false | Docker 强制 |
| `agent.guard.loop-detector.similarity-threshold` | 0.88 | 循环检测阈值 |
| `workpilot.quality.model` | qwen3.7-plus | 质检模型 |
| `trace.max-spans-per-trace` | 200 | 轨迹 span 上限 |
| `memory.coordinator.enabled` | true | 分层记忆开关 |
| `memory.coordinator.timeout-ms` | 2000 | 记忆层查询超时 |
| `memory.coordinator.total-token-budget` | 6000 | 记忆总 Token 预算 |

---

## 十四、评测与质量

### 14.1 评测中心 (L22 EvalCenter)

Agent 质量评测框架，支持回归测试和发版评估：

```
评测流程：加载用例(YAML) → 调用 Agent → 评分 → 生成报告 → 检测回归
```

| 组件 | 职责 |
|------|------|
| `EvalCenter` | 评测服务，加载 YAML 评测套件，运行评测，生成报告 |
| `EvalCase` | 评测用例（caseId、input、expectedOutput、scoringRule、passThreshold） |
| `EvalReport` | 评测报告（overallScore、passRate、regression、caseResults） |

**评分规则**：EXACT_MATCH / SEMANTIC_SIMILARITY / LLM_JUDGE

**路由评测测试**：
- `AgentRoutingEvalTest` — 路由准确率 + 快速路径覆盖率 + 响应时间
- `FastPathRoutingTest` — 快速路径规则匹配验证

### 14.2 质量守护 (L10 QualityGuardAgent)

- 模式：OFF / AUTO / REVIEW / RED_TEAM
- 5 维评分：accuracyScore(30%) + completenessScore(20%) + logicScore(20%) + hallucinationScore(30%) + riskScore
- HIGH/CRITICAL 审查自动持久化审计
- `QualityReviewHandler` 从 OrchestratorAgent 抽离，独立处理质量审查逻辑

---

## 十五、Agent 注册与 Prompt 管理

### 15.1 Agent 注册中心 (L21)

YAML 声明式 Agent 描述符，支持 Agent Marketplace 场景：

| 组件 | 职责 |
|------|------|
| `AgentRegistry` | 注册中心接口 |
| `InMemoryAgentRegistry` | 内存实现 |
| `AgentDescriptor` | Agent 描述符（agentCode、capabilities、intentKeywords、permissionProfile） |

**加载方式**：从 `resources/agents/*.yaml` 加载，启动时自动注册。

**查询能力**：按编码获取、按能力标签查找、按意图关键词匹配。

### 15.2 Prompt 版本管理 (L23)

管理 Prompt 版本，支持灰度发布和 A/B 测试流量分配：

| 组件 | 职责 |
|------|------|
| `PromptRegistry` | 注册中心 |
| `PromptVersion` | 版本定义 |

**流量分配逻辑**：
- 只有 1 个 ACTIVE → 直接返回
- 多个 ACTIVE → 按 trafficPercent 加权随机选择
- 无 ACTIVE → 返回最新版本（降级）

**版本限制**：每个 promptKey 最多保留 50 个版本（MAX_VERSIONS_PER_KEY），超出自动清理最旧版本。

---

## 十六、性能评估与监控 (L28)

### 16.1 Actuator 指标监控

集成 Spring Boot Actuator + Micrometer，暴露 Prometheus 格式指标：

| Metric | 类型 | 说明 |
|--------|------|------|
| `agent_request_duration` | Timer | 请求延迟 (P50/P95/P99) |
| `agent_tool_call_duration` | Timer | 工具调用延迟 |
| `agent_token_usage` | DistributionSummary | Token 消耗 |
| `agent_tool_call_total` | Counter | 工具调用次数 (按 agent/tool/result 标签) |
| `agent_active_requests` | Gauge | 当前活跃请求数 |
| `agent_error_total` | Counter | 错误计数 |
| `agent_step_count` | DistributionSummary | 每请求步数 |

**访问端点**：
- `GET /actuator/health` — 健康检查
- `GET /actuator/agent-metrics` — 自定义指标
- `GET /actuator/prometheus` — Prometheus 格式

### 16.2 压测脚本

提供 k6 和 Shell 两种压测脚本：

```bash
# k6 压测 (推荐)
k6 run --vus 10 --duration 30s stress-test.js

# Shell 简易压测
./stress-test.sh 5 20
```

---

## 十七、经典范式支持 (L29)

### 17.1 范式选择器

根据任务特征自动选择最优推理范式：

| 范式 | 适用场景 | 执行流程 |
|------|----------|----------|
| **REACT** | 交互式任务、工具调用 | Think → Act → 循环 |
| **PLAN_AND_SOLVE** | 复杂多步骤任务 | 规划 → 执行 → 验证 |
| **REFLECTION** | 高质量输出任务 | 生成 → 评估 → 反思 → 修正 |

### 17.2 范式组件

| 组件 | 职责 |
|------|------|
| `AgentParadigm` | 范式枚举 |
| `ParadigmSelector` | 智能范式选择 (规则+关键词) |
| `BaseParadigmAgent` | 范式 Agent 抽象基类 |
| `PlanAndSolveAgent` | Plan-and-Solve 范式实现 |
| `ReflectionAgent` | Reflection 范式实现 |
| `ParadigmAgentFactory` | 范式 Agent 工厂 |
| `ParadigmService` | 范式服务 (高层 API) |

### 17.3 使用方式

```java
// 自动选择范式
String result = paradigmService.execute("分析我的职业发展路径", userId);

// 指定范式
String result = paradigmService.executeWithParadigm("写一篇总结", "reflection", userId);
```

---

## 十八、上下文工程优化 (L30)

### 18.1 相关性评分

按关键词重叠和密度对记忆项评分排序：

| 组件 | 职责 |
|------|------|
| `ContextRelevanceScorer` | 相关性评分器 |
| `DynamicBudgetAllocator` | 动态预算分配 |
| `KeyInfoExtractor` | 关键信息提取 |
| `ContextEngineer` | 上下文工程服务 |

### 18.2 动态预算分配

根据查询类型动态调整 Token 预算：

| 查询类型 | L1 滑动窗口 | L2 事实 | L3 摘要 | L4 经验 |
|----------|-------------|---------|---------|---------|
| CONVERSATIONAL | 75% | 10% | 10% | 5% |
| FACTUAL | 40% | 35% | 10% | 15% |
| ANALYTICAL | 50% | 15% | 15% | 20% |

### 18.3 使用方式

```java
// 分析查询
QueryAnalysis analysis = contextEngineer.analyzeQuery("分析我的职业发展路径");

// 获取动态预算
Map<MemoryLayer, Integer> budgets = contextEngineer.getBudgetAllocation(6000, query);

// 按相关性排序记忆
List<ScoredMemory> ranked = contextEngineer.rankByRelevance(query, memoryItems);
```

---

## 十九、工具注册机制 (L31)

### 19.1 动态工具注册表

支持运行时动态注册/注销工具：

| 组件 | 职责 |
|------|------|
| `ToolDefinition` | 工具元数据 (名称/描述/能力/健康状态) |
| `ToolRegistry` | 动态注册表 (注册/发现/过滤) |
| `ToolDiscovery` | 自动发现 (Spring Bean 扫描) |
| `ToolRegistryService` | 集成服务 (高层 API) |

### 19.2 能力发现

工具名自动推断能力标签：

| 工具名关键词 | 推断的能力标签 |
|--------------|----------------|
| search, query | `search` |
| file, read, write | `file` |
| web, http, url | `web` |
| terminal, command | `terminal` |
| pdf, document | `document` |

### 19.3 使用方式

```java
// 注册工具
toolRegistryService.register("myTool", "My custom tool",
    Set.of("custom", "utility"), myToolCallback);

// 获取所有工具
ToolCallback[] tools = toolRegistryService.getToolCallbacks();

// 按能力获取工具
ToolCallback[] searchTools = toolRegistryService.getToolCallbacksByCapability("search");
```

---

## 二十、Reflexion 失败记忆 (L32)

### 20.1 机制说明

基于 Reflexion 论文，Agent 从失败中学习：

| 组件 | 职责 |
|------|------|
| `ReflexionMemory` | 失败轨迹存储 (per-user + global) |
| `ReflexionService` | 集成服务 (记录/查询/注入) |

### 20.2 核心功能

- **失败记录**：taskType + error + resolution
- **相关性检索**：按 taskType 匹配历史失败
- **提示词注入**：格式化为"历史失败经验"
- **自动过期**：7 天后自动清理

### 20.3 使用方式

```java
// 记录失败
reflexionService.recordFailure(userId, "tool_call", "超时", "增加超时时间");

// 获取失败上下文 (注入提示词)
String context = reflexionService.getFailureContext(userId, "tool_call");
```

---

## 二十一、RAG Rerank 重排序 (L33)

### 21.1 机制说明

检索后对文档重排序，提升相关性：

| 组件 | 职责 |
|------|------|
| `RerankService` | 重排序服务 |

### 21.2 评分策略

| 因素 | 权重 | 说明 |
|------|------|------|
| 关键词重叠 | 60% | Jaccard + 覆盖率 |
| 文档质量 | 20% | 长度 + 结构 |
| 位置偏差 | 20% | 原始检索顺序 |

### 21.3 使用方式

```java
// 重排序
List<Document> reranked = rerankService.rerank(query, documents);

// Top-K 过滤
List<Document> top5 = rerankService.rerankTopK(query, documents, 5);
```

---

## 二十二、性能监控与诊断 (L28)

### 22.1 监控指标

| 指标 | 类型 | 说明 |
|------|------|------|
| `agent_execution_duration` | Timer | Agent 执行耗时 (P50/P95/P99) |
| `agent_execution_success` | Counter | 成功执行次数 |
| `agent_execution_failure` | Counter | 失败执行次数 |
| `agent_execution_timeout` | Counter | 超时次数 |
| `agent_token_consumption` | Summary | Token 消耗 |
| `agent_tool_calls` | Summary | 工具调用次数 |
| `agent_step_count` | Summary | 执行步数 |
| `agent_active_count` | Gauge | 当前活跃 Agent 数 |

### 22.2 断路器机制

| 状态 | 说明 | 转换条件 |
|------|------|----------|
| CLOSED | 正常运行 | 默认状态 |
| OPEN | 拒绝请求 | 失败率 > 50% 或超时率 > 30% 或连续失败 5 次 |
| HALF_OPEN | 测试恢复 | 30 秒后自动转换 |

### 22.3 诊断端点

```
# 全局诊断
GET /actuator/agent-diagnostics

# 单个 Agent 诊断
GET /actuator/agent-diagnostics/{agentType}
```

返回内容：
- Global overview (活跃 Agent 数、成功率)
- Per-agent metrics (耗时、Token、工具调用)
- Circuit breaker status (断路器状态)
- Health assessment (健康评估)
- Recommendations (优化建议)

### 22.4 Prometheus 集成

```bash
# 获取 Prometheus 指标
curl http://localhost:8123/api/actuator/prometheus
```

Grafana 查询示例：
```promql
# Agent 成功率
rate(agent_execution_success_total[5m]) / rate(agent_execution_duration_seconds_count[5m])

# Agent P95 延迟
histogram_quantile(0.95, rate(agent_execution_duration_seconds_bucket[5m]))

# 活跃 Agent 数
agent_active_count
```

### 22.5 告警规则建议

| 指标 | 阈值 | 说明 |
|------|------|------|
| 成功率 | < 80% | Agent 执行成功率过低 |
| P95 延迟 | > 10s | Agent 响应过慢 |
| 超时率 | > 20% | 频繁超时 |
| 断路器状态 | OPEN | Agent 被断路器阻断 |

---

## 附录：能力分层总览

```
L0  基础对话         单轮 / 多轮对话 + 对话记忆持久化
L1  RAG 知识库       11篇职场文档检索 + Multi-Query 多路召回 + 查询改写
L2  工具调用         联网搜索 / 文件 / 网页抓取 / 资源下载 / 终端 / PDF
L3  MCP              图片搜索等外部 MCP 服务
L4  Manus 超级智能体  ReAct 自主规划 + 工具循环
L5  Multi-Agent      意图识别 → 5 个专业 Agent
L6  预约咨询         状态机追问 + 飞书/钉钉日历
L7  记忆压缩         Token/轮数策略 + LLM 摘要
L8  黑板协作         交付物货架 + 数据员工 + 用户画像
L9  技能系统         YAML 声明式技能热加载
L10 质量守护         自动审查(Review/RedTeam) + 风险分级 + 审计持久化
L11 收藏系统         消息快照 + orphan 标记
L12 用量追踪         7 种事件 + 多维度统计
L13 导入导出         ZIP 全量备份/恢复
L14 对话搜索         加权评分 + 时间衰减
L15 持久化消息       Source of Truth + 双索引
L16 NLU 意图理解层   1次LLM + 别名解析 + 槽位提取 + 意图分类 + 澄清 + 快速路径
L17 多 Agent 运行时  群聊模式 + Task Orchestrator + AgentRunner 适配层
L18 工作流引擎       6种节点 + 实例状态 + 持久化
L19 沙箱执行         Docker/本地进程隔离 + 5层防护
L20 访问控制与治理   投票式决策 + Agent权限 + MCP信任 + Quota配额
L21 Agent 注册中心   YAML声明式 + Marketplace就绪
L22 评测中心         回归测试 + 发版评估 + YAML评测套件
L23 Prompt 版本管理  多版本 + 灰度发布 + A/B测试
L24 交付物生命周期   DRAFT→REVIEWING→APPROVED→PUBLISHED→ARCHIVED + 审计
L25 事件总线         异步治理事件 + 审计日志
L26 安全防护         循环检测 + 工具结果分级 + Token预算
L27 分层记忆系统     四层记忆 + 异步提取 + Token预算分配
L28 性能评估与监控   Actuator + Micrometer + Prometheus + 压测脚本
L29 经典范式支持     ReAct/Plan-and-Solve/Reflection + 范式选择器
L30 上下文工程优化   相关性评分 + 动态预算分配 + 关键信息提取
L31 工具注册机制     动态注册表 + 能力发现 + 健康监控
L32 Reflexion 机制   失败轨迹记忆 + 自动注入提示词
L33 RAG Rerank       关键词重叠 + 文档质量评分 + 位置偏差
横切关注点：JWT 鉴权 · 会话三态生命周期 · 归档/回收站 · AppService 业务编排层 · 全局异常处理 · 结构化输出
```

---

## 附录：测试覆盖

| 测试类别 | 测试文件数 | 覆盖范围 |
|----------|-----------|----------|
| 记忆系统 | 9 | L1-L4 四层 + 协调器 + 提取管道 + 预算分配 + 集成 |
| 执行轨迹 | 10 | 模型属性 + 记录器 + 上下文 + 持久化 + 流 + 控制器 + 集成 |
| 预约咨询 | 5 | Agent集成 + 仓库 + 日历 + 校验 + 模型 |
| Agent 路由 | 3 | 路由评测 + 快速路径 + OrchestratorAgent |
| 工具 | 5 | Web搜索 + 网页抓取 + 资源下载 + PDF + 文件操作 |
| RAG | 3 | 向量库 + 文档加载 + MultiQuery |
| 其他 | 6 | YuManus + AiChatAgent + 应用启动 + 日历错误处理 |
| **合计** | **41** | |
