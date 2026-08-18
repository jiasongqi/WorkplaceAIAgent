# 全场景职场生存智囊 Agent · 渐进式功能文档

> 本文档按"能力由浅入深"的顺序梳理项目功能，每一层都建立在前一层之上。
> 适用于：个人学习复盘、作品集讲解、面试技术亮点串讲。
>
> 技术底座：Java 21 + Spring Boot 3.4 + Spring AI 1.0（Alibaba DashScope）+ Vue 3 + PDFBox / POI（感知层）。
> 品牌名：WorkPilot
> 文档同步：2026-08-18 · 桌面萌宠 · 预约查日程/目录分流 · 平台 `platform.*` 开关 · sage/dark 双主题

---

## 能力分层总览

> **状态图例**：`[闭环]` 端到端可用（真实调用/真实数据，无占位逻辑）· `[部分]` 核心路径闭环，部分分支/子能力为简化实现或需额外开关 · `[脚手架]` 数据结构与接口已就位，尚未接入真实执行/调用链。
>
> 关键层级：L5/L17 多 Agent `[闭环]`（**单意图真 SSE**；多意图并行辩论+综合后推送 + failover + agent-progress）· L8 黑板 `[部分]` · L18 工作流 `[部分]`（`workflow.dag.enabled` 时 JOB_CHANGE/INTERVIEW 接主聊天；其余节点仍为脚手架）· L21 注册中心 `[脚手架]` · L22 评测 `[部分]`（routing 门禁闭环；live 需 ADMIN）· L23 Prompt A/B `[脚手架]`。
>
> 面试话术速查：`docs/INTERVIEW-DEFENSE.md`

```
L0 基础对话         单轮 / 多轮对话 + 对话记忆持久化
   └─ L1 RAG 知识库   RetrievalPipeline + RagTool + PDF 表格 + /knowledge 管理页
       └─ L2 工具调用   联网搜索 / 文件(file_id) / 抓取 / 下载 / 终端 / PDF / 异步轮询
           └─ L3 MCP    图片搜索等外部 MCP 服务
               └─ L4 Manus 超级智能体   ReAct + Wrap-up + 完成态防幻觉 + Depth Limit
                   └─ L5 Multi-Agent 智能路由 [闭环]   意图识别 → 5 个专业 Agent + 并行辩论/failover + SSE 进度事件
                       └─ L6 预约咨询   状态机追问 + 飞书/钉钉日历 + HITL 人工审批
                       └─ L7 记忆压缩   Token/轮数策略 + LLM 摘要
                       └─ L8 黑板协作 [部分]   交付物货架 + 数据员工 + 用户画像
                       └─ L9 技能系统   YAML 声明式技能热加载
                       └─ L10 质量守护  自动审查(Review/RedTeam) + 风险分级 + 审计持久化
                       └─ L11 收藏系统  消息快照 + orphan 标记
                       └─ L12 用量追踪  7 种事件 + 多维度统计
                       └─ L13 导入导出  ZIP 全量备份/恢复
                       └─ L14 对话搜索  加权评分 + 时间衰减
                       └─ L15 持久化消息  Source of Truth + 双索引
                       └─ L16 NLU 意图理解层  1次LLM + 别名解析 + 槽位提取 + 意图分类 + 澄清
                       └─ L17 多 Agent 运行时 [闭环]   群聊模式 + Task Orchestrator + 工作流引擎
                       └─ L18 工作流引擎 [部分]   DAG 就绪队列（JOB_CHANGE/INTERVIEW）+ 开关；legacy 6 节点脚手架
                       └─ L19 沙箱执行  Docker/本地进程隔离 + 5层防护
                       └─ L20 访问控制与治理 [闭环]  投票式决策接到 ToolCallAgent / Escape / Negotiation
                       └─ L21 Agent 注册中心 [脚手架]   YAML声明式 + Marketplace就绪
                       └─ L22 评测中心 [部分]   路由门禁闭环 + 内容评测可选 live 闭环
                       └─ L23 Prompt 版本管理 [脚手架]   多版本 + 灰度发布 + A/B测试
                       └─ L24 交付物生命周期  DRAFT→REVIEWING→APPROVED→PUBLISHED
                       └─ L25 事件总线  异步治理事件 + 审计日志
                       └─ L26 安全防护  循环检测 + 工具结果分级 + Token预算 + Observation清洗 + 连续失败熔断 + Goal Anchor + 感知预处理 + 工具幂等
                       └─ L27 分层记忆系统  四层记忆 + 异步提取 + Token预算分配
                       └─ L28 性能监控与诊断  Actuator + Micrometer + 断路器 + 诊断端点
                       └─ L29 经典范式支持  ReAct / Plan-and-Solve(+Replanner) / Reflection / Loop Wrap-up
                       └─ L30 上下文工程优化  相关性评分 + 动态预算分配 + 关键信息提取
                       └─ L31 工具注册机制  动态注册表 + 能力发现 + 健康监控
                       └─ L32 Reflexion 失败记忆  失败轨迹记录 + 自动注入提示词
                       └─ L33 RAG Rerank  关键词重叠 + 文档质量评分 + 位置偏差
                       └─ L34 感知层 Perception [闭环]  文档降维 + SharedState 绑定 + 感知路由 + 视觉注入防护
横切关注点：JWT 鉴权（access/refresh）· 日配额 · HITL · 会话三态 · 双存储（file/jdbc）· AppService 编排 · 全局异常处理 · Goal Anchor
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

在基础对话之上叠加检索增强：内置职场 Markdown 文档 + 用户动态上传（`.md` / `.pdf`）。

| 组件 | 职责 |
|------|------|
| `AiChatDocumentLoader` | 启动加载 classpath Markdown |
| `RetrievalPipeline` | **统一管线**：Query Rewrite → Multi-Query/HyDE → Rerank（含时间衰减） |
| `RagTool` | Agent 可调用的 `searchKnowledgeBase`（已注册 ToolRegistration） |
| `RagRetrievalAttemptTracker` | 同 chat 空检索上限，防检索循环 |
| `QueryRewriter` / `MultiQueryRetriever` | 查询改写与多路召回 |
| `RerankService` | 关键词 + 质量 + 位置 + **`indexedAt` 时间衰减** |
| `PipelineRagAdvisorFactory` | ResumeAgent 等 Spring AI RAG Advisor |
| `DocumentAppService` | 上传/文本入库/列表/软删；PDF 表格结构化 |
| `PdfKnowledgeIngestionService` | PDF → 正文 chunk + 表格 Markdown chunk（`chunkType=table`） |
| `DocumentMetadataManager` | 生命周期 + SHA-256 去重 |
| `AiChatVectorStoreConfig` / `PgVectorVectorStoreConfig` | 向量库（内存 / PgVector） |

**入口**：
- 检索同步：`GET /api/ai/ai_chat/rag/sync`
- 文档 API：`POST /api/document/upload` · `POST /api/document/add` · `GET /api/document/list` · `DELETE /api/document/{docId}`
- 前端：`/knowledge`（`KnowledgeBase.vue`，双主题 · 分类 · 筛选 · 文本粘贴）

**教程对照**： [mm-agent-tutorial-ch5-落地.md](./mm-agent-tutorial-ch5-落地.md) · [场景对照总结](./mm-agent-tutorial-场景对照总结.md)

---

## L2 · 工具调用（Tool Calling）

让模型具备"动手"能力，工具统一在 `ToolRegistration` 注册为 `ToolCallback[]`。  
Schema 描述含 **WHEN / DO NOT / RETURNS**（防 Tool Confusion）；同轮多 tool 由 `ParallelToolCallingSupport` 并行扇出。

| 工具 | 类 | 用途 |
|------|----|------|
| 联网搜索 | `WebSearchTool` | 实时外网事实（SearchAPI）；与 RAG/抓取边界写在 description |
| 网页抓取 | `WebScrapingTool` | `scrapeWebPage` 同步；慢页用 `startScrapeWebPage`（Submit-Poll） |
| 文件操作 | `FileOperationTool` | 读写；大文件返回 `file_id`，细节用 `readFileChunk`；写入可 HITL + 幂等 |
| 资源下载 | `ResourceDownloadTool` | `downloadResource` / `startDownloadResource`（SSRF + 幂等） |
| 终端操作 | `TerminalOperationTool` | 沙箱执行 + HITL + 幂等（超时不自动重试） |
| PDF 生成 | `PDFGenerationTool` | `generatePDF` / `startGeneratePDF` |
| 异步任务查询 | `AsyncToolStatusTool` | `checkAsyncToolTask(taskId)` |
| 终止 | `TerminateTool` | 供 Agent 主动结束任务 |

**工程增强（Ch3）**：`ObservationSanitizer` · `ToolIdempotencyStore` · `ToolSideEffectPolicy` · `AsyncToolTaskService` · `FileHandleStore`。  
配置：`app.tools.idempotency-ttl-seconds` · `app.tools.async-task-ttl-seconds`。

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
- **SSE 协议**：每步推送 `Step N: ...`；无工具调用时回传模型正文（不再只返回「思考完成」）；结束发送 `[DONE]`，前端避免把正常关流误报为连接错误
- DashScope 额度不足时返回可读中文错误（`AllocationQuota.FreeTierOnly`）
- **Ch4 Loop 增强**：
  - 步数耗尽且未自终止 → `LoopWrapUp` 强制收尾（`AgentLoopResult.PARTIAL_SUCCESS`）
  - System Prompt 禁止「无 Tool Output 却声称已完成」；`CompletionClaimGuard` 校验
  - 嵌套深度 `AgentDepthContext`（默认 ≤3）
  - 非 NORMAL 工具结果注入 `StepReflector`

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

**入口分流（先于状态机）**：

| 用户意图 | 检测 | 行为 |
|----------|------|------|
| 查已有预约 / 今天日程 | `isScheduleInquiry` | 读 `AppointmentRepository`，列出当天或全部记录，**不**开填表 |
| 有什么可预约 / 服务目录 | `isServiceCatalogInquiry` | 返回 5 类一对一咨询服务目录，进入 `COLLECTING_INFO` 收集 topic |
| 明确新开预约 | Keyword/NLU → CONSULTATION | 状态机：姓名 → 联系方式 → 时间 → 确认 → 创建 |

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

**HITL 人工审批**：`app.hitl.calendar-require-approval=true`（默认开启）时，创建日历事件前需人工审批。`ConsultationAgent` 在进入 `CREATING_APPOINTMENT` 时检查 `HumanApprovalService`；未获批准则返回待审批提示（含 `approvalId`），调用方需 `POST /api/hitl/approve?approvalId=...` 确认后重新触发确认流程。终端工具（`TerminalOperationTool`）同样受 `app.hitl.terminal-require-approval` 网关保护。

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

### 共享交付物货架（Artifact Shelf 与 ArtifactPublisher）

```
生产者 Agent ──put(READY)──► ArtifactShelf ──ArtifactPublisher.publish()──► PUBLISHED
                                  │                                          │
                            ArtifactRepository                        前端展示推荐/采纳计数
                            (Jackson+JSON+RWLock)
```

- `ArtifactShelf`：放货 `put` / 读取 `get` / 查询 `query` / 消费标记 `markConsumed`
- **ArtifactPublisher**：结构化可复用交付物发布至 `PUBLISHED` 状态（跳过 REVIEWING/APPROVED 中间态）
- **智能回忆**：
  - `TASK` 作用域：当前会话专项任务交付物，对话上下文注入
  - `USER_PROFILE` 作用域：用户跨会话累积的结构化资产，用户侧智能推荐
- **状态机**：`DRAFT → REVIEWING → APPROVED → PUBLISHED → ARCHIVED`（或 ArtifactPublisher 直接 DRAFT → PUBLISHED）
- **OFFERED/ADOPTED 分类账**：不改变交付物生命周期，记录推荐次数与采纳次数（前端展示）
- **五数据员工显式闭环**：DataAnalystAgent、CareerCoachAgent、ProfileCuratorAgent、PromotionPlannerAgent、LearningResourceRecommenderAgent 按结构化交付物请求触发
- **用户侧闭环** `[闭环]`：`GET /api/artifact/mine?chatId=` · `GET /api/artifact/mine/{id}`（JWT 归属校验）；SSE `artifact-ready` 推送推荐计数与采纳计数
- **专家包** `[闭环]`：`classpath:packs/*.yaml` + `/api/pack/list` · `/api/pack/{id}/enabled`；启用包收窄技能匹配与数字员工模板
- **会话共享状态** `[闭环]`：`sessionstate/*` — 同对话框跨 Agent 可读的结构化 scratchpad（预约事实、handoff、activeGoal）；预约创建写入，ContextInjection 全专家注入；详见 `docs/interview-multi-agent-session-state.md`
- **技能沉淀** `[闭环]`：`GET /api/skill/list` · `POST /api/skill/draft-from-trace` · `POST /api/skill/save`
- **任务中心 / 沙箱可见** `[闭环]`：`GET /api/task/mine` · `GET /api/task/sandbox-policy`；HITL 持久化 + webhook 通知；FILE_WRITE 需确认

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
| `MessageStore` | 抽象存储；`FileMessageStore` / `JpaMessageStore` 按 `app.storage.type` 切换 |
| `PersistentMessageRepository` | 兼容门面（委托 MessageStore） |
| `ChatMemoryAdapter` | Truth ↔ ChatMemory 桥接 |

**存储模式**（`app.storage.type` / 环境变量 `STORAGE_TYPE`）：

| 模式 | 说明 |
|------|------|
| `file`（默认） | `{session.storage.dir}/messages/{chatId}.json`，适合本地演示 |
| `jdbc` | PostgreSQL 表 `messages`（Flyway `V1__init_schema.sql` + JPA `MessageEntity`） |

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

## L18 · 工作流引擎（Workflow Runtime）`[部分]`

### DAG 路径（已接主聊天，需开关）

配置：`workflow.dag.enabled`（默认 `false`；`application-local.yml` 为 `true`）。

| 模板 | 图结构 | 说明 |
|------|--------|------|
| JOB_CHANGE | RESUME ∥ NEGOTIATION → SYNTHESIZE | 跳槽：并行专家后综合 |
| INTERVIEW | RESUME → GENERAL → SYNTHESIZE | 面试：串行后综合 |

**执行**：`DagCompiler` → `DagWorkflowExecutor`（就绪队列 + `CompletableFuture` 并行）→ 真实 `AgentRunner` → `ResultAggregator.synthesizeDebate`。

**接入点**：`OrchestratorAgent.tryDagWorkflow`；Matcher 命中且 confidence ≥ 0.6；SSE `collaboration.mode=DAG_WORKFLOW` + `agent-progress`；Trace：`WORKFLOW_MATCH` / `TASK_EXECUTION` / `RESULT_AGGREGATION`。

**关键类**：`DagDefinition` / `DagNodeSpec` / `DagCompiler` / `DagWorkflowExecutor`；`WorkflowRuntime.startDag` 委托执行。

### Legacy list 节点（脚手架，未接主聊天）

| 节点类型 | 类 | 说明 |
|----------|-----|------|
| AgentNode | `AgentNode` | list 模式下仍为占位调度 |
| ToolNode / Condition / Parallel / Loop / Approval | 对应 node 类 | 顺序 idx 推进，未接 Orchestrator |

**工作流状态**：PENDING → RUNNING → PAUSED（等待审批）/ COMPLETED / FAILED / CANCELLED

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

**热路径（已接线）**：
- `ToolCallAgent.think()` 用 `PermittedToolFilter` 对 LLM **隐藏** DENY 工具
- `ParallelToolCallingSupport` 执行前再校验一次；拒绝时返回 permission denied observation，不调用工具
- `YuManus`（`yu-manus` 画像，admin）与 `EscapeAgent` / `NegotiationAgent` 按 YAML 过滤 `ToolCallback[]`
- YAML 中的 `rag.query` / `web.search` / `file.read` 等别名由 `ToolNameMatcher` 映射到真实 `@Tool` 方法名

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

**插件平台迁移（2026）**：`application.yml` 中 `platform.*` 开关控制 `AgentManifestRegistry`、`AgentRunnerRegistry`、`ManifestLoader` 等是否接管路由元数据（默认 `legacy` / `off`，不改变现有行为）。详见 [workpilot-plugin-platform-refactor-plan.md](./workpilot-plugin-platform-refactor-plan.md)。

---

## 产品体验 · 桌面萌宠 & 双主题

横切前端体验，不单独占 L 层编号。

| 组件 | 说明 |
|------|------|
| `CompanionPet.vue` | 全局悬浮入口（`AppLayout`），可拖拽、收起、右键菜单 |
| `PetRoom.vue` | SVG 小房间场景；CSS 变量 `--pet-room-*` 随 **sage / dark** 切换 |
| `CatPet.vue` / `PilotPet.vue` | 皮肤：小猫（PNG 多姿态）/ 领航员（SVG） |
| `useCompanion.js` | SSE 状态驱动 idle/thinking/celebrate；presence onChair/away |
| `useTheme.js` | 顶栏 🌿/🌙 切换 sage ↔ dark，`localStorage` 持久化 |

设置入口：职场顾问 →「我的伙伴」抽屉（`/api/companion/me`），非独立 Vue 路由。

---

## L22 · 评测中心（Eval Center）[部分]

Agent 质量评测框架：YAML 用例加载 → 执行 → 评分 → 回归检测。

**评测报告**：`EvalReport`（reportId、overallScore、passRate、regression、caseResults）

**路由套件（`[闭环]`）**：`routing-suite` 用 `KeywordRouter` 零 LLM 实跑评分，作为 CI/发版门禁（`POST /eval/gate/{suiteId}`，passRate < 0.8 或相对上次回归即失败）。

**内容套件（`[部分]`）**：默认 `POST /eval/run/{suiteId}` 仅做 `NOT_RUN` 占位（避免误触发 LLM 调用）；需要真实闭环时调用 `POST /eval/run/{suiteId}/live`（`EvalAppService.runContentLive` → 真实调用 `OrchestratorAgent.chat` → `EvalScorer.scoreContent` 关键词重叠评分），会消耗 LLM 配额。

**鉴权**：除 `/eval/suites` 外全部端点需登录（`AuthService.authenticate`），避免评测接口被滥用触发资源消耗。

**关键类**：`EvalCenter`（套件加载/执行/评分/回归）、`EvalAppService`（内容套件 live 闭环编排）、`EvalScorer`（路由/关键词重叠评分）、`EvalController`（REST API）。

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
| TIMEOUT | 超时 | 只读工具可自动重试；副作用工具不重试（防重复执行） |
| EMPTY | 空结果 | 建议换策略（不是重试能解决的） |
| GARBAGE | 垃圾内容 | 过滤后建议换关键词（登录墙/付费墙/堆栈跟踪） |
| NORMAL | 正常 | 不干预 |

### ObservationSanitizer（工具 Observation 清洗）

- 去 HTML / 省略长 Base64 / 压缩空白；超长截断并标注 `[System Note]`
- 在 `ToolCallAgent.act()` 写入历史前执行，防止 Context 污染（教程 Ch3 Sanitizer Layer）
- 与 `TokenBudgetManager` 互补：Sanitizer 管「脏数据」，Budget 管「档位压缩」

### TokenBudgetManager（Token 预算分级）

| 模式 | 触发条件 | 策略 |
|------|----------|------|
| Normal | < 65% | 搜索结果保留 3000 字符，无截断 |
| Compact | 65% ~ 85% | 搜索结果截断至 1500 字符 |
| Compress | > 85% | 用 LLM 压缩历史 Observation，摘要化 |

**关键取舍**：Think（AssistantMessage）绝对不动，只压缩 Observation（ToolResponseMessage）。因为"思考轨迹"比"原始搜索结果"对推理更有价值。

### ConsecutiveFailureGuard（连续失败熔断）

- Manus / `ToolCallAgent` 内：非 NORMAL 工具结果或 think 异常累计
- 达阈值（默认 `app.hitl.max-consecutive-tool-errors=3`）→ 终止循环，可选 `HumanHandoffService.park`
- 成功一次清零；与 LoopDetector 互补（Loop 管「重复调用」，本守卫管「连续失败」）
- 专业术语：**Fail-fast**、**Circuit-like fuse**、**Human-in-the-loop (HITL) escalation**

### Goal Anchor（本轮目标重插）

- `GoalAnchor` + `ContextInjectionService`：专家路由每轮注入 `【本轮任务目标】`（注入链最前）
- `OrchestratorAgent`：会话无 `activeGoal` 时用本轮用户话种入 SharedState
- `ToolCallAgent.think()`：ReAct 每步把目标挂回 **system prompt**，防止长循环遗忘
- 专业术语：**Goal grounding**、**Context forgetting mitigation**、**System-prompt re-anchoring**

### Perception 感知预处理（L26 交叉 / 详见 L34）

- 入口：`POST /api/perception/preprocess`（调试）与 **`POST /api/perception/preprocess-and-bind`**（联调推荐）
- 绑定路径：材料写入 `SessionSharedState.lastPerceptionBlock`，SSE 只发短句（规避 EventSource GET URL 长度限制）
- 感知路由：`suggestIntentFromPerception` → 有绑定材料时跳过模糊 NLU 澄清，直达 RESUME/NEGOTIATION
- 详见下方 **L34** 与 [mm-agent-tutorial-ch1-落地.md](./mm-agent-tutorial-ch1-落地.md)

---

## L34 · 感知层 Perception（文档降维 → Agent）

> 状态：`[闭环]`（职场顾问上传 → bind → 路由专家 → 注入分析）。混合检索 / OCR / VLM 精读为扩展桩。

源自多模态 Agent 教程「Perception 不是把像素扔给 VLM，而是降维成语义流」与 **Budget Awareness（预算感知）**：先用便宜解析（PDFBox / POI / 启发式结构化），再进专家 Agent。

### 端到端链路

```
前端 📎 上传 (resume.txt / PDF / docx)
    → POST /api/perception/preprocess-and-bind
         DocumentPerceptionService（抽文本 + ResumeOfferStructurer）
         VisualPromptSanitizer（图片重采样 / 注入 scrub）
         SessionSharedState.setPerceptionBlock + setActiveGoal
    → SSE 短消息（含「简历/Offer」关键词或靠感知路由）
         Orchestrator：perceptionIntent 优先于模糊 NLU
         ContextInjection：Goal Anchor + SharedState（感知块置顶，上限 5000 字）
    → ResumeAgent / NegotiationAgent 流式回答
```

### 组件与技术

| 组件 | 路径 | 技术 / 术语 |
|------|------|-------------|
| `DocumentPerceptionService` | `perception/` | PDFBox 文字层；Apache POI `.docx`；启发式 **Information Extraction** |
| `ResumeOfferStructurer` | 同上 | 零 LLM 抽 email/phone/薪资/学历（**cheap first pass**） |
| `VisualPromptSanitizer` | 同上 | JPEG 重压缩抗对抗样本；文本 **Prompt Injection scrub** |
| `PerceptionCrossValidator` | 同上 | 感知假设 vs 工具观测交叉验证（防 **Perceptual Hallucination**） |
| `TextFirstHybridRetrieval` | `rag/hybrid/` | Text-first Hybrid Retrieval 桩（Caption Top-1，不全量塞图） |
| `PerceptionAppService` | `service/` | Controller → AppService → Domain 分层 |
| 前端 | `CareerAdvisor.vue` | `preprocessPerceptionAndBind`；类型 hint=resume/offer |

### 格式支持

| 格式 | 状态 |
|------|------|
| `.txt` / `.md` / `.csv` | ✅ |
| `.pdf`（可选中文字） | ✅ PDFBox |
| `.pdf` 扫描件 | ❌ 无 OCR |
| `.docx` | ✅ POI |
| `.doc` | ❌ 请另存 docx |
| 图片 | ⚠️ 仅净化，无 OCR |

### 联调踩坑（已修，面试可讲）

1. **默认话术过泛** → NLU Ambiguity →「多个领域能否具体描述」→ 改为带「简历/Offer」默认句 + `suggestIntentFromPerception` 快路径  
2. **SSE EventSource 只能 GET** → 长 `promptBlock` 撑爆 URL → **preprocess-and-bind** 写 SharedState  
3. **SharedState 1800 字截断**把感知块砍掉 → 感知块置顶 + 有感知时上限 5000  

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
| 鉴权 | `JwtUtil` + `AuthService` + `AccountService` | Access JWT + Refresh Token；SSE 的 token 走 URL 参数；注册/登录/刷新 |
| 日配额 | `UserQuotaService` | 按 GUEST/USER/ADMIN 限制每日 chats 与 tokens |
| HITL | `HumanApprovalService` | 终端命令、日历创建等高危副作用需人工审批 |
| 双存储 | `app.storage.type=file\|jdbc` | 消息/轨迹可落文件或 PostgreSQL |
| 会话三态 | `SessionManager` | ACTIVE / ARCHIVED / DELETED（软删除）；`chatOwner` 反向索引防越权 |
| AppService 编排 | `OrchestratorAppService` 等 | 输入校验、归属检查、用量追踪、编排 Agent/Repository |
| 异常处理 | `GlobalExceptionHandler` | 全局统一异常响应 |
| 统一响应 | `common/Response` + `ResultCode` | 标准化返回结构 |
| 结构化输出 | `AiChatAgent.AiChatReport` | 职场报告结构化（victools jsonschema） |
| 健康检查 | `HealthController` | 探活 |
| 跨域 | `CorsConfig` | 前端联调 |

---

## 数据存储一览（file / jdbc 双模式）

默认 **`app.storage.type=file`**：以文件 + JSON/Kryo 作为演示持久层。生产可切 **`jdbc`**：PostgreSQL + Flyway + Spring Data JPA（见 `repository/entity`、`repository/jpa`、`db/migration/V1__init_schema.sql`）。可用 `docker-compose.yml` 一键起 `pgvector/pgvector:pg16`。

| 逻辑"表" | file 位置（默认） | jdbc 表 / 组件 | 关键字段 |
|----------|------------------|----------------|----------|
| 会话 sessions | `./tmp/sessions/sessions.json` | `chat_sessions` / `ChatSessionEntity` | chatId、userId、title、status |
| 消息 messages | `./tmp/sessions/messages/{chatId}.json` | `messages` / `JpaMessageStore` | messageId、chatId、role、content、status |
| 轨迹 traces | `./tmp/traces/` | `traces` / `JpaTraceStore` | traceId、spans、userId、chatId |
| 用户 accounts | `./tmp/auth/` | `users` / `UserEntity` | userId、username、role、passwordHash |
| 预约 appointments | `./tmp/appointments/` | `appointments` | name、contact、appointmentTime |
| 交付物 artifacts | `./tmp/artifacts/artifacts.json` | `artifacts` | artifactId、type、status |
| 用户画像 user-profiles | `./tmp/user-profiles/` | `user_profiles` | userId、preferences、focusAreas |
| 向量库 | PgVector / 内存 | PostgreSQL / SimpleVectorStore | embedding + 元数据 |

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
| `spring.ai.dashscope.chat.options.model` | `qwen3.7-max` | 主模型 |
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
| `app.hitl.max-consecutive-tool-errors` | `3` | 工具连续失败熔断阈值，触发后可 HITL park |
| `app.tools.idempotency-ttl-seconds` | `600` | 副作用工具幂等缓存 TTL |
| `app.tools.async-task-ttl-seconds` | `3600` | Submit-Poll 异步任务保留时长 |
| `app.hitl.*` | — | 终端/日历/写文件审批开关与 TTL |

---

## API 速查

| 分类 | 方法 | 路径 |
|------|------|------|
| 注册/登录 | POST | `/api/session/register` · `/api/session/login` · `/api/session/refresh` · `/api/session/logout` |
| 当前用户 | GET | `/api/session/me`（JWT） |
| 基础对话 | GET | `/api/ai/ai_chat/chat/sync` · `/sse` · `/server_sent_event` · `/sse_emitter` |
| 智能路由 | GET | `/api/ai/orchestrator/chat`（JWT，SSE token 走 query） |
| 续传 | GET | `/api/ai/orchestrator/chat/resume`（JWT） |
| Manus | GET | `/api/ai/manus/chat` |
| RAG | GET | `/api/ai/ai_chat/rag/sync` |
| 工具 | GET | `/api/ai/ai_chat/tools/sync` |
| MCP | GET | `/api/ai/ai_chat/mcp/sync` |
| 结构化报告 | GET | `/api/ai/ai_chat/report/sync` |
| 文档入库 | POST | `/api/document/upload` · `/api/document/add` |
| 感知预处理 | POST | `/api/perception/preprocess` · `/api/perception/preprocess-and-bind` · `/api/perception/cross-check` |
| 用户画像 | GET/DELETE | `/api/profile/me`（JWT） |
| 交付物 | GET | `/api/artifact/mine` · `/api/artifact/mine/{id}`（JWT）· `/api/artifact/list` · `/api/artifact/{id}`（管理员） |
| 专家包 | GET/POST | `/api/pack/list` · `/api/pack/{packId}/enabled`（JWT） |
| 技能 | GET/POST | `/api/skill/list` · `/api/skill/draft-from-trace` · `/api/skill/save`（JWT） |
| 任务中心 | GET | `/api/task/mine` · `/api/task/sandbox-policy`（JWT） |
| 会话 | - | `SessionController`（增删查/归档/搜索/消息历史） |
| 收藏 | POST/DELETE/GET | `/api/favorite` · `/api/favorite/{id}` · `/api/favorite/list`（JWT） |
| 用量 | GET | `/api/usage/stats`（JWT） |
| 导入导出 | GET/POST | `/api/export/all` · `/api/export/import`（JWT） |
| 轨迹 | GET | `/api/trace/{id}` · `/api/trace/chat/{chatId}` · `/api/trace/user/{userId}` |
| 人工审批（HITL） | GET/POST | `/api/hitl/{approvalId}` · `/api/hitl/approve` · `/api/hitl/reject`（JWT） |
| 评测中心 | GET/POST | `/api/eval/suites` · `/api/eval/run/{suiteId}` · `/api/eval/run/{suiteId}/live` · `/api/eval/gate/{suiteId}`（除 suites 外需 JWT） |
| 健康 | GET | `/api/health` |
