# 职场生存智囊 — 项目 Wiki

> AI Agent 全场景职场决策系统  
> 生成时间：2026-06-06  
> 技术栈：Spring Boot 3 + Spring AI + DashScope (Qwen) + Vue 3

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 快速开始](#2-快速开始)
- [3. 架构总览](#3-架构总览)
- [4. 功能模块](#4-功能模块)
  - [4.1 Multi-Agent 智能路由](#41-multi-agent-智能路由)
  - [4.2 预约咨询 Agent](#42-预约咨询-agent)
  - [4.3 数据员工 Agent](#43-数据员工-agent)
  - [4.4 用户画像系统](#44-用户画像系统)
  - [4.5 交付物货架](#45-交付物货架)
  - [4.6 执行轨迹可视化](#46-执行轨迹可视化)
  - [4.7 对话记忆管理](#47-对话记忆管理)
  - [4.8 RAG 知识库](#48-rag-知识库)
  - [4.9 工具集](#49-工具集)
  - [4.10 日历集成](#410-日历集成)
  - [4.11 会话管理](#411-会话管理)
  - [4.12 认证与鉴权](#412-认证与鉴权)
  - [4.13 质量守护](#413-质量守护)
  - [4.14 收藏系统](#414-收藏系统)
  - [4.15 用量追踪](#415-用量追踪)
  - [4.16 数据导入导出](#416-数据导入导出)
  - [4.17 对话搜索](#417-对话搜索)
  - [4.18 持久化消息](#418-持久化消息)
- [5. API 参考](#5-api-参考)
- [6. 数据字典](#6-数据字典)
- [7. 前端指南](#7-前端指南)
- [8. 配置参考](#8-配置参考)

---

## 1. 项目概述

「职场生存智囊」是一个基于大语言模型的 AI Agent 平台，核心能力是**根据用户意图自动路由到专业子 Agent**，覆盖简历优化、薪资谈判、离职规划、预约咨询等职场场景。

### 核心特性

| 特性 | 说明 |
|------|------|
| 智能路由 | OrchestratorAgent 根据意图自动分发给专业子 Agent |
| 多轮对话 | 支持上下文记忆、自动压缩、Token 阈值触发 |
| 用户画像 | 自动从对话中提取用户特征，注入后续对话 |
| 交付物管理 | Agent 生产的结构化成果（简历、报告等）持久化存储 |
| 执行轨迹 | 全链路 trace，实时 SSE 推送 + 历史查询 |
| RAG 知识库 | 文档上传 → 向量化 → 多路召回增强回答 |
| 工具调用 | Web 搜索、文件操作、PDF 生成、终端执行等 |
| 日历集成 | 飞书/钉钉日历自动创建预约事件 |
| 质量守护 | 自动审查 AI 输出的准确性、幻觉风险、安全隐患 |
| 收藏系统 | 收藏消息快照，会话删除后不丢失 |
| 用量追踪 | 按用户/Agent/时间维度统计使用量 |
| 数据导入导出 | ZIP 格式全量备份与恢复 |
| 对话搜索 | 跨会话加权搜索（标题+内容+时间衰减） |
| 持久化消息 | 消息 Source of Truth，支持历史回溯与搜索 |

---

## 2. 快速开始

### 环境要求

- JDK 21
- Maven 3.8+
- Node.js 18+（前端）
- DashScope API Key（Qwen 模型）

### 启动后端

```bash
# 设置环境变量
export DASHSCOPE_API_KEY=your-key
export JWT_SECRET=your-secret

# 编译运行
mvn spring-boot:run
# 默认端口 8123，context-path /api
```

### 启动前端

```bash
cd yu-ai-agent-frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

---

## 3. 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (Vue 3)                         │
│  Home → CareerAdvisor / SuperAgent / LoveMaster             │
│  Components: ChatRoom / TraceTimelineView                   │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP / SSE
┌──────────────────────▼──────────────────────────────────────┐
│                  Controller Layer                            │
│  AiController · TraceController · SessionController         │
│  ProfileController · ArtifactController · DocumentController│
│  FavoriteController · UsageController · ExportController     │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│               AppService Layer (业务编排)                     │
│  OrchestratorAppService (校验/归属/用量追踪)                  │
│  SessionAppService (CRUD/归档/搜索/消息历史)                  │
│  FavoriteAppService · ExportAppService · DocumentAppService  │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                  Agent Layer (核心)                          │
│  OrchestratorAgent (路由)                                    │
│    ├─ ConsultationAgent (预约咨询)                           │
│    ├─ ResumeAgent (简历优化)                                 │
│    ├─ NegotiationAgent (薪资谈判)                            │
│    ├─ EscapeAgent (离职规划)                                 │
│    ├─ GeneralCareerAgent (通用顾问)                          │
│    └─ YuManus (超级智能体，工具调用)                          │
│  DataEmployeeAgent (数据员工族)                              │
│    ├─ DataAnalystAgent                                       │
│    ├─ CareerCoachAgent                                       │
│    ├─ ProfileCuratorAgent                                    │
│    ├─ PromotionPlannerAgent                                  │
│    └─ LearningResourceRecommenderAgent                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                  Infrastructure Layer                        │
│  ChatMemoryManager (记忆压缩) · UserProfileService (画像)    │
│  ArtifactShelf (交付物) · TraceRecorder (轨迹采集)           │
│  SkillRegistry (技能匹配) · CalendarService (日历)           │
│  SessionManager (三态会话) · AuthService (JWT)               │
│  QualityGuardAgent (质量审查) · UsageTracker (用量统计)       │
│  ChatSearchService (对话搜索) · PersistentMessageRepository  │
│  FavoriteRepository (收藏) · DataExportService (导入导出)    │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                  Storage Layer                               │
│  File-based JSON: sessions / appointments / artifacts        │
│  File-based JSON: user-profiles / traces / messages          │
│  File-based JSON: favorites / quality-reviews / usage-events │
│  PgVector: RAG 向量存储（可选）                               │
└─────────────────────────────────────────────────────────────┘
```

### 分层职责

| 层 | 职责 | 禁止 |
|----|------|------|
| Controller | 参数绑定、认证、调用 AppService、返回 Result\\<T\\> | 业务逻辑、try-catch |
| AppService | 输入校验、归属检查、用量追踪、编排 Agent/Repository | LLM 交互 |
| Agent | LLM 交互、意图识别、工具编排 | 直接操作存储 |
| Repository | 文件持久化、JSON 序列化、并发安全 | — |

---

## 4. 功能模块

### 4.1 Multi-Agent 智能路由

**入口**: `GET /api/ai/orchestrator/chat?message=xxx&chatId=xxx`

**流程**:
```
用户消息 → OrchestratorAgent
  ├─ 1. 技能匹配 (SkillRegistry)
  ├─ 2. 意图识别 (LLM 分类)
  ├─ 3. 路由分发
  │    ├─ 简历相关 → ResumeAgent
  │    ├─ 薪资相关 → NegotiationAgent
  │    ├─ 离职相关 → EscapeAgent
  │    ├─ 预约咨询 → ConsultationAgent
  │    └─ 其他 → GeneralCareerAgent / YuManus
  ├─ 4. 画像注入 (UserProfile → system prompt)
  ├─ 5. 交付物查询 (ArtifactShelf)
  └─ 6. 流式响应 (SSE)
```

**SSE 事件类型**:
- `routing` — 路由决策（"已为您匹配简历优化专家"）
- `message` — 流式回答片段
- `trace` — 执行轨迹事件
- `error` — 错误信息

**关键类**:
- `OrchestratorAgent` — 路由编排核心
- `AgentIntent` — 意图枚举

---

### 4.2 预约咨询 Agent

**功能**: 多轮对话收集预约信息 → 校验 → 创建日历事件

**核心信息收集** (`CoreInformation`):

| 字段 | 类型 | 说明 |
|------|------|------|
| name | String | 咨询者姓名 |
| contact | String | 联系方式（手机/微信） |
| appointmentTime | LocalDateTime | 预约时间 |
| topic | String | 咨询主题 |

**流程**:
```
用户消息 → 意图识别 → 信息完整性检查
  ├─ 缺失 → 追问模板 (FollowUpTemplateConfig)
  │   ├─ 非法输入校验 (InfoValidator)
  │   └─ 重试提示
  └─ 完整 → 确认 → 创建预约 (AppointmentRepository)
       └─ 创建日历事件 (CalendarService)
           ├─ 飞书 (FeishuCalendarService)
           └─ 钉钉 (DingTalkCalendarService)
```

**关键类**:
- `ConsultationAgent` — 预约咨询主 Agent
- `InfoValidator` — 输入校验（手机号、时间格式等）
- `AppointmentRepository` — 预约记录持久化
- `CalendarServiceFactory` — 日历服务工厂

---

### 4.3 数据员工 Agent

**概念**: 一组专业化 Agent，各自负责特定的数据分析和建议任务。

| Agent | 职责 |
|-------|------|
| DataAnalystAgent | 职场数据分析、行业趋势 |
| CareerCoachAgent | 职业规划教练 |
| ProfileCuratorAgent | 用户画像维护 |
| PromotionPlannerAgent | 晋升路径规划 |
| LearningResourceRecommenderAgent | 学习资源推荐 |

**产出**: 每个 Agent 可生成 `ProductionResult`（结构化交付物），存入 ArtifactShelf。

**关键类**:
- `DataEmployeeAgent` — 数据员工基类
- `ProductionContext` — 生产上下文
- `ProductionResult` — 生产结果

---

### 4.4 用户画像系统

**功能**: 自动从对话中提取用户特征，持久化存储，在后续对话中注入 system prompt。

**画像维度** (`UserProfile`):

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | String | 用户 ID |
| communicationPreference | CommunicationPreference | 沟通偏好 |
| tonePreference | String | 语气偏好 |
| focusAreas | List\<String\> | 关注领域 |
| knownBackground | String | 已知背景 |
| historicalDemands | List\<String\> | 历史诉求 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

**沟通偏好枚举** (`CommunicationPreference`):

| 值 | 说明 |
|----|------|
| DETAILED | 详细分析型 |
| CONCISE | 简洁直接型 |
| FRIENDLY | 友好亲切型 |
| PROFESSIONAL | 专业严谨型 |

**流程**:
```
对话结束 → UserProfileExtractor (LLM 提取)
  → UserProfileRepository (持久化)
  → 下次对话 → ProfilePromptBuilder (注入 system prompt)
```

**API**:
- `GET /api/profile/me` — 查看当前画像
- `DELETE /api/profile/me` — 清空画像

**关键类**:
- `UserProfileService` — 画像编排
- `UserProfileExtractor` — LLM 提取器
- `ProfilePromptBuilder` — 提示词注入

---

### 4.5 交付物货架

**功能**: Agent 生产的结构化成果（简历、报告、分析等）持久化存储，支持按用户/会话/类型查询。

**Artifact 模型**:

| 字段 | 类型 | 说明 |
|------|------|------|
| artifactId | String | 唯一 ID |
| userId | String | 所属用户 |
| chatId | String | 所属会话 |
| type | String | 交付物类型 |
| producer | String | 生产者 Agent |
| title | String | 标题 |
| content | String | 完整内容（JSON 字符串） |
| status | ArtifactStatus | 状态 |
| scope | ArtifactScope | 作用域 |

**状态枚举** (`ArtifactStatus`):

| 值 | 说明 |
|----|------|
| PENDING | 生产中 |
| READY | 可消费 |
| CONSUMED | 已消费 |

**作用域枚举** (`ArtifactScope`):

| 值 | 说明 |
|----|------|
| USER_PROFILE | 用户画像级（按 userId 跨会话累积） |
| TASK | 任务级（按 chatId 会话级） |

**API**:
- `GET /api/artifact/list` — 管理员查询列表
- `GET /api/artifact/{artifactId}` — 查看详情

**关键类**:
- `ArtifactShelf` — 交付物货架（存取编排）
- `ArtifactRepository` — 文件持久化

---

### 4.6 执行轨迹可视化

**功能**: 全链路执行轨迹采集，支持实时 SSE 推送和历史查询。

**数据模型**:

```
ExecutionTrace
  ├─ traceId (String)
  ├─ userId (String)
  ├─ chatId (String)
  ├─ requestId (String)
  ├─ status (TraceStatus)
  ├─ startTime (Instant)
  ├─ endTime (Instant)
  └─ spans (List<TraceSpan>)
       ├─ sequence (int)
       ├─ stepType (TraceStepType)
       ├─ label (String)
       ├─ status (TraceStepStatus)
       ├─ startTime (Instant)
       ├─ endTime (Instant)
       ├─ errorMessage (String)
       └─ metadata (Map<String, String>)
```

**TraceStatus 枚举**:

| 值 | displayName | 终态 |
|----|-------------|------|
| RUNNING | 执行中 | ✗ |
| SUCCESS | 成功 | ✓ |
| FAILED | 失败 | ✓ |
| CANCELLED | 已取消 | ✓ |

**TraceStepStatus 枚举**:

| 值 | displayName | 终态 |
|----|-------------|------|
| RUNNING | 执行中 | ✗ |
| SUCCESS | 成功 | ✓ |
| FAILED | 失败 | ✓ |
| SKIPPED | 已跳过 | ✓ |

**TraceStepType 枚举（10 种步骤类型）**:

| 值 | displayName | 采集位置 |
|----|-------------|----------|
| SKILL_MATCH | 技能匹配 | OrchestratorAgent |
| INTENT_DETECTION | 意图识别 | OrchestratorAgent |
| ROUTING | 路由分发 | OrchestratorAgent |
| PROFILE_INJECTION | 画像注入 | OrchestratorAgent |
| ARTIFACT_QUERY | 交付物查询 | OrchestratorAgent |
| ARTIFACT_CONSUME | 交付物消费 | OrchestratorAgent |
| SUB_AGENT_EXECUTION | 子Agent执行 | OrchestratorAgent |
| TOOL_CALL | 工具调用 | ToolCallAgent |
| MEMORY_COMPRESSION | 记忆压缩 | ChatMemoryManager |
| PROFILE_UPDATE | 画像更新 | OrchestratorAgent (异步) |

**SSE trace 事件格式**:
```json
{
  "type": "SPAN_STARTED | SPAN_ENDED | TRACE_STARTED | TRACE_COMPLETED | TRACE_FAILED",
  "sequence": 0,
  "stepType": "INTENT_DETECTION",
  "stepTypeDisplayName": "意图识别",
  "label": "识别用户职场意图",
  "status": "SUCCESS",
  "errorMessage": null
}
```

**API**:
- `GET /api/trace/{traceId}` — 查询单条轨迹
- `GET /api/trace/chat/{chatId}?pageNum=1&pageSize=20` — 按会话查询（分页）
- `GET /api/trace/user/{userId}?pageNum=1&pageSize=20` — 按用户查询（分页）

**配置**:

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| trace.storage.dir | ./tmp/traces | 存储目录 |
| trace.stream.enabled | true | 实时 SSE 推送开关 |
| trace.max-spans-per-trace | 200 | 单轨迹最大 span 数 |
| trace.metadata.max-value-chars | 2000 | metadata 值最大字符数 |
| trace.max-traces-per-user | 500 | 单用户保留轨迹上限 |

**关键类**:
- `TraceRecorder` — 采集门面（try-catch 容错）
- `TraceContext` — 请求级上下文
- `TraceStreamPublisher` — SSE 推送
- `TraceRepository` — 文件持久化 + 保留策略

---

### 4.7 对话记忆管理

**功能**: 自动管理对话历史，支持 Token 阈值和对话轮数两种压缩触发策略。

**压缩策略**:

| 策略 | 触发条件 | 说明 |
|------|----------|------|
| TokenCompressionStrategy | Token 数 > threshold | 基于 Token 计数 |
| TurnCompressionStrategy | 对话轮数 > threshold | 基于轮数计数 |

**压缩流程**:
```
对话进行中 → 检查是否触发压缩
  ├─ 否 → 继续
  └─ 是 → MemoryCompressor (LLM 摘要)
       ├─ 保留最近 N 轮完整对话
       └─ 历史对话 → 压缩摘要 (CompressedMemory)
```

**配置**:

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| chat.memory.compression.token-threshold | 4000 | Token 触发阈值 |
| chat.memory.compression.turn-threshold | 20 | 轮数触发阈值 |
| chat.memory.compression.recent-turns | 5 | 保留最近轮数 |

**关键类**:
- `ChatMemoryManager` — 记忆管理编排
- `MemoryCompressor` — LLM 压缩器
- `FileBasedChatMemory` — 文件持久化对话历史

---

### 4.8 RAG 知识库

**功能**: 文档上传 → 文本分割 → 向量化 → 存储 → 多路召回增强回答。

**流程**:
```
文档上传 (DocumentController)
  → AiChatDocumentLoader (加载解析)
  → MyTokenTextSplitter (文本分割)
  → PgVectorVectorStore (向量化存储)

用户提问
  → MultiQueryRetriever (多路召回)
  → QueryRewriter (查询改写)
  → AiChatRagCustomAdvisorFactory (RAG 增强)
  → LLM 回答
```

**关键类**:
- `MultiQueryRetriever` — 多查询召回器
- `QueryRewriter` — 查询改写器
- `AiChatDocumentLoader` — 文档加载器
- `PgVectorVectorStoreConfig` — PgVector 向量存储配置

---

### 4.9 工具集

**功能**: YuManus 超级智能体可调用的外部工具。

| 工具 | 类 | 功能 |
|------|-----|------|
| Web 搜索 | WebSearchTool | 联网搜索信息 |
| 网页抓取 | WebScrapingTool | 抓取网页内容 |
| 文件操作 | FileOperationTool | 读写本地文件 |
| PDF 生成 | PDFGenerationTool | 生成 PDF 文档 |
| 资源下载 | ResourceDownloadTool | 下载网络资源 |
| 终端执行 | TerminalOperationTool | 执行 shell 命令 |
| 终止工具 | TerminateTool | 结束任务 |

**注册**: `ToolRegistration` 类统一注册所有工具为 `ToolCallback[]`。

---

### 4.10 日历集成

**功能**: 创建/取消日历事件，支持飞书和钉钉两个平台。

**配置**:

| 配置项 | 说明 |
|--------|------|
| calendar.provider | 默认提供商：FEISHU / DINGTALK |
| calendar.feishu.app-id | 飞书应用 ID |
| calendar.feishu.app-secret | 飞书应用密钥 |
| calendar.dingtalk.app-key | 钉钉应用 Key |
| calendar.dingtalk.app-secret | 钉钉应用密钥 |

**关键类**:
- `CalendarService` — 日历服务接口
- `FeishuCalendarService` — 飞书实现
- `DingTalkCalendarService` — 钉钉实现
- `CalendarServiceFactory` — 工厂（按配置选择实现）

---

### 4.11 会话管理

**功能**: 管理用户的对话会话，支持三态生命周期（ACTIVE / ARCHIVED / DELETED）、归属校验、搜索、消息历史。

**三态生命周期**:

```
ACTIVE  → ARCHIVED  (用户归档)
ACTIVE  → DELETED   (用户删除 — 软删除)
ARCHIVED → ACTIVE   (用户取消归档)
ARCHIVED → DELETED  (用户删除 — 软删除)
DELETED → (30 天后物理清理)
```

**SessionInfo 模型**:

| 字段 | 类型 | 说明 |
|------|------|------|
| chatId | String | 会话 ID |
| title | String | 会话标题（首条消息自动设置，最长 20 字） |
| status | SessionStatus | 状态：ACTIVE / ARCHIVED / DELETED |
| createdAt | LocalDateTime | 创建时间 |
| lastActiveAt | LocalDateTime | 最后活跃时间 |
| archivedAt | LocalDateTime | 归档时间 |
| deletedAt | LocalDateTime | 删除时间（软删除） |

**存储**: 文件 JSON，路径 `{session.storage.dir}/sessions.json`，包含 `userSessions` 和 `chatOwner`（反向索引防越权）。

**API**:
- `POST /api/session/login?username=xxx` — 游客登录（返回 JWT）
- `POST /api/session/create?title=xxx` — 创建会话
- `GET /api/session/list` — 活跃会话列表
- `GET /api/session/archived` — 已归档会话列表
- `GET /api/session/trash` — 回收站（已删除会话）
- `PUT /api/session/{chatId}/title` — 重命名会话
- `PUT /api/session/{chatId}/archive` — 归档会话
- `PUT /api/session/{chatId}/unarchive` — 取消归档
- `PUT /api/session/{chatId}/restore` — 从回收站恢复
- `DELETE /api/session/{chatId}` — 软删除会话
- `GET /api/session/search?keyword=xxx` — 搜索会话
- `GET /api/session/{chatId}/messages` — 获取消息历史

---

### 4.12 认证与鉴权

**双通道鉴权** (`AuthService`):

| 来源 | 说明 | 场景 |
|------|------|------|
| `Authorization: Bearer xxx` | HTTP Header | 普通 API 调用 |
| `?token=xxx` | URL 参数 | EventSource (SSE) 不支持自定义 Header |

**AuthService.authenticate(tokenParam, authHeader)**:
1. 优先取 URL 参数 token
2. 回退到 Authorization header
3. 校验 JWT → 返回 userId
4. 失败抛 `BusinessException(401)`

**JWT**: 有效期 7 天，payload 含 userId + username。

---

### 4.13 质量守护

**功能**: 对其他 Agent 的输出进行质量审查，检测事实准确性、幻觉风险和安全隐患。

**运行模式** (`QualityMode`):

| 模式 | 说明 |
|------|------|
| OFF | 关闭审查，最快响应（日常闲聊） |
| AUTO | 自动检测（默认），由 QualityModeResolver 决定 |
| REVIEW | 单次审查，返回评分 + 问题列表 |
| RED_TEAM | 红队对抗，最大化问题检测（宁可误报不可漏报） |

**自动模式解析** (`QualityModeResolver`):

```
用户消息 + 意图
  ├─ 手动指定模式 → 直接使用
  ├─ 职业决策意图 (RESUME/NEGOTIATION/ESCAPE) → REVIEW
  └─ 其他 → LLM 风险分类
       ├─ LOW → OFF
       ├─ MEDIUM → REVIEW
       └─ HIGH/CRITICAL → RED_TEAM
```

**审查维度** (0-100 分):

| 维度 | 说明 | 权重 |
|------|------|------|
| accuracyScore | 事实准确性 | 30% |
| completenessScore | 信息完整性 | 20% |
| logicScore | 推理合理性 | 20% |
| hallucinationScore | 无幻觉程度（越高越安全） | 30% |
| riskScore | 风险程度（越高越危险） | — |

**风险等级** (`RiskLevel`):

| 等级 | 说明 | 行为 |
|------|------|------|
| LOW | 日常建议，无风险 | 正常返回 |
| MEDIUM | 需要用户自行判断 | 正常返回 |
| HIGH | 建议咨询专业人士 | 持久化审查记录 |
| CRITICAL | 极高风险，建议阻断 | 持久化 + 阻断回答 |

**持久化**: 仅 HIGH 和 CRITICAL 风险审查结果持久化到 `quality-reviews.json`，用于审计和告警。

**关键类**:
- `QualityGuardAgent` — 审查执行（REVIEW / RED_TEAM 两种 prompt）
- `QualityModeResolver` — 模式自动解析（意图 + LLM 风险分类）
- `QualityReview` — 审查结果（评分 + 风险 + issues + suggestions）
- `QualityReviewRepository` — 高风险审查持久化

---

### 4.14 收藏系统

**功能**: 用户收藏消息快照，即使原消息或会话被删除，收藏内容依然保留。

**Favorite 模型**:

| 字段 | 类型 | 说明 |
|------|------|------|
| favoriteId | String | 收藏唯一 ID |
| userId | String | 所属用户 |
| chatId | String | 来源会话 |
| messageId | String | 来源消息 ID |
| contentSnapshot | String | 消息内容快照（防丢失） |
| sessionTitleSnapshot | String | 会话标题快照（防丢失） |
| role | String | 消息角色：user / assistant |
| orphaned | boolean | 来源是否已删除 |
| createdAt | LocalDateTime | 收藏时间 |

**存储**: `{artifact.storage.dir}/favorites.json`，按 userId 分组。

**API**:
- `POST /api/favorite` — 添加收藏（需 JWT）
- `DELETE /api/favorite/{favoriteId}` — 取消收藏（需 JWT）
- `GET /api/favorite/list` — 我的收藏列表（需 JWT）

**关键类**:
- `Favorite` — 收藏实体（含快照字段）
- `FavoriteRepository` — 文件持久化 + orphan 标记
- `FavoriteAppService` — 业务编排

---

### 4.15 用量追踪

**功能**: 记录用户操作事件，提供多维度使用统计。

**事件类型** (`UsageEventType`):

| 类型 | 说明 |
|------|------|
| CHAT | 普通对话 |
| RAG | RAG 知识库查询 |
| TOOL_CALL | 工具调用 |
| DOCUMENT_UPLOAD | 文档上传 |
| EXPORT | 数据导出 |
| COMPARE | Agent 对比 |
| QUALITY_REVIEW | 质量审查 |

**UsageStats 统计维度**:
- `totalEvents` — 总事件数
- `eventsByType` — 按事件类型分组
- `eventsByAgent` — 按 Agent 类型分组
- `dailyCounts` — 近 7 天每日事件数
- `totalDurationMs` — 总耗时

**存储**: `{artifact.storage.dir}/usage-events.json`，append-only。

**API**:
- `GET /api/usage/stats` — 我的使用统计（需 JWT）

**关键类**:
- `UsageTracker` — 事件记录 + 统计聚合
- `UsageEvent` — 事件实体

---

### 4.16 数据导入导出

**功能**: 用户数据全量备份（ZIP 格式导出）与恢复（导入），支持会话、消息、收藏。

**导出内容**:
- `sessions/sessions.json` — 会话列表
- `messages/{chatId}.json` — 每个会话的消息历史
- `favorites/favorites.json` — 收藏列表

**导入策略**:
- chatId 冲突 → 自动生成新 chatId，更新内部引用
- 覆盖会话、消息、收藏三类数据

**API**:
- `GET /api/export/all` — 导出全量数据（ZIP 下载，需 JWT）
- `POST /api/export/import` — 导入数据（multipart，需 JWT）

**关键类**:
- `DataExportService` — ZIP 打包导出
- `DataImportService` — ZIP 解析 + 冲突处理
- `ExportAppService` — 业务编排

---

### 4.17 对话搜索

**功能**: 跨会话加权搜索，支持标题、用户消息、AI 消息多区域匹配。

**评分模型**:

| 区域 | 权重 | 说明 |
|------|------|------|
| 标题匹配 | 100 | 最高权重 |
| 用户消息 | 30 | 用户输入内容 |
| AI 消息 | 20 | AI 回复内容 |

**匹配类型**:

| 匹配 | 分数 |
|------|------|
| 完全匹配 | 100 |
| 前缀匹配 | 70 |
| 包含匹配 | 50 |

**时间衰减加成**:
- ≤1 天: +30
- ≤7 天: +20
- ≤30 天: +10

**命中次数加成**: count × 10

**返回结果**: chatId、title、relevance(0-100)、snippet(上下文摘录)、bestHit(messageId + offset，用于前端高亮定位)

**关键类**:
- `ChatSearchService` — 加权搜索引擎

---

### 4.18 持久化消息

**功能**: 对话消息的 Source of Truth，所有下游功能（历史、搜索、收藏、导出）基于此模型。

**PersistentChatMessage 模型**:

| 字段 | 类型 | 说明 |
|------|------|------|
| messageId | String | 稳定唯一 ID（ULID，时间有序） |
| chatId | String | 所属会话 |
| role | String | user / assistant / system |
| content | String | 消息内容 |
| timestamp | long | 创建时间（epoch millis） |

**双索引**:
- `chatIndex`: chatId → 消息列表（按插入序）
- `messageIdIndex`: messageId → 消息（O(1) 查找，用于收藏/搜索定位）

**存储**: `{session.storage.dir}/messages/{chatId}.json`，每个会话一个文件。

**架构设计**: PersistentMessageRepository 是 Source of Truth，ChatMemory 是运行时缓存。写入时先持久化再同步缓存（best-effort），读取时先检查缓存一致性，不一致则从 Truth 重建。

**压缩支持**: `replaceWithSummary(chatId, summary, keepRecent)` — 压缩时替换旧消息为摘要 + 最近 N 条。

**关键类**:
- `PersistentMessageRepository` — 双索引持久化
- `ChatMemoryAdapter` — Truth ↔ ChatMemory 桥接
- `PersistentChatMessage` — 消息实体

---

## 5. API 参考

> Base URL: `http://localhost:8123/api`

### 会话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /session/login?username=xxx | 游客登录 |
| POST | /session/create?title=xxx | 创建会话 |
| GET | /session/list | 活跃会话列表 |
| GET | /session/archived | 已归档会话 |
| GET | /session/trash | 回收站 |
| PUT | /session/{chatId}/title | 重命名 |
| PUT | /session/{chatId}/archive | 归档 |
| PUT | /session/{chatId}/unarchive | 取消归档 |
| PUT | /session/{chatId}/restore | 恢复 |
| DELETE | /session/{chatId} | 软删除 |
| GET | /session/search?keyword=xxx | 搜索会话 |
| GET | /session/{chatId}/messages | 消息历史 |

### AI 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /ai/ai_chat/chat/sync | 同步对话 |
| GET | /ai/ai_chat/chat/sse | SSE 流式对话 |
| GET | /ai/orchestrator/chat | 智能路由对话（SSE） |
| GET | /ai/manus/chat | 超级智能体（SSE） |
| GET | /ai/ai_chat/rag/sync | RAG 知识库对话 |
| GET | /ai/ai_chat/tools/sync | 工具调用对话 |
| GET | /ai/ai_chat/mcp/sync | MCP 服务对话 |
| GET | /ai/ai_chat/report/sync | 职场报告生成 |

### 用户画像

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /profile/me | 查看我的画像 |
| DELETE | /profile/me | 清空画像 |

### 交付物

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /artifact/list | 查询列表（管理员） |
| GET | /artifact/{artifactId} | 查看详情 |

### 执行轨迹

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /trace/{traceId} | 单条轨迹 |
| GET | /trace/chat/{chatId}?pageNum=1&pageSize=20 | 按会话查询 |
| GET | /trace/user/{userId}?pageNum=1&pageSize=20 | 按用户查询 |

### 文档

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /document/upload | 上传知识库文档 |
| POST | /document/add | 添加文本知识库文档 |
| GET | /document/list | 文档列表 |
| DELETE | /document/{docId} | 删除文档 |

### 收藏

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /favorite | 添加收藏 |
| DELETE | /favorite/{favoriteId} | 取消收藏 |
| GET | /favorite/list | 我的收藏列表 |

### 用量统计

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /usage/stats | 我的使用统计 |

### 数据导入导出

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /export/all | 导出全量数据（ZIP） |
| POST | /export/import | 导入数据 |

### 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /health | 服务健康状态 |

---

## 6. 数据字典

### 6.1 核心数据模型

#### ExecutionTrace（执行轨迹）

| 字段 | Java 类型 | JSON 类型 | 说明 |
|------|-----------|-----------|------|
| traceId | String | string | 轨迹唯一 ID（UUID 去横线） |
| userId | String | string | 所属用户 ID |
| chatId | String | string | 所属会话 ID |
| requestId | String | string | HTTP 请求 ID（关联用） |
| status | TraceStatus | string | 轨迹状态 |
| startTime | Instant | string(ISO) | 开始时间 |
| endTime | Instant | string(ISO) | 结束时间（终态时设置） |
| spans | List\<TraceSpan\> | array | 步骤列表 |

#### TraceSpan（轨迹步骤）

| 字段 | Java 类型 | JSON 类型 | 说明 |
|------|-----------|-----------|------|
| sequence | int | number | 步骤序号（0-based） |
| stepType | TraceStepType | string | 步骤类型枚举名 |
| label | String | string | 人类可读标签 |
| status | TraceStepStatus | string | 步骤状态 |
| startTime | Instant | string(ISO) | 开始时间 |
| endTime | Instant | string(ISO) | 结束时间 |
| errorMessage | String | string | 错误信息（仅 FAILED） |
| metadata | Map\<String,String\> | object | 附加元数据（≤50 键） |

#### UserProfile（用户画像）

| 字段 | Java 类型 | JSON 类型 | 说明 |
|------|-----------|-----------|------|
| userId | String | string | 用户 ID |
| communicationPreference | CommunicationPreference | string | 沟通偏好 |
| tonePreference | String | string | 语气偏好 |
| focusAreas | List\<String\> | array | 关注领域 |
| knownBackground | String | string | 已知背景 |
| historicalDemands | List\<String\> | array | 历史诉求 |
| createdAt | LocalDateTime | string | 创建时间 |
| updatedAt | LocalDateTime | string | 更新时间 |

#### Artifact（交付物）

| 字段 | Java 类型 | JSON 类型 | 说明 |
|------|-----------|-----------|------|
| artifactId | String | string | 交付物唯一 ID |
| userId | String | string | 所属用户 |
| chatId | String | string | 所属会话 |
| type | String | string | 类型（如 resume, report） |
| producer | String | string | 生产者 Agent 名称 |
| title | String | string | 标题 |
| content | String | string | 完整内容（JSON 字符串） |
| status | ArtifactStatus | string | 状态 |
| scope | ArtifactScope | string | 作用域 |
| createdAt | LocalDateTime | string | 创建时间 |
| updatedAt | LocalDateTime | string | 更新时间 |

#### Appointment（预约记录）

| 字段 | Java 类型 | JSON 类型 | 说明 |
|------|-----------|-----------|------|
| appointmentId | String | string | 预约唯一 ID |
| chatId | String | string | 所属会话 |
| name | String | string | 咨询者姓名 |
| contact | String | string | 联系方式 |
| appointmentTime | LocalDateTime | string | 预约时间 |
| topic | String | string | 咨询主题 |
| remark | String | string | 备注 |
| calendarEventId | String | string | 日历事件 ID |
| calendarLink | String | string | 日历事件链接 |
| calendarProvider | CalendarProvider | string | 日历提供商 |
| status | AppointmentStatus | string | 预约状态 |
| createdAt | LocalDateTime | string | 创建时间 |
| updatedAt | LocalDateTime | string | 更新时间 |

#### CoreInformation（核心信息）

| 字段 | Java 类型 | JSON 类型 | 说明 |
|------|-----------|-----------|------|
| name | String | string | 咨询者姓名 |
| contact | String | string | 联系方式 |
| appointmentTime | LocalDateTime | string | 预约时间 |
| topic | String | string | 咨询主题 |

#### SessionInfo（会话信息）

| 字段 | Java 类型 | JSON 类型 | 说明 |
|------|-----------|-----------|------|
| chatId | String | string | 会话 ID |
| title | String | string | 会话标题 |
| status | SessionStatus | string | ACTIVE / ARCHIVED / DELETED |
| createdAt | LocalDateTime | string | 创建时间 |
| lastActiveAt | LocalDateTime | string | 最后活跃时间 |
| archivedAt | LocalDateTime | string | 归档时间 |
| deletedAt | LocalDateTime | string | 删除时间 |

#### Favorite（收藏）

| 字段 | Java 类型 | JSON 类型 | 说明 |
|------|-----------|-----------|------|
| favoriteId | String | string | 收藏唯一 ID |
| userId | String | string | 所属用户 |
| chatId | String | string | 来源会话 |
| messageId | String | string | 来源消息 ID |
| contentSnapshot | String | string | 消息内容快照 |
| sessionTitleSnapshot | String | string | 会话标题快照 |
| role | String | string | user / assistant |
| orphaned | boolean | boolean | 来源是否已删除 |
| createdAt | LocalDateTime | string | 收藏时间 |

#### PersistentChatMessage（持久化消息）

| 字段 | Java 类型 | JSON 类型 | 说明 |
|------|-----------|-----------|------|
| messageId | String | string | ULID 唯一 ID |
| chatId | String | string | 所属会话 |
| role | String | string | user / assistant / system |
| content | String | string | 消息内容 |
| timestamp | long | number | 创建时间（epoch millis） |

#### QualityReview（质量审查）

| 字段 | Java 类型 | JSON 类型 | 说明 |
|------|-----------|-----------|------|
| reviewId | String | string | 审查唯一 ID |
| chatId | String | string | 所属会话 |
| mode | QualityMode | string | 审查模式 |
| accuracyScore | int | number | 事实准确性 (0-100) |
| completenessScore | int | number | 信息完整性 (0-100) |
| logicScore | int | number | 推理合理性 (0-100) |
| hallucinationScore | int | number | 无幻觉程度 (0-100) |
| riskScore | int | number | 风险程度 (0-100) |
| overallScore | int | number | 综合评分 (加权) |
| riskLevel | RiskLevel | string | 风险等级 |
| issues | List\\<String\\> | array | 发现的问题 |
| suggestions | List\\<String\\> | array | 改进建议 |
| summary | String | string | 一句话总结 |
| createdAt | LocalDateTime | string | 审查时间 |

#### UsageEvent（用量事件）

| 字段 | Java 类型 | JSON 类型 | 说明 |
|------|-----------|-----------|------|
| eventId | String | string | 事件唯一 ID |
| userId | String | string | 所属用户 |
| type | UsageEventType | string | 事件类型 |
| agentType | String | string | Agent 类型（仅 CHAT） |
| durationMs | long | number | 耗时（毫秒） |
| timestamp | LocalDateTime | string | 事件时间 |

### 6.2 枚举值速查

#### TraceStatus

| 枚举值 | displayName | 终态 |
|--------|-------------|------|
| RUNNING | 执行中 | ✗ |
| SUCCESS | 成功 | ✓ |
| FAILED | 失败 | ✓ |
| CANCELLED | 已取消 | ✓ |

#### TraceStepStatus

| 枚举值 | displayName | 终态 |
|--------|-------------|------|
| RUNNING | 执行中 | ✗ |
| SUCCESS | 成功 | ✓ |
| FAILED | 失败 | ✓ |
| SKIPPED | 已跳过 | ✓ |

#### TraceStepType

| 枚举值 | displayName |
|--------|-------------|
| SKILL_MATCH | 技能匹配 |
| INTENT_DETECTION | 意图识别 |
| ROUTING | 路由分发 |
| PROFILE_INJECTION | 画像注入 |
| ARTIFACT_QUERY | 交付物查询 |
| ARTIFACT_CONSUME | 交付物消费 |
| SUB_AGENT_EXECUTION | 子Agent执行 |
| TOOL_CALL | 工具调用 |
| MEMORY_COMPRESSION | 记忆压缩 |
| PROFILE_UPDATE | 画像更新 |

#### ArtifactStatus

| 枚举值 | 说明 |
|--------|------|
| PENDING | 生产中 |
| READY | 可消费 |
| CONSUMED | 已消费 |

#### ArtifactScope

| 枚举值 | 说明 |
|--------|------|
| USER_PROFILE | 用户画像级（按 userId 跨会话累积） |
| TASK | 任务级（按 chatId 会话级） |

#### CommunicationPreference

| 枚举值 | 说明 |
|--------|------|
| DETAILED | 详细分析型 |
| CONCISE | 简洁直接型 |
| FRIENDLY | 友好亲切型 |
| PROFESSIONAL | 专业严谨型 |

#### AppointmentStatus

| 枚举值 | 说明 |
|--------|------|
| PENDING | 待确认 |
| CONFIRMED | 已确认 |
| CANCELLED | 已取消 |
| COMPLETED | 已完成 |

#### CalendarProvider

| 枚举值 | 说明 |
|--------|------|
| FEISHU | 飞书 |
| DINGTALK | 钉钉 |

#### AgentState

| 枚举值 | 说明 |
|--------|------|
| IDLE | 空闲 |
| RUNNING | 运行中 |
| FINISHED | 已完成 |
| ERROR | 出错 |

#### CoreInfoType

| 枚举值 | fieldName | displayName |
|--------|-----------|-------------|
| NAME | name | 姓名 |
| CONTACT | contact | 联系方式 |
| APPOINTMENT_TIME | appointmentTime | 预约时间 |
| TOPIC | topic | 咨询主题 |

#### SessionStatus

| 枚举值 | displayName | 说明 |
|--------|-------------|------|
| ACTIVE | 活跃 | 侧边栏可见 |
| ARCHIVED | 已归档 | 折叠在"归档"区域 |
| DELETED | 已删除 | 软删除，30 天后物理清理 |

#### QualityMode

| 枚举值 | displayName | 说明 |
|--------|-------------|------|
| OFF | 关闭 | 不审查，最快响应 |
| AUTO | 自动 | 由 QualityModeResolver 决定 |
| REVIEW | 审查模式 | 单次审查 |
| RED_TEAM | 红蓝对抗 | 红队对抗审查 |

#### RiskLevel

| 枚举值 | displayName | 说明 |
|--------|-------------|------|
| LOW | 低风险 | 日常建议，无风险 |
| MEDIUM | 中风险 | 需要用户自行判断 |
| HIGH | 高风险 | 建议咨询专业人士 |
| CRITICAL | 极高风险 | 建议阻断回答 |

#### UsageEventType

| 枚举值 | displayName |
|--------|-------------|
| CHAT | 普通对话 |
| RAG | RAG 知识库查询 |
| TOOL_CALL | 工具调用 |
| DOCUMENT_UPLOAD | 文档上传 |
| EXPORT | 数据导出 |
| COMPARE | Agent 对比 |
| QUALITY_REVIEW | 质量审查 |

### 6.3 存储结构

所有数据均为文件 JSON 存储，路径由 application.yml 配置：

| 数据 | 配置项 | 默认路径 | 格式 |
|------|--------|----------|------|
| 会话 | session.storage.dir | ./tmp/sessions | sessions.json |
| 消息 | session.storage.dir | ./tmp/sessions/messages/ | {chatId}.json |
| 预约 | appointment.storage.dir | ./tmp/appointments | appointments.json |
| 画像 | user-profile.storage.dir | ./tmp/user-profiles | user-profiles.json |
| 交付物 | artifact.storage.dir | ./tmp/artifacts | artifacts.json |
| 轨迹 | trace.storage.dir | ./tmp/traces | traces.json |
| 收藏 | artifact.storage.dir | ./tmp/artifacts | favorites.json |
| 质量审查 | artifact.storage.dir | ./tmp/artifacts | quality-reviews.json |
| 用量事件 | artifact.storage.dir | ./tmp/artifacts | usage-events.json |
| 对话记忆 | — | ./tmp/chat-memory | 按 chatId 分文件 |

---

## 7. 前端指南

### 路由

| 路径 | 页面 | 说明 |
|------|------|------|
| / | Home | 首页，应用入口 |
| /career-advisor | CareerAdvisor | 职场顾问（主对话页面） |
| /super-agent | SuperAgent | 超级智能体 |
| /love-master | LoveMaster | 恋爱大师 |
| /artifact-admin | ArtifactAdmin | 交付物管理（管理员） |
| /trace/:traceId | TraceDetail | 轨迹详情页 |

### 组件

| 组件 | 说明 |
|------|------|
| ChatRoom | 通用聊天室（消息列表 + 输入框） |
| TraceTimelineView | 轨迹时间线（暗色风格，自动判断实时/历史） |
| AiAvatarFallback | AI 头像回退 |
| AppFooter | 页脚 |

### API 封装 (`src/api/index.js`)

所有 API 通过 axios 实例调用，自动注入 JWT：
- `request` — axios 实例，baseURL 自动切换 dev/prod
- `connectSSE` — SSE 连接封装
- 拦截器自动带 `Authorization: Bearer xxx`

### 实时轨迹

CareerAdvisor 页面：
1. 发送消息时重置 `traceMap`
2. 监听 SSE `trace` 事件，switch 分发 5 种事件类型
3. 输入框上方显示暗色 trace 面板
4. Header「📊 轨迹」按钮打开历史弹窗（分页加载）
5. 每条轨迹可点击跳转 TraceDetail 详情页

---

## 8. 配置参考

### application.yml 完整配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 8123 | 服务端口 |
| server.servlet.context-path | /api | 路径前缀 |
| spring.ai.dashscope.api-key | ${DASHSCOPE_API_KEY} | DashScope API Key |
| spring.ai.dashscope.chat.options.model | qwen3.5-plus-2026-04-20 | 模型名称 |
| jwt.secret | ${JWT_SECRET} | JWT 密钥 |
| search-api.api-key | ${SEARCH_API_KEY} | 搜索 API Key |
| calendar.provider | FEISHU | 日历提供商 |
| calendar.feishu.app-id | ${FEISHU_APP_ID} | 飞书应用 ID |
| calendar.feishu.app-secret | ${FEISHU_APP_SECRET} | 飞书应用密钥 |
| calendar.dingtalk.app-key | ${DINGTALK_APP_KEY} | 钉钉应用 Key |
| calendar.dingtalk.app-secret | ${DINGTALK_APP_SECRET} | 钉钉应用密钥 |
| appointment.storage.dir | ./tmp/appointments | 预约存储目录 |
| session.storage.dir | ./tmp/sessions | 会话存储目录 |
| artifact.storage.dir | ./tmp/artifacts | 交付物存储目录 |
| user-profile.storage.dir | ./tmp/user-profiles | 画像存储目录 |
| profile.injection.max-chars | 1000 | 画像注入字符上限 |
| trace.storage.dir | ./tmp/traces | 轨迹存储目录 |
| trace.stream.enabled | true | 实时 trace SSE 开关 |
| trace.max-spans-per-trace | 200 | 单轨迹最大 span 数 |
| trace.metadata.max-value-chars | 2000 | metadata 值最大字符 |
| trace.max-traces-per-user | 500 | 单用户轨迹保留上限 |
| chat.memory.compression.token-threshold | 4000 | Token 压缩阈值 |
| chat.memory.compression.turn-threshold | 20 | 轮数压缩阈值 |
| chat.memory.compression.recent-turns | 5 | 保留最近轮数 |
