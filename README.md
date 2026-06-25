# 全场景职场生存智囊Agent

> 作者：kira
>
> 本项目为"全场景职场生存智囊Agent"，是一个融合长链推理与多Agent协作的智能决策系统

## 项目介绍

这是一套以 **AI 开发实战** 为核心的项目教程，将通过开发 **AI 职场生存智囊应用 + 拥有自主规划能力的超级智能体**，带大家掌握新时代程序员必知必会的 AI 核心概念、AI 实用工具、AI 编程技术、AI 框架原理、AI 调优技巧，大幅增加求职的竞争力！



`AI 职场生存智囊应用` 可以依赖 AI 大模型解决用户的职场难题，支持多轮对话、基于自定义知识库进行问答、自主调用工具和 MCP 服务完成任务，比如调用文档工具生成离职话术模板、结合搜索制定谈判策略，或者根据用户处境生成实时高情商回复。


- 主流 AI 应用平台的使用
- AI 大模型的 4 种接入方式
- AI 开发框架（Spring AI + LangChain4j）
- AI 大模型本地部署
- Prompt 工程和优化技巧
- Spring AI 核心特性：如自定义 Advisor、对话记忆、结构化输出
- RAG 知识库实战、原理和调优技巧
- PgVector 向量数据库 + 云数据库服务
- Tool Calling 工具调用实战及原理
- MCP 模型上下文协议和服务开发
- AI 智能体 Manus 原理和自主开发
- AI 服务化和 Serverless 部署上线
- 各种新概念：如多模态、智能体工作流、A2A 协议、大模型评估等

## 项目功能梳理

项目中，我们将开发一个 AI 职场生存智囊应用、一个拥有自主规划能力的超级智能体，以及一系列工具和 MCP 服务。

具体需求如下：

- **AI 职场生存智囊应用**：职场人在面对上下级沟通、高难度汇报、同事甩锅、谈薪离职、饭局应酬等高压力场景时，AI 提供实时话术、策略分析和心理建设。支持多轮对话、对话记忆持久化、RAG 知识库检索（内置八篇全场景职场生存文档）、工具调用、MCP 服务调用。
- **AI 超级智能体（YuManus）**：可以根据用户的需求（如"帮我准备下周的加薪谈判方案"、"我要离职，帮我梳理完整证据链和交接清单"），自主推理和行动，直到完成目标。
- **提供给 AI 的工具**：包括联网搜索（实时职场案例/法律条款）、文件操作、网页抓取、资源下载、终端操作、PDF 生成（生成定制化职场生存手册）。
- **AI MCP 服务**：可以从特定网站搜索与职场技能相关的图片或信息。

### 🆕 预约咨询功能（新增）

系统新增预约咨询功能，支持用户通过对话预约专家咨询服务：

#### 核心特性

1. **智能意图识别**
   - 自动识别用户预约咨询意图（"预约"、"咨询"、"约时间"等）
   - 通过 OrchestratorAgent 自动路由到 ConsultationAgent

2. **追问机制**
   - 自动收集必要信息：姓名、联系方式、预约时间
   - 支持可选信息：咨询主题、备注
   - 智能追问引导，格式验证

3. **企业日历对接**
   - 支持飞书日历 API（默认）
   - 支持钉钉日历 API
   - 自动创建日历事件并返回链接

4. **预约记录管理**
   - 持久化存储预约记录
   - 支持按状态查询（待确认、已确认、已取消等）
   - 文件存储（可扩展为数据库）

5. **模板化配置**
   - 追问模板支持热更新
   - 确认信息模板可配置
   - 成功/失败消息模板化

#### 状态机流程

```
INITIAL → COLLECTING_INFO → CONFIRMING → CREATING_APPOINTMENT → COMPLETED
```

### 🆕 记忆压缩功能（新增）

系统新增对话记忆压缩功能，优化长对话性能：

#### 核心特性

1. **多策略压缩**
   - Token 阈值策略（默认 4000 Token）
   - 对话轮数策略（默认 20 轮）
   - 可扩展混合策略

2. **智能摘要生成**
   - 调用 LLM 生成关键信息摘要
   - 保留用户需求、已确认信息、未解决问题
   - 降级方案：LLM 失败时使用简单摘要

3. **自动触发**
   - 对话过程中自动检测压缩条件
   - 保留最近 N 轮完整对话（默认 5 轮）
   - 压缩状态实时跟踪

4. **配置化管理**
   - Token 阈值可配置
   - 轮数阈值可配置
   - 保留轮数可配置

## 用哪些技术？

项目以 Spring AI 开发框架实战为核心，涉及到多种主流 AI 客户端和工具库的运用。

- Java 21 + Spring Boot 3 框架
- ⭐️ Spring AI + LangChain4j
- ⭐️ RAG 知识库（加载八篇职场生存文档）
- ⭐️ PGvector 向量数据库
- ⭐ Tool Calling 工具调用
- ⭐️ MCP 模型上下文协议
- ⭐️ ReAct Agent 智能体构建
- ⭐️ Serverless 计算服务
- ⭐️ AI 大模型开发平台百炼
- ⭐️ Cursor AI 代码生成
- ⭐️ SSE 异步推送
- 第三方接口：如 SearchAPI / Pexels API
- Ollama 大模型部署
- 工具库如：Kryo 高性能序列化 + Jsoup 网页抓取 + iText PDF 生成 + Knife4j 接口文档

### 🆕 新增技术栈

- ⭐️ NLU 意图理解层（V4.2：1 次 LLM + 别名解析 + 槽位提取 + 意图分类 + 澄清）
- ⭐️ 多 Agent 运行时（V1 群聊模式 + V2 Task Orchestrator 基础设施）
- ⭐️ 工作流引擎（WorkflowMatcher + TaskExecutor + ResultAggregator）
- ⭐️ Token 预算控制（TokenBudget + TokenUsageTracker）
- ⭐️ 执行轨迹实时时间线（TraceTimelineView）
- ⭐️ 消息来源追踪（MessageSource: USER/AGENT/SYSTEM/TOOL/SYNTHESIZER）
- ⭐️ 飞书/钉钉日历 API 集成
- ⭐️ 对话记忆压缩（Token/轮数策略）
- ⭐️ 模板化追问机制
- ⭐️ 文件持久化存储（JSON）
- ⭐️ 质量守护（Review/RedTeam 自动审查 + 风险分级）
- ⭐️ 收藏系统（消息快照 + orphan 标记）
- ⭐️ 用量追踪（7 种事件类型 + 多维度统计）
- ⭐️ 数据导入导出（ZIP 全量备份/恢复）
- ⭐️ 对话搜索（加权评分 + 时间衰减）
- ⭐️ 持久化消息（Source of Truth + 双索引）
- ⭐️ 会话三态生命周期（ACTIVE/ARCHIVED/DELETED）
- ⭐️ 跨 Agent 记忆注入（切换 Agent 不丢上下文）
- ⭐️ 预约咨询智能提取（自然语言姓名/手机号/时间）
- ⭐️ Markdown 结构化输出（表格确认、列表结果）

## 项目结构

```
src/main/java/com/yupi/yuaiagent/
├── agent/                          # Agent 层
│   ├── model/                      # Agent 数据模型
│   │   ├── AgentState.java         # 智能体状态机
│   │   ├── Appointment.java        # 预约记录实体
│   │   ├── CoreInformation.java    # 核心信息实体
│   │   ├── CoreInfoType.java       # 核心信息类型枚举
│   │   ├── FollowUpQuestion.java   # 追问问题实体
│   │   └── CompressedMemory.java   # 压缩记忆模型
│   ├── output/                     # Agent 输出模型（V2）
│   │   ├── AgentOutput.java        # 类型化输出接口
│   │   ├── TextOutput.java         # 通用文本输出
│   │   ├── AgentOutputFormatter.java # Formatter 接口
│   │   └── FormatterRegistry.java  # Formatter 注册表
│   ├── task/                       # 任务模型（V2）
│   │   ├── ExecutionResult.java    # 统一执行结果
│   │   ├── TaskStatus.java         # 任务状态枚举
│   │   └── FailurePolicy.java      # 失败策略枚举
│   ├── runner/                     # AgentRunner 适配器（V2）
│   │   ├── ResumeAgentRunner.java
│   │   ├── NegotiationAgentRunner.java
│   │   ├── EscapeAgentRunner.java
│   │   └── GeneralCareerAgentRunner.java
│   ├── data/                       # 数据员工 Agent 族
│   │   ├── DataEmployeeAgent.java  # 数据员工基类（模板方法）
│   │   ├── DataAnalystAgent.java   # 数据分析师
│   │   ├── CareerCoachAgent.java   # 岗位辅导
│   │   ├── ProfileCuratorAgent.java# 用户画像整理
│   │   ├── PromotionPlannerAgent.java    # 晋升路径规划
│   │   ├── LearningResourceRecommenderAgent.java # 学习资源推荐
│   │   ├── ProductionResult.java   # 生产结果
│   │   ├── ProductionContext.java  # 生产上下文
│   │   ├── AnalysisReport.java     # 分析报告
│   │   └── AnalysisSource.java     # 分析来源枚举
│   ├── AgentIntent.java            # 意图枚举
│   ├── AgentRunner.java          # Agent 执行接口（V2）
│   ├── TaskExecutor.java         # 任务执行引擎（V2）
│   ├── ResultAggregator.java     # 结果汇总器（V2）
│   ├── DataQueryRouter.java      # 数据查询路由（零 LLM）
│   ├── BaseAgent.java              # Agent 基类
│   ├── ReActAgent.java             # ReAct 思考-行动循环
│   ├── ToolCallAgent.java          # 工具调用 Agent
│   ├── OrchestratorAgent.java      # 主控 Agent（路由分发）
│   ├── ConsultationAgent.java      # 预约咨询 Agent
│   ├── ResumeAgent.java            # 简历优化 Agent
│   ├── NegotiationAgent.java       # 薪资谈判 Agent
│   ├── EscapeAgent.java            # 离职规划 Agent
│   ├── GeneralCareerAgent.java     # 通用职场 Agent
│   └── YuManus.java                # 超级智能体（工具调用）
├── controller/                     # Controller 层（HTTP 适配）
│   ├── AiController.java           # AI 对话接口
│   ├── TraceController.java        # 执行轨迹查询
│   ├── SessionController.java      # 会话管理（CRUD/归档/搜索/消息）
│   ├── ProfileController.java      # 用户画像
│   ├── ArtifactController.java     # 交付物管理
│   ├── DocumentController.java     # 文档管理（上传/列表/删除）
│   ├── FavoriteController.java     # 收藏管理
│   ├── UsageController.java        # 用量统计
│   ├── ExportController.java       # 数据导入导出
│   └── HealthController.java       # 健康检查
├── service/                        # AppService 层（业务编排）
│   ├── OrchestratorAppService.java # 智能路由编排（校验/归属/追踪）
│   ├── SessionAppService.java      # 会话业务（CRUD/归档/搜索/消息）
│   ├── FavoriteAppService.java     # 收藏业务
│   ├── ExportAppService.java       # 导入导出业务
│   └── DocumentAppService.java     # 文档业务
├── artifact/                       # 交付物货架
│   ├── model/                      # 交付物模型
│   │   ├── Artifact.java           # 交付物实体
│   │   ├── ArtifactStatus.java     # 状态枚举
│   │   ├── ArtifactScope.java      # 作用域枚举
│   │   ├── ArtifactQuery.java      # 查询条件
│   │   └── ArtifactSummary.java    # 摘要
│   ├── ArtifactShelf.java          # 货架（存取编排）
│   └── ArtifactRepository.java     # 文件持久化
├── profile/                        # 用户画像系统
│   ├── model/
│   │   ├── UserProfile.java        # 画像实体
│   │   └── CommunicationPreference.java # 沟通偏好枚举
│   ├── UserProfileService.java     # 画像编排
│   ├── UserProfileExtractor.java   # LLM 提取器
│   ├── ProfilePromptBuilder.java   # 提示词注入
│   └── UserProfileRepository.java  # 画像持久化
├── quality/                        # 质量守护
│   ├── QualityGuardAgent.java      # 审查执行（REVIEW/RED_TEAM）
│   ├── QualityModeResolver.java    # 模式自动解析
│   ├── QualityReview.java          # 审查结果
│   ├── QualityReviewRepository.java# 高风险审查持久化
│   ├── QualityMode.java            # 审查模式枚举
│   └── RiskLevel.java              # 风险等级枚举
├── favorite/                       # 收藏系统
│   ├── Favorite.java               # 收藏实体（含快照）
│   └── FavoriteRepository.java     # 文件持久化 + orphan 标记
├── usage/                          # 用量追踪
│   ├── UsageTracker.java           # 事件记录 + 统计聚合
│   ├── UsageEvent.java             # 事件实体
│   └── UsageEventType.java         # 事件类型枚举
├── export/                         # 数据导入导出
│   ├── DataExportService.java      # ZIP 打包导出
│   └── DataImportService.java      # ZIP 解析 + 冲突处理
├── search/                         # 对话搜索
│   └── ChatSearchService.java      # 加权搜索引擎
├── message/                        # 持久化消息（Source of Truth）
│   ├── PersistentChatMessage.java  # 消息实体（ULID + sourceType/sourceId/sourceName）
│   ├── PersistentMessageRepository.java # 双索引持久化
│   ├── ChatMemoryAdapter.java      # Truth ↔ ChatMemory 桥接
│   └── MessageSource.java          # 消息来源枚举（USER/AGENT/SYSTEM/TOOL/SYNTHESIZER）
├── nlu/                            # NLU 意图理解层（V4.2）
│   ├── NluPipeline.java            # NLU 串联管道
│   ├── UnifiedNluExtractor.java    # 1 次 LLM：intent + slots + domain + action
│   ├── AliasResolver.java          # 别名元数据提取（Word Boundary）
│   ├── IntentReranker.java         # domain 信号 re-rank
│   ├── IntentAmbiguityDetector.java # 同类意图检测
│   ├── IntentRequirementRegistry.java # 双维度槽位需求
│   ├── ClarificationHandler.java   # 模板追问（零 LLM）
│   ├── ContextShiftDetector.java   # 3 态接口（FOLLOW_UP/ENTITY_SWITCH/NEW_QUERY）
│   ├── RuleContextShiftDetector.java # 规则实现
│   ├── ConversationState.java      # 多轮槽位状态 + smartMerge
│   ├── ConversationStateStore.java # 存储接口
│   ├── InMemoryConversationStateStore.java # 内存实现
│   ├── NluContext.java             # state + aliases 分离
│   ├── NluIntent.java              # 细粒度意图枚举（14 值）
│   ├── RouteHint.java              # NLU → WorkflowMatcher 桥接
│   └── RouteTemplate.java          # 点分记法路由
├── context/                        # 上下文层（V2）
│   ├── ConversationContext.java    # 不可变对话上下文
│   ├── ConversationContextBuilder.java # 上下文构建器
│   └── RuntimeContext.java         # 可变执行状态
├── workflow/                       # 工作流层（V2）
│   ├── WorkflowTemplate.java       # 工作流模板
│   ├── WorkflowRegistry.java       # 工作流注册表
│   ├── WorkflowMatcher.java        # Score-based 匹配器
│   ├── WorkflowMatchResult.java    # 匹配结果
│   ├── MatchType.java              # 匹配类型枚举
│   └── PlanStep.java               # 工作流步骤
├── budget/                         # 预算层（V2）
│   ├── TokenBudget.java            # Token 预算
│   ├── TokenUsage.java             # Token 使用记录
│   └── TokenUsageTracker.java      # 使用量追踪
├── session/                        # 会话管理
│   ├── SessionManager.java         # 三态会话（ACTIVE/ARCHIVED/DELETED）
│   └── SessionStatus.java          # 会话状态枚举
├── trace/                          # 执行轨迹
│   ├── model/                      # 轨迹模型
│   │   ├── ExecutionTrace.java     # 轨迹实体
│   │   ├── TraceSpan.java          # 步骤实体
│   │   ├── TraceStepType.java      # 步骤类型枚举（10 种）
│   │   ├── TraceStepStatus.java    # 步骤状态枚举
│   │   ├── TraceStatus.java        # 轨迹状态枚举
│   │   └── TraceConstants.java     # 常量
│   ├── TraceRecorder.java          # 采集门面
│   ├── TraceContext.java           # 请求级上下文
│   ├── TraceStreamPublisher.java   # SSE 推送
│   ├── TraceRepository.java        # 文件持久化 + 保留策略
│   └── TraceProperties.java        # 配置属性
├── chatmemory/                     # 对话记忆管理
│   ├── ChatMemoryManager.java      # 记忆管理编排
│   ├── FileBasedChatMemory.java    # 文件持久化（Kryo）
│   ├── CompressionStrategy.java    # 压缩策略接口
│   ├── TokenCompressionStrategy.java # Token 阈值策略
│   ├── TurnCompressionStrategy.java  # 轮数阈值策略
│   └── MemoryCompressor.java       # LLM 压缩器
├── skill/                          # 技能系统
│   ├── SkillDefinition.java        # 技能定义
│   ├── SkillRegistry.java          # 技能注册表
│   └── SkillExecutor.java          # 技能执行器
├── calendar/                       # 日历集成
│   ├── CalendarService.java        # 日历服务接口
│   ├── CalendarServiceFactory.java # 工厂
│   ├── CalendarEvent.java          # 日历事件模型
│   ├── FeishuCalendarService.java  # 飞书实现
│   └── DingTalkCalendarService.java# 钉钉实现
├── auth/                           # 认证鉴权
│   ├── AuthService.java            # 双通道认证（Header + URL 参数）
│   └── JwtUtil.java                # JWT 工具
├── rag/                            # RAG 知识库
│   ├── AiChatDocumentLoader.java   # 文档加载器
│   ├── MyTokenTextSplitter.java    # 文本分割
│   ├── MyKeywordEnricher.java      # 关键词增强
│   ├── QueryRewriter.java          # 查询改写
│   ├── MultiQueryRetriever.java    # 多路召回
│   ├── AiChatRagCustomAdvisorFactory.java # RAG 增强
│   ├── AiChatVectorStoreConfig.java # 内存向量库
│   ├── PgVectorVectorStoreConfig.java # PgVector 向量库
│   └── AiChatRagCloudAdvisorConfig.java # 云端 RAG
├── tools/                          # 工具集
│   ├── WebSearchTool.java          # 联网搜索
│   ├── WebScrapingTool.java        # 网页抓取
│   ├── FileOperationTool.java      # 文件操作
│   ├── PDFGenerationTool.java      # PDF 生成
│   ├── ResourceDownloadTool.java   # 资源下载
│   ├── TerminalOperationTool.java  # 终端执行
│   ├── TerminateTool.java          # 终止工具
│   └── ToolRegistration.java       # 工具注册
├── advisor/                        # Advisor
│   ├── MyLoggerAdvisor.java        # 调用日志
│   └── ReReadingAdvisor.java       # Re2 提升推理
├── dto/                            # 数据传输对象
│   ├── DocumentResponse.java
│   ├── FavoriteResponse.java
│   ├── SessionSearchResponse.java
│   ├── ImportResult.java
│   ├── AddFavoriteRequest.java
│   └── RenameRequest.java
├── common/                         # 通用组件
│   ├── Response.java               # 统一响应
│   └── ResultCode.java             # 响应码
├── config/                         # 配置类
│   ├── AgentConfig.java
│   ├── CalendarConfig.java
│   ├── CompressionConfig.java
│   ├── FollowUpTemplateConfig.java # 追问模板
│   └── CorsConfig.java             # 跨域
├── exception/                      # 异常处理
│   ├── GlobalExceptionHandler.java # 全局异常
│   └── BusinessException.java      # 业务异常
├── validation/                     # 验证器
│   └── InfoValidator.java          # 信息格式校验
├── repository/                     # 预约存储
│   └── AppointmentRepository.java
├── constant/                       # 常量
│   └── FileConstant.java
├── demo/                           # 示例代码
│   ├── invoke/                     # AI 调用示例（5 种方式）
│   └── rag/                        # RAG 示例
├── app/                            # 基础对话
│   └── AiChatAgent.java            # 基础 AI 对话 Agent
└── AiAgentApplication.java         # 启动类
```

## 配置说明

### 日历服务配置

```yaml
calendar:
  provider: ${CALENDAR_PROVIDER:FEISHU}  # FEISHU 或 DINGTALK
  feishu:
    app-id: ${FEISHU_APP_ID:}
    app-secret: ${FEISHU_APP_SECRET:}
    base-url: ${FEISHU_BASE_URL:https://open.feishu.cn/open-apis}
  dingtalk:
    app-key: ${DINGTALK_APP_KEY:}
    app-secret: ${DINGTALK_APP_SECRET:}
    base-url: ${DINGTALK_BASE_URL:https://api.dingtalk.com}
```

### 记忆压缩配置

```yaml
chat:
  memory:
    compression:
      token-threshold: ${CHAT_TOKEN_THRESHOLD:4000}
      turn-threshold: ${CHAT_TURN_THRESHOLD:20}
      recent-turns: ${CHAT_RECENT_TURNS:5}
```

### 预约存储配置

```yaml
appointment:
  storage:
    dir: ${APPOINTMENT_STORAGE_DIR:./tmp/appointments}
```

## API 接口

### 智能路由接口

```
GET /api/ai/orchestrator/chat?message={message}&chatId={chatId}
```

系统会自动识别用户意图并路由到对应的 Agent：
- 简历相关 → ResumeAgent
- 薪资谈判 → NegotiationAgent
- 离职规划 → EscapeAgent
- 数据查询 → DataQueryRouter（零 LLM）
- 预约咨询 → ConsultationAgent
- 其他职场问题 → GeneralCareerAgent

SSE 事件：routing / agent-turn / message / clarification / trace / quality-review / error

### 会话管理接口

```
POST /api/session/login?username=xxx     # 游客登录
POST /api/session/create?title=xxx       # 创建会话
GET  /api/session/list                   # 活跃会话
GET  /api/session/archived               # 已归档会话
GET  /api/session/trash                  # 回收站
PUT  /api/session/{chatId}/title         # 重命名
PUT  /api/session/{chatId}/archive       # 归档
PUT  /api/session/{chatId}/unarchive     # 取消归档
PUT  /api/session/{chatId}/restore       # 恢复
DELETE /api/session/{chatId}             # 软删除
GET  /api/session/search?keyword=xxx     # 搜索会话
GET  /api/session/{chatId}/messages      # 消息历史
```

### 🆕 收藏接口

```
POST   /api/favorite                    # 添加收藏
DELETE /api/favorite/{favoriteId}       # 取消收藏
GET    /api/favorite/list               # 我的收藏列表
```

### 🆕 用量统计接口

```
GET /api/usage/stats                    # 我的使用统计
```

### 🆕 数据导入导出接口

```
GET  /api/export/all                    # 导出全量数据（ZIP）
POST /api/export/import                 # 导入数据
```

### 预约咨询流程示例

```
用户: 我想预约咨询
助手: 请告诉我您的姓名，以便我们为您预约。
用户: 张三
助手: 请提供您的联系方式（手机号或邮箱），以便我们与您确认预约。
用户: 13800138000
助手: 您希望预约什么时间？请提供具体日期和时间，例如：2024-01-15 14:00
用户: 明天下午3点
助手: 请问您想咨询什么主题？（可选，如：职业规划、简历优化等）
用户: 职业规划
助手: 请确认以下预约信息：
      姓名：张三
      联系方式：13800138000
      预约时间：2024-01-16 15:00
      咨询主题：职业规划
      
      请回复"确认"创建预约，或回复"修改"重新填写信息。
用户: 确认
助手: 预约创建成功！
      预约编号：xxx
      预约人：张三
      预约时间：2024-01-16 15:00
      
      我们会通过 13800138000 与您确认预约详情。
```

## 代码审查报告

### 已修复问题

1. **AppointmentRepository.java** - 语法错误
   - `new java.time.LocalDateTime.now()` → `LocalDateTime.now()`
   - 添加 `import java.time.LocalDateTime`

### 代码质量评估

| 模块 | 评分 | 说明 |
|------|------|------|
| ConsultationAgent | ⭐⭐⭐⭐ | 状态机清晰，功能完整 |
| CalendarService | ⭐⭐⭐⭐⭐ | 接口抽象良好，支持扩展 |
| MemoryCompression | ⭐⭐⭐⭐ | 策略模式优秀，配置灵活 |
| AppointmentRepository | ⭐⭐⭐ | 基础功能完整，建议增加数据库支持 |
| FollowUpTemplateConfig | ⭐⭐⭐⭐ | 模板化设计，支持热更新 |

### 建议改进

1. **数据库支持**：当前 AppointmentRepository 使用文件存储，建议增加 MySQL/PostgreSQL 支持
2. **单元测试**：为新增模块添加单元测试
3. **异常处理**：部分异常处理可以更细化
4. **日志规范**：统一日志格式和级别
5. **文档完善**：为新增 API 添加 Swagger 文档

RAG 核心特性实战：

![RAG 核心特性实战](https://pic.yupi.icu/1/1745224085267-57afea3b-2de9-44a0-8f53-49e338c0e6b9.png)

项目架构设计图：

![AI 智能体架构图](https://pic.yupi.icu/1/AI%E6%99%BA%E8%83%BD%E4%BD%93%E6%9E%B6%E6%9E%84%E5%9B%BE.png)

参考项目：https://github.com/liyupi/yu-ai-agent
