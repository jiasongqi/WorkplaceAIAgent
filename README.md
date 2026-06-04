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

- ⭐️ 飞书/钉钉日历 API 集成
- ⭐️ 对话记忆压缩（Token/轮数策略）
- ⭐️ 模板化追问机制
- ⭐️ 文件持久化存储（JSON）

## 项目结构

```
src/main/java/com/yupi/yuaiagent/
├── agent/                      # Agent 相关
│   ├── model/                  # 数据模型
│   │   ├── Appointment.java    # 预约记录实体
│   │   ├── CoreInformation.java # 核心信息实体
│   │   └── FollowUpQuestion.java # 追问问题实体
│   ├── AgentIntent.java        # 意图枚举（含 CONSULTATION）
│   ├── ConsultationAgent.java  # 🆕 预约咨询 Agent
│   ├── OrchestratorAgent.java  # 主控 Agent（路由分发）
│   ├── ResumeAgent.java        # 简历优化 Agent
│   ├── NegotiationAgent.java   # 薪资谈判 Agent
│   ├── EscapeAgent.java        # 离职规划 Agent
│   └── GeneralCareerAgent.java # 通用职场 Agent
├── calendar/                   # 🆕 日历服务
│   ├── CalendarService.java    # 日历服务接口
│   ├── CalendarEvent.java      # 日历事件模型
│   ├── CalendarServiceFactory.java # 日历服务工厂
│   ├── FeishuCalendarService.java  # 飞书日历实现
│   └── DingTalkCalendarService.java # 钉钉日历实现
├── chatmemory/                 # 🆕 对话记忆管理
│   ├── ChatMemoryManager.java  # 记忆管理器（增强版）
│   ├── FileBasedChatMemory.java # 文件记忆（增强版）
│   ├── CompressionStrategy.java # 压缩策略接口
│   ├── TokenCompressionStrategy.java # Token 压缩策略
│   ├── TurnCompressionStrategy.java  # 轮数压缩策略
│   └── MemoryCompressor.java   # 记忆压缩器
├── config/                     # 🆕 配置类
│   └── FollowUpTemplateConfig.java # 追问模板配置
├── repository/                 # 🆕 数据存储
│   └── AppointmentRepository.java # 预约记录存储
├── validation/                 # 🆕 验证器
│   └── InfoValidator.java      # 信息验证器
├── controller/                 # 控制器
│   └── AiController.java       # AI 接口（已更新）
└── ...
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
- **预约咨询 → ConsultationAgent** 🆕
- 其他职场问题 → GeneralCareerAgent

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
