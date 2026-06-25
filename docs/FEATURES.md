# 全场景职场生存智囊 Agent · 渐进式功能文档

> 本文档按"能力由浅入深"的顺序梳理项目功能，每一层都建立在前一层之上。
> 适用于：个人学习复盘、作品集讲解、面试技术亮点串讲。
>
> 技术底座：Java 21 + Spring Boot 3.4 + Spring AI 1.0（Alibaba DashScope）+ Ollama + PgVector。
> 品牌名：WorkPilot

---

## 能力分层总览

```
L0 基础对话         单轮 / 多轮对话 + 对话记忆持久化
   └─ L1 RAG 知识库   八篇职场文档检索 + Multi-Query 多路召回 + 查询改写
       └─ L2 工具调用   联网搜索 / 文件 / 网页抓取 / 资源下载 / 终端 / PDF
           └─ L3 MCP    图片搜索等外部 MCP 服务
               └─ L4 Manus 超级智能体   ReAct 自主规划 + 工具循环
                   └─ L5 Multi-Agent 智能路由   意图识别 → 5 个专业 Agent
                       └─ L6 预约咨询   状态机追问 + 飞书/钉钉日历
                       └─ L7 记忆压缩   Token/轮数策略 + LLM 摘要
                       └─ L8 黑板协作   交付物货架 + 数据员工 + 用户画像
                       └─ L9 技能系统   YAML 声明式技能热加载
                       └─ L10 质量守护  自动审查(Review/RedTeam) + 风险分级 + 审计持久化
                       └─ L11 收藏系统  消息快照 + orphan 标记
                       └─ L12 用量追踪  7 种事件 + 多维度统计
                       └─ L13 导入导出  ZIP 全量备份/恢复
                       └─ L14 对话搜索  加权评分 + 时间衰减
                       └─ L15 持久化消息  Source of Truth + 双索引
                       └─ L16 NLU 意图理解层  1次LLM + 别名解析 + 槽位提取 + 意图分类 + 澄清
                       └─ L17 多 Agent 运行时  群聊模式 + Task Orchestrator + 工作流引擎
                       └─ L18 工作流引擎  6种节点 + 实例状态 + 持久化
                       └─ L19 沙箱执行  Docker/本地进程隔离 + 5层防护
                       └─ L20 访问控制与治理  投票式决策 + Agent权限 + MCP信任 + Quota配额
                       └─ L21 Agent 注册中心  YAML声明式 + Marketplace就绪
                       └─ L22 评测中心  回归测试 + 发版评估
                       └─ L23 Prompt 版本管理  多版本 + 灰度发布 + A/B测试
                       └─ L24 交付物生命周期  DRAFT→REVIEWING→APPROVED→PUBLISHED
                       └─ L25 事件总线  异步治理事件 + 审计日志
                       └─ L26 安全防护  循环检测 + 工具结果分级 + Token预算
                       └─ L27 分层记忆系统  四层记忆 + 异步提取 + Token预算分配
横切关注点：JWT 鉴权 · 会话三态生命周期 · 归档/回收站 · AppService 业务编排层 · 全局异常处理 · 结构化输出
```

---

## L0 · 基础对话与对话记忆

最底层能力，由 `AiChatAgent`（`app/`）承载。

| 能力 | 说明 | 入口 |
|------|------|------|
| 同步对话 | 单次请求-响应 | `GET /api/ai/ai_chat/chat/sync` |
| 流式对话（SSE） | token 级流式推送，3 种实现：`Flux`、`ServerSentEvent`、`SseEmitter` | `GET /api/ai/ai_chat/chat/sse` 等 |
| 对话记忆 | 按 `chatId` 持久化历史，重启不丢失 | `FileBasedChatMemory`（Kryo 序列化） |
| 自定义 Advisor | `MyLoggerAdvisor`（调用日志）、`ReReadingAdvisor`（Re2 提升推理） | `advisor/` |

**关键点**：对话记忆走文件持久化（Kryo 高性能序列化），`ChatMemoryManager` 统一管理各 Agent 的 `ChatMemory` 实例，按 agent 类型（`resume`/`negotiation`/`escape`/`consultation`/`general`）隔离。

---

## L1 · RAG 知识库

在基础对话之上叠加检索增强，内置职场生存文档（求职篇、在职篇、晋升篇等）。

| 组件 | 职责 |
|------|------|
| `AiChatDocumentLoader` | 加载 Markdown 文档 |
| `MyTokenTextSplitter` | 分词切片 |
| `MyKeywordEnricher` | 关键词元数据增强 |
| `QueryRewriter` | 查询改写，提升召回 |
| `MultiQueryRetriever` | Multi-Query 多路召回（一个问题扩展为多个查询并行检索后合并） |
| `AiChatVectorStoreConfig` / `PgVectorVectorStoreConfig` | 向量库装配（内存 / PgVector 可切换） |
| `AiChatRagCloudAdvisorConfig` | 云端 RAG Advisor |

**入口**：`GET /api/ai/ai_chat/rag/sync` · 文档动态入库 `POST /api/document/upload`、`POST /api/document/add`（上传后实时嵌入向量库）。

---

## L2 · 工具调用（Tool Calling）

让模型具备"动手"能力，工具统一在 `ToolRegistration` 注册为 `ToolCallback[]`。

| 工具 | 类 | 用途 |
|------|----|------|
| 联网搜索 | `WebSearchTool` | 实时职场案例 / 法律条款（SearchAPI） |
| 网页抓取 | `WebScrapingTool` | Jsoup 解析网页正文 |
| 文件操作 | `FileOperationTool` | 读写本地文件 |
| 资源下载 | `ResourceDownloadTool` | 下载网络资源 |
| 终端操作 | `TerminalOperationTool` | 执行命令行 |
| PDF 生成 | `PDFGenerationTool` | 生成定制化职场生存手册（iText + 亚洲字体） |
| 终止 | `TerminateTool` | 供 Agent 主动结束任务 |

**入口**：`GET /api/ai/ai_chat/tools/sync`。

---

## L3 · MCP 模型上下文协议

通过 Spring AI MCP Client 接入外部 MCP 服务，项目内含独立模块 `kiro-image-search-mcp-server`（职场技能相关图片/信息搜索）。

**入口**：`GET /api/ai/ai_chat/mcp/sync`。配置见 `application.yml` 中 `spring.ai.mcp`（默认注释，按需启用）。

---

## L4 · Manus 超级智能体

具备自主规划能力的 ReAct 型 Agent，能拆解目标、循环调用工具直至完成。

```
BaseAgent  →  ReActAgent（思考-行动循环）  →  ToolCallAgent（工具调用）  →  YuManus（具体装配）
```

- `AgentState`：智能体状态机
- `YuManus`：组合全部工具 + DashScope 模型，支持 `runStream` 流式输出执行过程

**入口**：`GET /api/ai/manus/chat`。

---

## L5 · Multi-Agent 智能路由

`OrchestratorAgent` 作为主控，先做意图识别再分发到专业子 Agent，是整个产品的"中枢"。

```
用户消息 → 技能匹配(L9) ──命中──→ 技能直答
              │未命中
              ▼
         意图识别(LLM 分类)
              ▼
   ┌──────────┬──────────┬──────────┬──────────┬──────────┐
 RESUME    NEGOTIATION  ESCAPE   CONSULTATION  GENERAL
 简历优化    薪资谈判    离职规划    预约咨询    通用顾问
```

**跨 Agent 记忆**: 用户在同一会话切换 Agent 时，Orchestrator 从 PersistentMessageRepository 取最近 10 条消息注入给子 Agent，避免上下文丢失。

**会话级路由锁定**: ConsultationAgent 多轮信息收集期间锁定路由，完成/取消后解锁。

| 意图 | 子 Agent | 关键词示例 |
|------|----------|-----------|
| `RESUME` | `ResumeAgent` | 简历、面试、offer、跳槽 |
| `NEGOTIATION` | `NegotiationAgent` | 薪资、涨薪、绩效奖金 |
| `ESCAPE` | `EscapeAgent` | 离职、辞职、裁员、劳动纠纷 |
| `CONSULTATION` | `ConsultationAgent` | 预约、咨询、约时间 |
| `GENERAL` | `GeneralCareerAgent` | 人际、压力、职业规划 |

**入口**：`GET /api/ai/orchestrator/chat`（SSE，需 JWT）。路由前会注入用户画像（L8）+ 货架就绪交付物（L8），对话结束后异步更新画像。意图枚举见 `AgentIntent`。

---

## L6 · 预约咨询（状态机 + 企业日历）

`ConsultationAgent` 通过追问状态机收集信息并对接企业日历。支持从对话历史提取已有信息，确认阶段可自由提问。

```
INITIAL → COLLECTING_INFO → CONFIRMING → CREATING_APPOINTMENT → COMPLETED
```

| 组件 | 职责 |
|------|------|
| `FollowUpTemplateConfig` | 追问模板（姓名/联系方式/时间），支持热更新，Markdown 结构化输出 |
| `InfoValidator` | 自然语言提取（"我叫小琪"→小琪，"我的手机号是18104620109"→18104620109） |
| `CalendarService` + `CalendarServiceFactory` | 日历服务抽象 |
| `FeishuCalendarService` / `DingTalkCalendarService` | 飞书 / 钉钉日历实现 |
| `AppointmentRepository` | 预约记录持久化 |

**智能提取**：姓名（"我叫X"→X）、联系方式（从自然语言搜索手机号/邮箱）、时间（中文数字"三点"→15:00，忽略无关文字）。

**确认阶段灵活性**：用户在确认阶段提问时，LLM 回答问题后再引导确认/修改。

---

## L7 · 对话记忆压缩

长对话性能优化，避免上下文无限膨胀。

| 组件 | 职责 |
|------|------|
| `CompressionStrategy` | 压缩策略接口 |
| `TokenCompressionStrategy` | Token 阈值触发（默认 4000） |
| `TurnCompressionStrategy` | 对话轮数触发（默认 20 轮） |
| `MemoryCompressor` | 调用 LLM 生成关键信息摘要 |

**保留策略**：保留最近 N 轮（默认 5）完整对话，更早的历史压缩为摘要（用户需求、已确认信息、未解决问题、重要决策）。压缩失败降级为简单摘要。

---

## L8 · 黑板协作（数据员工 + 货架 + 用户画像）

最高阶的多 Agent 协作能力，采用**黑板模式（Blackboard Pattern）**。上游 Agent 产出交付物放上货架，下游按需取用。

### 共享交付物货架（Artifact Shelf）

```
生产者 Agent ──put(READY)──► ArtifactShelf ──query/get──► 消费者 Agent
                                  │                            │
                            ArtifactRepository           markConsumed
                            (Jackson+JSON+RWLock)
```

- `ArtifactShelf`：放货 `put` / 读取 `get` / 查询 `query` / 消费标记 `markConsumed`
- **作用域隔离**：`USER_PROFILE`（按 userId 跨会话累积）/ `TASK`（按 chatId 会话级）
- **状态机**：`DRAFT → REVIEWING → APPROVED → PUBLISHED → ARCHIVED`

### 数据员工 Agent（`agent/data/`）

```
DataEmployeeAgent（抽象模板：加工 → 封装 Artifact → 放货）
   ├─ DataAnalystAgent           数据分析师（对话/文档分析报告）
   ├─ CareerCoachAgent           岗位辅导
   ├─ ProfileCuratorAgent        用户画像整理
   ├─ PromotionPlannerAgent      晋升路径规划
   └─ LearningResourceRecommenderAgent  学习资源推荐
```

### 用户画像系统（`profile/`）

| 组件 | 职责 |
|------|------|
| `UserProfileExtractor` | 对话结束后 LLM 抽取画像维度 |
| `UserProfileService` | 抽取编排 / 合并 / 查询 / 清空 / 注入 |
| `UserProfileRepository` | 画像持久化 + 合并去重 |
| `ProfilePromptBuilder` | 画像 → system prompt 片段（含字符上限 1000） |

**画像维度**：沟通偏好（CONCISE/DETAILED）、语气偏好、关注领域（列表）、已知背景、历史诉求（列表）。

**入口**：`GET /api/profile/me`（查看，JWT）、`DELETE /api/profile/me`（清空，JWT）、`GET /api/artifact/list`、`GET /api/artifact/{id}`（管理员）。

---

## L9 · 技能系统（YAML 声明式）

参考 Hermes Agent 的 SKILL.md 思路，用 YAML 声明技能并在启动时热加载（`classpath:skills/*.yaml`）。

| 组件 | 职责 |
|------|------|
| `SkillDefinition` | 技能定义（名称、描述、systemPrompt、输入字段、few-shot 示例） |
| `SkillRegistry` | 扫描加载 / 按名称·标签·意图查找 / 运行时注册 |
| `SkillExecutor` | 技能执行（同步 + 流式） |

技能匹配优先于意图路由（见 L5），命中即直接由技能回答。当前为关键词匹配，可升级为向量相似度。

---

## L10 · 质量守护（Quality Guard）

对其他 Agent 的输出进行质量审查，检测事实准确性、幻觉风险和安全隐患。

| 组件 | 职责 |
|------|------|
| `QualityGuardAgent` | 审查执行（REVIEW 单次 / RED_TEAM 红队对抗） |
| `QualityModeResolver` | 模式自动解析（意图 + LLM 风险分类） |
| `QualityReview` | 审查结果（5 维评分 + 风险等级 + issues + suggestions） |
| `QualityReviewRepository` | HIGH/CRITICAL 审查持久化（审计告警） |

**审查维度**：accuracyScore(30%)、completenessScore(20%)、logicScore(20%)、hallucinationScore(30%)、riskScore(参考)。

**模式解析**：RESUME/NEGOTIATION/ESCAPE 意图 → REVIEW；其他意图 → LLM 风险分类（LOW→OFF, MEDIUM→REVIEW, HIGH/CRITICAL→RED_TEAM）。

**持久化策略**：仅 HIGH/CRITICAL 风险审查写入 `quality-reviews.json`，普通审查仅记录在 ExecutionTrace。

---

## L11 · 收藏系统（Favorites）

收藏消息快照，即使原消息或会话被删除，收藏内容依然保留。

| 组件 | 职责 |
|------|------|
| `Favorite` | 收藏实体（含 contentSnapshot + sessionTitleSnapshot 防丢失） |
| `FavoriteRepository` | 文件持久化 + orphan 标记（会话删除时自动标记） |
| `FavoriteAppService` | 业务编排 |

**API**：`POST /api/favorite`（添加）、`DELETE /api/favorite/{id}`（取消）、`GET /api/favorite/list`（列表）。均需 JWT。

---

## L12 · 用量追踪（Usage Tracking）

记录用户操作事件，提供多维度使用统计。

| 事件类型 | 说明 |
|----------|------|
| CHAT | 普通对话 |
| RAG | RAG 知识库查询 |
| TOOL_CALL | 工具调用 |
| DOCUMENT_UPLOAD | 文档上传 |
| EXPORT | 数据导出 |
| COMPARE | Agent 对比 |
| QUALITY_REVIEW | 质量审查 |

**统计维度**：totalEvents、eventsByType、eventsByAgent、dailyCounts（近 7 天）、totalDurationMs。

**存储**：`usage-events.json`，append-only。**API**：`GET /api/usage/stats`（JWT）。

---

## L13 · 数据导入导出（Import/Export）

用户数据全量备份（ZIP）与恢复，覆盖会话、消息、收藏三类数据。

| 组件 | 职责 |
|------|------|
| `DataExportService` | ZIP 打包导出（sessions + messages + favorites） |
| `DataImportService` | ZIP 解析 + chatId 冲突处理（自动生成新 ID） |
| `ExportAppService` | 业务编排 |

**API**：`GET /api/export/all`（ZIP 下载）、`POST /api/export/import`（multipart 上传）。均需 JWT。

---

## L14 · 对话搜索（Chat Search）

跨会话加权搜索，支持标题、用户消息、AI 消息多区域匹配。

**评分模型**：标题权重 100、用户消息 30、AI 消息 20。匹配类型：equals(100) > startsWith(70) > contains(50)。时间衰减：≤1d(+30)、≤7d(+20)、≤30d(+10)。命中次数：count × 10。

**返回**：chatId、title、relevance(0-100)、snippet、bestHit(messageId + offset，前端高亮定位)。

**关键类**：`ChatSearchService` — 内存直扫（万级会话毫秒级），未来可升级 Lucene/ES。

---

## L15 · 持久化消息（Persistent Messages）

对话消息的 **Source of Truth**，所有下游功能（历史、搜索、收藏、导出）基于此模型。

| 组件 | 职责 |
|------|------|
| `PersistentChatMessage` | 消息实体（ULID messageId + chatId + role + content + timestamp + source） |
| `PersistentMessageRepository` | 双索引持久化（chatIndex + messageIdIndex） |
| `ChatMemoryAdapter` | Truth ↔ ChatMemory 桥接（写入先持久化再同步缓存，读取先检查一致性） |

**存储**：`{session.storage.dir}/messages/{chatId}.json`，每个会话一个文件。

**压缩支持**：`replaceWithSummary(chatId, summary, keepRecent)` — 压缩时替换旧消息为摘要 + 最近 N 条。

**消息来源**：`MessageSource` 枚举（USER/AGENT/SYSTEM/TOOL/SYNTHESIZER），支持多 Agent 群聊模式下消息溯源。

---

## L16 · NLU 意图理解层（V4.2）

替代原 `detectIntent()` 单次 LLM 分类，升级为结构化 NLU 管道。

```
用户消息 → AliasResolver（别名元数据提取，不改原文）
         → UnifiedNluExtractor（1次LLM：intent排名 + slots + domain + action）
         → IntentReranker（alias domain 信号 re-rank）
         → IntentAmbiguityDetector（同类意图检测）
         → RouteTemplate（点分记法路由：advertiser.query.roi）
         → ContextShiftDetector（3态：FOLLOW_UP / ENTITY_SWITCH / NEW_QUERY）
         → ConversationState.smartMerge（followUp感知，追问继承/reset）
         → IntentRequirementRegistry（required/optional 槽位可配置）
         → ClarificationHandler（模板追问，零LLM）
         → NluResult
```

| 组件 | 职责 |
|------|------|
| `UnifiedNluExtractor` | 单次 LLM 调用，输出 intent 排名 + 结构化槽位 + domain + action |
| `AliasResolver` | 别名元数据提取（中文后边界 `(?!\\p{IsHan})`，英文 `\\b`），不改原文 |
| `IntentReranker` | alias domain 信号 re-rank（ADVERTISER → QUERY_DATA +0.15） |
| `IntentAmbiguityDetector` | Top1/Top2 同类检测（替代 confidence < 0.2） |
| `RouteTemplate` | 点分记法路由生成（`domain.action.metric`） |
| `ContextShiftDetector` | 3 态：FOLLOW_UP / ENTITY_SWITCH / NEW_QUERY（接口，Phase 2 换 Embedding） |
| `ConversationState` | 多轮槽位状态 + 3 态 smartMerge + version CAS |
| `IntentRequirementRegistry` | intent + routeHint 双维度槽位需求（可配置） |
| `ClarificationHandler` | 模板化追问（零 LLM 调用） |
| `NluPipeline` | 串联管道，1 次 LLM 调用完成全部意图理解 |
| `DataQueryRouter` | DATA_QUERY 意图透传 slots（不调 LLM） |
| `RouteHint` | NLU → WorkflowMatcher 桥接（Phase 2 直接消费） |

**关键设计决策**：
- 1 次 LLM 调用（非 2-3 次），延迟减半
- 别名不改原文（只输出 AliasMatch 元数据）
- Confidence = Top1-Top2 差值（非模型自报）
- 澄清用模板（零 LLM 调用）
- RouteHint Phase 1 即输出，Phase 2 WorkflowMatcher 零重构

---

## L17 · 多 Agent 运行时（V1 群聊 + V2 Task Orchestrator）

### V1 群聊模式

用户提问 → NLU 多意图识别 → 多 Agent 串行执行 → 每个 Agent 独立回答。

```
用户: "我要跳槽，简历和薪资怎么准备"
  → NLU: [RESUME, NEGOTIATION]
  → agent-turn SSE 事件（前端知道谁在说话）
  → ResumeAgent → "简历建议..." → SSE 推送
  → NegotiationAgent → "薪资建议..." → SSE 推送
  → 持久化（每个 Agent 回答带 MessageSource 追踪）
```

| 组件 | 职责 |
|------|------|
| `MessageSource` | 消息来源枚举（USER/AGENT/SYSTEM/TOOL/SYNTHESIZER） |
| `PersistentChatMessage` | +sourceType/sourceId/sourceName 三字段 |
| `AgentIntent.fromMultiIntent()` | NLU reranked intents → AgentIntent 列表 |
| `OrchestratorAgent` | 多意图串行执行 + agent-turn SSE 事件 + source 追踪持久化 |

### V2 Task Orchestrator 基础设施

| 层 | 组件 | 职责 |
|----|------|------|
| 模型层 | `AgentOutput` / `TextOutput` / `FormatterRegistry` | 类型化 Agent 输出 + 格式化 |
| 模型层 | `ExecutionResult` / `TaskStatus` / `FailurePolicy` | 统一执行结果 + 状态 + 失败策略 |
| 预算层 | `TokenBudget` / `TokenUsage` / `TokenUsageTracker` | Token 预算控制 |
| 上下文层 | `ConversationContext` / `RuntimeContext` / `ConversationContextBuilder` | 不可变对话上下文 + 可变执行状态 |
| 工作流层 | `WorkflowTemplate` / `WorkflowRegistry` / `WorkflowMatcher` | 工作流定义 + 注册 + Score-based 匹配 |
| 执行层 | `AgentRunner` / `TaskExecutor` / `ResultAggregator` | Agent 执行接口 + 任务引擎 + 结果汇总 |

**内置工作流模板**：JOB_CHANGE / INTERVIEW / CONSULTATION / GENERIC_CAREER / DATA_QUERY

**V1 → V2 升级路径**：V1 的 for 循环可随时切换为 WorkflowMatcher + TaskExecutor + ResultAggregator，基础设施已就绪。

---

## L18 · 工作流引擎（Workflow Runtime）

独立于 Agent 的工作流执行引擎，支持 6 种节点类型，用于复杂任务编排。

| 节点类型 | 类 | 说明 |
|----------|-----|------|
| AgentNode | `AgentNode` | 委托给 Agent 执行 |
| ToolNode | `ToolNode` | 直接调用 Tool |
| ConditionNode | `ConditionNode` | 条件分支（SpEL 表达式） |
| ParallelNode | `ParallelNode` | 并行执行多个子节点 |
| LoopNode | `LoopNode` | 循环执行（最大迭代次数限制） |
| ApprovalNode | `ApprovalNode` | 人工审批（暂停工作流） |

**工作流状态**：PENDING → RUNNING → PAUSED（等待审批）/ COMPLETED / FAILED / CANCELLED

**执行流程**：
```
WorkflowRuntime.startWorkflow(workflowId, nodes, initialContext, userId, chatId)
  → 创建 WorkflowInstance (status=RUNNING)
  → 遍历 nodes，按顺序执行
    ├─ AgentNode → 委托给指定 Agent
    ├─ ToolNode → 直接调用 Tool
    ├─ ConditionNode → 评估条件，选择分支
    ├─ ParallelNode → 并行执行子节点
    ├─ LoopNode → 循环执行（最大迭代次数限制）
    └─ ApprovalNode → 暂停，等待人工审批
  → 记录 StepRecord（节点ID、状态、结果、耗时）
  → 更新 WorkflowInstance 状态
  → 持久化到 WorkflowRepository
```

**关键类**：`WorkflowRuntime`（运行时引擎）、`WorkflowInstance`（实例状态）、`WorkflowRepository`（持久化）、`WorkflowNode`（节点基类，Jackson 多态序列化）

---

## L19 · 沙箱执行（Sandbox）

Tool 执行的隔离环境，防止恶意命令影响宿主系统。

| 沙箱策略 | 说明 | 场景 |
|----------|------|------|
| UNSANDBOXED | 不使用沙箱 | 纯计算型安全 Tool（PDF 生成、字符串处理） |
| PROCESS_SANDBOX | 本地进程沙箱 | Docker 不可用时的降级方案 |
| DOCKER_SANDBOX | Docker 容器隔离 | 生产环境强制要求 |

**本地进程沙箱 5 层防护**：命令白名单、工作目录隔离、超时控制、输出限制、环境变量隔离。

**Docker 沙箱资源限制**：CPU 核心数、内存上限、执行超时、网络访问、根文件系统只读。

**SandboxFactory 决策逻辑**：Docker 可用 → DockerSandbox；Docker 不可用 + 非生产 → LocalProcessSandbox（降级）；Docker 不可用 + 生产 → 启动失败。

**关键类**：`SandboxFactory`（工厂）、`ToolSandbox`（接口）、`DockerSandbox`（Docker 实现）、`LocalProcessSandbox`（本地进程实现）

---

## L20 · 访问控制与治理（Access Control & Governance）

统一的访问决策服务，聚合多个安全维度的投票结果。

**决策策略**：一票否决（any DENY → reject），全部弃权 → reject（默认安全）。

| 投票器 | 职责 |
|--------|------|
| AgentPolicyVoter | 检查 Agent 权限画像（PermissionProfile） |
| McpPolicyVoter | 检查 MCP Server 信任等级（McpTrustLevel） |
| QuotaPolicyVoter | 检查调用配额（单请求最大 Tool 调用次数） |

**PermissionProfile（权限画像）**：
- `allowedToolPatterns` — 允许的 Tool 命名空间模式（支持通配符）
- `minMcpTrustScore` — MCP 信任分下限
- `maxToolCallsPerRequest` — 单请求最大 Tool 调用次数
- `filesystemAccess` / `networkAccess` — 文件系统/网络访问权限
- `admin` — 超级管理员（跳过所有检查）

**McpTrustLevel（信任等级）**：
- VERIFIED(100) — 官方认证，全部权限
- PARTNER(70) — 合作伙伴，受限权限
- COMMUNITY(30) — 社区上传，仅公开 Tool
- PRIVATE(0) — 私有/未审核，禁止访问敏感 Tool

**事件总线**：`EventBusAdapter` 异步发布治理事件（`AccessDeniedEvent`、`SandboxExecEvent`），不阻塞主流程。

**关键类**：`AccessDecisionService`（投票式决策）、`AgentPermissionService`（权限校验）、`McpTrustService`（MCP 信任管理）、`EventBusAdapter`（事件总线）

---

## L21 · Agent 注册中心（Agent Registry）

YAML 声明式 Agent 描述符，支持 Agent Marketplace 场景。

**AgentDescriptor 字段**：agentCode、agentVersion、displayName、description、promptVersion、capabilities、skillBindings、mcpBindings、permissionProfile、intentKeywords、metadata

**加载方式**：从 `resources/agents/*.yaml` 加载，启动时自动注册。

**查询能力**：按编码获取、按能力标签查找、按意图关键词匹配。

**关键类**：`AgentRegistry`（接口）、`InMemoryAgentRegistry`（内存实现）、`AgentDescriptor`（描述符）

---

## L22 · 评测中心（Eval Center）

Agent 质量评测框架。当前实现：评测报告模型 + 路由准确率集成测试。

**评测报告**：`EvalReport`（reportId、overallScore、passRate、regression、caseResults）

**路由评测测试**：
- `AgentRoutingEvalTest` — 路由准确率 + 快速路径覆盖率 + 响应时间
- `FastPathRoutingTest` — 快速路径规则匹配验证

**待实现**：`EvalCenter` 服务（用例加载 → Agent 调用 → 评分 → 回归检测），当前由集成测试覆盖。

---

## L23 · Prompt 版本管理（Prompt Registry）

管理 Prompt 版本，支持灰度发布和 A/B 测试流量分配。

**流量分配逻辑**：只有 1 个 ACTIVE → 直接返回；多个 ACTIVE → 按 trafficPercent 加权随机选择；无 ACTIVE → 返回最新版本（降级）。

**版本限制**：每个 promptKey 最多保留 50 个版本（MAX_VERSIONS_PER_KEY），超出自动清理最旧版本。

**关键类**：`PromptRegistry`（注册中心）、`PromptVersion`（版本）

---

## L24 · 交付物生命周期（Artifact Lifecycle）

控制交付物状态流转的合法性并记录审计事件。

**合法流转**：
```
DRAFT → REVIEWING（提交审核）
REVIEWING → APPROVED（审核通过）
REVIEWING → DRAFT（审核拒绝，含原因）
APPROVED → PUBLISHED（发布）
任意 → ARCHIVED（归档）
```

**关键类**：`ArtifactLifecycleManager`（生命周期管理器）、`ArtifactLifecycleEvent`（生命周期事件）

---

## L25 · 事件总线（Event Bus）

异步治理事件发布，监听器执行（审计日志、指标、通知）不阻塞主流程。

| 事件 | 说明 | 触发场景 |
|------|------|----------|
| GovernanceEvent | 治理事件基类 | 所有治理操作 |
| AccessDeniedEvent | 权限拒绝 | Agent/Tool 权限校验失败 |
| SandboxExecEvent | 沙箱执行 | Tool 在沙箱中执行 |

**关键类**：`EventBusAdapter`（事件总线适配器）、`GovernanceEventListener`（事件监听器）

---

## L26 · 安全防护（Safety Guards）

多层安全防护机制，防止 Agent 陷入死循环、工具返回垃圾数据、Token 成本失控。

### EmbeddingLoopDetector（循环检测）

- 基于 Embedding 余弦相似度的循环检测器
- 滑动窗口 5 条，相似度阈值 0.88
- 检测到循环后注入带有"上次失败原因"的针对性引导消息
- 连续循环阈值 2 次触发干预

### ToolResultClassifier（工具结果分级）

| 等级 | 说明 | 策略 |
|------|------|------|
| TIMEOUT | 超时 | 建议直接重试（方向对，网络问题） |
| EMPTY | 空结果 | 建议换策略（不是重试能解决的） |
| GARBAGE | 垃圾内容 | 过滤后建议换关键词（登录墙/付费墙/堆栈跟踪） |
| NORMAL | 正常 | 不干预 |

### TokenBudgetManager（Token 预算分级）

| 模式 | 触发条件 | 策略 |
|------|----------|------|
| Normal | < 65% | 搜索结果保留 3000 字符，无截断 |
| Compact | 65% ~ 85% | 搜索结果截断至 1500 字符 |
| Compress | > 85% | 用 LLM 压缩历史 Observation，摘要化 |

**关键取舍**：Think（AssistantMessage）绝对不动，只压缩 Observation（ToolResponseMessage）。因为"思考轨迹"比"原始搜索结果"对推理更有价值。

---

## L27 · 分层记忆系统（MemoryCoordinator）

替代原单一 `ChatMemoryManager`，升级为四层记忆架构，支持长期知识积累和语义检索。

```
用户消息
  │
  ▼
MemoryCoordinator.assembleContext(userId, chatId, agentType)
  │
  ├─ CompletableFuture 并行查询四层（超时 2000ms）
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
| `FactStoreLayer` (L2) | 结构化用户事实，精确匹配（含 v1→v2 迁移兼容） |
| `SummaryLayer` (L3) | 对话摘要要点清单（FIFO 淘汰 + checklist） |
| `ExperienceStoreLayer` (L4) | 历史经验向量化存储（PgVector），语义模糊匹配 |
| `TokenBudgetAllocator` | 四层预算分配（优先级裁剪：L1 最优先） |
| `ExtractionPipeline` | 对话完成后异步提取事实/摘要/经验（单次 LLM，永不阻塞） |
| `MemoryLayer` | 层级枚举（SLIDING_WINDOW / FACT_STORE / SUMMARY / EXPERIENCE） |
| `ContextWindow` | 上下文窗口记录 |

**提取管道（ExtractionPipeline）**：对话完成后异步运行，单次 LLM 调用同时提取事实、摘要和经验，写入对应层。使用独立线程池 `memoryExtractionExecutor`，永不阻塞调用者，所有异常内部捕获。

**容错设计**：每层查询超时独立（默认 2000ms），失败时使用 `layerCache` 中的 last-known-good 数据。总预算 6000 tokens，按 L1→L4 优先级递减分配。

**FactStore 迁移**：`FactStoreMigrationTest` 验证 v1→v2 字段映射的幂等性和向后兼容。

**关键类**：`MemoryCoordinator`（协调器）、`SlidingWindowLayer`（L1）、`FactStoreLayer`（L2）、`SummaryLayer`（L3）、`ExperienceStoreLayer`（L4）、`TokenBudgetAllocator`（预算分配）、`ExtractionPipeline`（提取管道）

---

## 横切关注点

| 关注点 | 实现 | 说明 |
|--------|------|------|
| 鉴权 | `JwtUtil` + `AuthService` | JWT 校验；SSE 接口 token 走 URL 参数，兼容 `Authorization` 头 |
| 会话三态 | `SessionManager` | ACTIVE / ARCHIVED / DELETED（软删除，30 天物理清理）；`chatOwner` 反向索引防越权 |
| AppService 编排 | `OrchestratorAppService` 等 | 输入校验、归属检查、用量追踪、编排 Agent/Repository |
| 异常处理 | `GlobalExceptionHandler` | 全局统一异常响应 |
| 统一响应 | `common/Response` + `ResultCode` | 标准化返回结构 |
| 结构化输出 | `AiChatAgent.AiChatReport` | 职场报告结构化（victools jsonschema） |
| 健康检查 | `HealthController` | 探活 |
| 跨域 | `CorsConfig` | 前端联调 |

---

## 数据存储一览（文件持久化 / "表")

项目当前以**文件 + JSON/Kryo** 作为持久层（统一范式：`ObjectMapper + JavaTimeModule` / `ConcurrentHashMap` 内存索引 / `ReadWriteLock` / `@PostConstruct` 加载 / `@Value` 配置目录）。PgVector 用于向量检索。

| 逻辑"表" | 存储位置（默认） | 负责组件 | 关键字段 |
|----------|-----------------|----------|----------|
| 会话 sessions | `./tmp/sessions/sessions.json` | `SessionManager` | chatId、userId、title、status、createdAt、lastActiveAt、archivedAt、deletedAt；`chatOwner` 反向索引 |
| 消息 messages | `./tmp/sessions/messages/{chatId}.json` | `PersistentMessageRepository` | messageId(ULID)、chatId、role、content、timestamp、sourceType、sourceId、sourceName；双索引 chatIndex + messageIdIndex |
| 预约 appointments | `./tmp/appointments/` | `AppointmentRepository` | name、contact、appointmentTime、calendarEventId、calendarUrl、provider、status、chatId、createdAt |
| 交付物 artifacts | `./tmp/artifacts/artifacts.json` | `ArtifactRepository` | artifactId、userId、chatId、type、producer、title、content、status(DRAFT/REVIEWING/APPROVED/PUBLISHED/ARCHIVED)、scope、createdAt、updatedAt |
| 用户画像 user-profiles | `./tmp/user-profiles/` | `UserProfileRepository` | userId、communicationPreference、tonePreference、focusAreas[]、knownBackground、historicalDemands[]、createdAt、updatedAt |
| 收藏 favorites | `./tmp/artifacts/favorites.json` | `FavoriteRepository` | favoriteId、userId、chatId、messageId、contentSnapshot、sessionTitleSnapshot、role、orphaned |
| 质量审查 quality-reviews | `./tmp/artifacts/quality-reviews.json` | `QualityReviewRepository` | reviewId、chatId、mode、5 维评分、riskLevel、issues[]、suggestions[]（仅 HIGH/CRITICAL） |
| 用量事件 usage-events | `./tmp/artifacts/usage-events.json` | `UsageTracker` | eventId、userId、type、agentType、durationMs、timestamp（append-only） |
| 对话记忆 chat-memory | 文件（Kryo） | `FileBasedChatMemory` | chatId → List<Message>（按 agent 类型隔离） |
| 工作流实例 | 内存 | `WorkflowRepository` | instanceId、workflowId、status、nodes、context、history |
| Agent 描述符 | `classpath:agents/*.yaml` | `AgentRegistry` | agentCode、capabilities、intentKeywords、permissionProfile |
| 权限画像 | `classpath:permissions/*.yaml` | `PermissionProfileRegistry` | agentCode、allowedToolPatterns、minMcpTrustScore、admin |
| 评测用例 | `classpath:eval/*.yaml` | `EvalCenter` | caseId、input、expectedIntent、expectedAgent、assertions |
| 向量库 | PgVector / 内存 | `*VectorStoreConfig` | 文档 embedding + 元数据（filename、status） |

### 枚举类型

| 枚举 | 取值 |
|------|------|
| `AgentIntent` | RESUME、NEGOTIATION、ESCAPE、CONSULTATION、DATA_QUERY、GENERAL |
| `NluIntent` | RESUME_OPTIMIZE、INTERVIEW_PREP、JOB_CHANGE、SALARY_ANALYZE、SALARY_NEGOTIATION、LEAVE_PLAN、CONSULTATION、QUERY_DATA、CAREER_GENERAL、UNKNOWN 等 14 值 |
| `MessageSource` | USER、AGENT、SYSTEM、TOOL、SYNTHESIZER |
| `TaskStatus` | PENDING、RUNNING、SUCCESS、FAILED、RETRYING、SKIPPED、SKIPPED_BY_BUDGET、SKIPPED_BY_POLICY |
| `FailurePolicy` | FAIL_FAST、RETRY_THEN_SKIP、RETRY_THEN_FAIL、SKIP |
| `MatchType` | RULE、LLM、FALLBACK |
| `ShiftType` | FOLLOW_UP、ENTITY_SWITCH、NEW_QUERY |
| `ArtifactStatus` | DRAFT、REVIEWING、APPROVED、PUBLISHED、ARCHIVED |
| `ArtifactScope` | USER_PROFILE、TASK |
| `CommunicationPreference` | CONCISE、DETAILED |
| `AnalysisSource` | CONVERSATION、UPLOADED_DOCUMENT |
| `AppointmentStatus` | PENDING、CONFIRMED、COMPLETED、CANCELLED、FAILED |
| `CalendarProvider` | FEISHU、DINGTALK |
| `SessionStatus` | ACTIVE、ARCHIVED、DELETED |
| `QualityMode` | OFF、AUTO、REVIEW、RED_TEAM |
| `RiskLevel` | LOW、MEDIUM、HIGH、CRITICAL |
| `UsageEventType` | CHAT、RAG、TOOL_CALL、DOCUMENT_UPLOAD、EXPORT、COMPARE、QUALITY_REVIEW |
| `WorkflowStatus` | PENDING、RUNNING、PAUSED、COMPLETED、FAILED、CANCELLED |
| `SandboxPolicy` | UNSANDBOXED、PROCESS_SANDBOX、DOCKER_SANDBOX |
| `McpTrustLevel` | VERIFIED、PARTNER、COMMUNITY、PRIVATE |

---

## 关键配置（application.yml）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `spring.ai.dashscope.chat.options.model` | `qwen3.5-plus-2026-04-20` | 主模型（多模态） |
| `spring.ai.ollama.chat.model` | `gemma3:1b` | 本地模型 |
| `server.port` / `context-path` | `8123` / `/api` | 服务端口与上下文 |
| `jwt.secret` | 环境变量注入 | JWT 密钥 |
| `calendar.provider` | `FEISHU` | 日历服务商 |
| `chat.memory.compression.token-threshold` | `4000` | 压缩 Token 阈值 |
| `chat.memory.compression.turn-threshold` | `20` | 压缩轮数阈值 |
| `chat.memory.compression.recent-turns` | `5` | 保留最近轮数 |
| `artifact.storage.dir` | `./tmp/artifacts` | 交付物目录 |
| `user-profile.storage.dir` | `./tmp/user-profiles` | 画像目录 |
| `profile.injection.max-chars` | `1000` | 画像注入字符上限 |
| `sandbox.require-docker` | `false` | 生产环境是否强制要求 Docker |

---

## API 速查

| 分类 | 方法 | 路径 |
|------|------|------|
| 基础对话 | GET | `/api/ai/ai_chat/chat/sync` · `/sse` · `/server_sent_event` · `/sse_emitter` |
| 智能路由 | GET | `/api/ai/orchestrator/chat`（JWT） |
| Manus | GET | `/api/ai/manus/chat` |
| RAG | GET | `/api/ai/ai_chat/rag/sync` |
| 工具 | GET | `/api/ai/ai_chat/tools/sync` |
| MCP | GET | `/api/ai/ai_chat/mcp/sync` |
| 结构化报告 | GET | `/api/ai/ai_chat/report/sync` |
| 文档入库 | POST | `/api/document/upload` · `/api/document/add` |
| 用户画像 | GET/DELETE | `/api/profile/me`（JWT） |
| 交付物 | GET | `/api/artifact/list` · `/api/artifact/{id}`（管理员） |
| 会话 | - | `SessionController`（增删查/归档/搜索/消息历史） |
| 收藏 | POST/DELETE/GET | `/api/favorite` · `/api/favorite/{id}` · `/api/favorite/list`（JWT） |
| 用量 | GET | `/api/usage/stats`（JWT） |
| 导入导出 | GET/POST | `/api/export/all` · `/api/export/import`（JWT） |
| 轨迹 | GET | `/api/trace/{id}` · `/api/trace/chat/{chatId}` · `/api/trace/user/{userId}` |
| 健康 | GET | `/api/health` |
