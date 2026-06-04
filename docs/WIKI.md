# 职场生存智囊 — 项目 Wiki

> AI Agent 全场景职场决策系统  
> 生成时间：2026-06-04  
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
│  SessionManager (会话) · AuthService (认证)                  │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                  Storage Layer                               │
│  File-based JSON: sessions / appointments / artifacts        │
│  File-based JSON: user-profiles / traces                     │
│  PgVector: RAG 向量存储（可选）                               │
└─────────────────────────────────────────────────────────────┘
```

### 分层职责

| 层 | 职责 | 禁止 |
|----|------|------|
| Controller | 参数绑定、调用 Agent/Service、返回 Result\<T\> | 业务逻辑、try-catch |
| Agent | LLM 交互、意图识别、工具编排 | 直接操作存储 |
| Service | 画像提取、记忆压缩、轨迹采集 | — |
| Repository | 文件持久化、JSON 序列化 | — |

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
| USER | 用户级（仅本人可见） |
| SESSION | 会话级 |
| GLOBAL | 全局（所有用户可见） |

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

**功能**: 管理用户的对话会话，支持创建、列表、删除、归属校验。

**SessionInfo 模型**:

| 字段 | 类型 | 说明 |
|------|------|------|
| chatId | String | 会话 ID |
| title | String | 会话标题（首条消息自动设置） |
| createdAt | LocalDateTime | 创建时间 |
| lastActiveAt | LocalDateTime | 最后活跃时间 |

**存储**: 文件 JSON，路径 `{session.storage.dir}/sessions.json`

**API**:
- `POST /api/session/login?username=xxx` — 游客登录（返回 JWT）
- `POST /api/session/create?title=xxx` — 创建会话
- `GET /api/session/list` — 会话列表
- `DELETE /api/session/{chatId}` — 删除会话

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

## 5. API 参考

> Base URL: `http://localhost:8123/api`

### 会话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /session/login?username=xxx | 游客登录 |
| POST | /session/create?title=xxx | 创建会话 |
| GET | /session/list | 会话列表 |
| DELETE | /session/{chatId} | 删除会话 |

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
| createdAt | LocalDateTime | string | 创建时间 |
| lastActiveAt | LocalDateTime | string | 最后活跃时间 |

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
| USER | 用户级 |
| SESSION | 会话级 |
| GLOBAL | 全局级 |

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

### 6.3 存储结构

所有数据均为文件 JSON 存储，路径由 application.yml 配置：

| 数据 | 配置项 | 默认路径 | 格式 |
|------|--------|----------|------|
| 会话 | session.storage.dir | ./tmp/sessions | sessions.json |
| 预约 | appointment.storage.dir | ./tmp/appointments | appointments.json |
| 画像 | user-profile.storage.dir | ./tmp/user-profiles | user-profiles.json |
| 交付物 | artifact.storage.dir | ./tmp/artifacts | artifacts.json |
| 轨迹 | trace.storage.dir | ./tmp/traces | traces.json |
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
