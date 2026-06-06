# 全场景职场生存智囊 Agent · 渐进式功能文档

> 本文档按"能力由浅入深"的顺序梳理项目功能，每一层都建立在前一层之上。
> 适用于：个人学习复盘、作品集讲解、面试技术亮点串讲。
>
> 技术底座：Java 21 + Spring Boot 3.4 + Spring AI 1.0（Alibaba DashScope）+ Ollama + PgVector。

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

`ConsultationAgent` 通过追问状态机收集信息并对接企业日历。

```
INITIAL → COLLECTING_INFO → CONFIRMING → CREATING_APPOINTMENT → COMPLETED
```

| 组件 | 职责 |
|------|------|
| `FollowUpTemplateConfig` | 追问模板（姓名/联系方式/时间），支持热更新 |
| `InfoValidator` | 信息格式校验 |
| `CalendarService` + `CalendarServiceFactory` | 日历服务抽象 |
| `FeishuCalendarService` / `DingTalkCalendarService` | 飞书 / 钉钉日历实现 |
| `AppointmentRepository` | 预约记录持久化 |

**核心信息**（必填，模板化追问）：姓名、联系方式、预约时间。**非核心信息**（AI 智能追问）：咨询主题、备注。

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
- **状态机**：`PENDING → READY → CONSUMED`

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
| `PersistentChatMessage` | 消息实体（ULID messageId + chatId + role + content + timestamp） |
| `PersistentMessageRepository` | 双索引持久化（chatIndex + messageIdIndex） |
| `ChatMemoryAdapter` | Truth ↔ ChatMemory 桥接（写入先持久化再同步缓存，读取先检查一致性） |

**存储**：`{session.storage.dir}/messages/{chatId}.json`，每个会话一个文件。

**压缩支持**：`replaceWithSummary(chatId, summary, keepRecent)` — 压缩时替换旧消息为摘要 + 最近 N 条。

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
| 消息 messages | `./tmp/sessions/messages/{chatId}.json` | `PersistentMessageRepository` | messageId(ULID)、chatId、role、content、timestamp；双索引 chatIndex + messageIdIndex |
| 预约 appointments | `./tmp/appointments/` | `AppointmentRepository` | name、contact、appointmentTime、calendarEventId、calendarUrl、provider、status、chatId、createdAt |
| 交付物 artifacts | `./tmp/artifacts/artifacts.json` | `ArtifactRepository` | artifactId、userId、chatId、type、producer、title、content、status、scope、createdAt、updatedAt |
| 用户画像 user-profiles | `./tmp/user-profiles/` | `UserProfileRepository` | userId、communicationPreference、tonePreference、focusAreas[]、knownBackground、historicalDemands[]、createdAt、updatedAt |
| 收藏 favorites | `./tmp/artifacts/favorites.json` | `FavoriteRepository` | favoriteId、userId、chatId、messageId、contentSnapshot、sessionTitleSnapshot、role、orphaned |
| 质量审查 quality-reviews | `./tmp/artifacts/quality-reviews.json` | `QualityReviewRepository` | reviewId、chatId、mode、5 维评分、riskLevel、issues[]、suggestions[]（仅 HIGH/CRITICAL） |
| 用量事件 usage-events | `./tmp/artifacts/usage-events.json` | `UsageTracker` | eventId、userId、type、agentType、durationMs、timestamp（append-only） |
| 对话记忆 chat-memory | 文件（Kryo） | `FileBasedChatMemory` | chatId → List<Message>（按 agent 类型隔离） |
| 向量库 | PgVector / 内存 | `*VectorStoreConfig` | 文档 embedding + 元数据（filename、status） |

### 枚举类型

| 枚举 | 取值 |
|------|------|
| `AgentIntent` | RESUME、NEGOTIATION、ESCAPE、CONSULTATION、GENERAL |
| `ArtifactStatus` | PENDING、READY、CONSUMED |
| `ArtifactScope` | USER_PROFILE、TASK |
| `CommunicationPreference` | CONCISE、DETAILED |
| `AnalysisSource` | CONVERSATION、UPLOADED_DOCUMENT |
| `AppointmentStatus` | PENDING、CONFIRMED、COMPLETED、CANCELLED、FAILED |
| `CalendarProvider` | FEISHU、DINGTALK |
| `SessionStatus` | ACTIVE、ARCHIVED、DELETED |
| `QualityMode` | OFF、AUTO、REVIEW、RED_TEAM |
| `RiskLevel` | LOW、MEDIUM、HIGH、CRITICAL |
| `UsageEventType` | CHAT、RAG、TOOL_CALL、DOCUMENT_UPLOAD、EXPORT、COMPARE、QUALITY_REVIEW |

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
|| 会话 | - | `SessionController`（增删查/归档/搜索/消息历史） |
|| 收藏 | POST/DELETE/GET | `/api/favorite` · `/api/favorite/{id}` · `/api/favorite/list`（JWT） |
|| 用量 | GET | `/api/usage/stats`（JWT） |
|| 导入导出 | GET/POST | `/api/export/all` · `/api/export/import`（JWT） |
|| API 文档 | - | `/api/swagger-ui.html`（Knife4j） |
```
