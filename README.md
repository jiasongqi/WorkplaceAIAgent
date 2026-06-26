# 🚀 WorkPilot / 全场景职场生存智囊

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-blue)](https://docs.spring.io/spring-ai/reference/)
[![Vue.js](https://img.shields.io/badge/Vue.js-3-brightgreen?logo=vuedotjs)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> **全场景职场 AI 智囊平台，覆盖从求职到离职的全生命周期。**  
> **An all-scenario AI career coach platform covering the full lifecycle from job seeking to resignation.**

---

## ✨ Features / 功能亮点

| Feature / 功能 | Description / 说明 | Agent |
|----------------|-------------------|-------|
| 🎯 Multi-Agent Routing / 多Agent智能路由 | NLU intent recognition → 5 professional sub-agents / 意图识别 → 5个专业子Agent | OrchestratorAgent |
| 🧠 4-Layer Memory / 四层记忆系统 | Sliding window + Facts + Summary + Vector experience / 滑动窗口 + 事实存储 + 摘要 + 向量化经验 | MemoryCoordinator |
| 🔍 NLU Pipeline / 意图理解管道 | Single LLM call for intent + slots + routing / 单次LLM调用完成意图+槽位+路由 | NluPipeline |
| ⚡ Fast Path / 快速路径 | Keyword router skips LLM for simple messages / 简单消息跳过LLM调用 | KeywordRouter |
| 🛡️ Voting Access Control / 投票式访问控制 | Agent + MCP + Quota voters, one-vote veto / 三维投票，一票否决 | AccessDecisionService |
| 🔧 Tool Calling / 工具调用 | 7 built-in tools + MCP external services / 7个内置工具 + MCP外部服务 | ToolCallAgent |
| 📋 Workflow Engine / 工作流引擎 | 6 node types: Agent/Tool/Condition/Parallel/Loop/Approval / 6种节点类型 | WorkflowRuntime |
| 🏖️ Sandbox Execution / 沙箱执行 | 3-level strategy: None/Process/Docker / 三级策略：无/进程/Docker | SandboxFactory |
| 📊 Quality Guard / 质量守护 | 5-dimension scoring + Red Team mode / 5维评分 + 红队对抗 | QualityGuardAgent |
| 📝 Trace System / 执行轨迹 | 10+ step types, real-time SSE visualization / 10+步骤类型，实时SSE可视化 | TraceRecorder |
| 🎓 Eval Center / 评测中心 | YAML test suites + regression detection / YAML评测套件 + 回归检测 | EvalCenter |
| 💾 Blackboard Pattern / 黑板模式 | Data employees produce artifacts on shelf / 数据员工通过货架协作 | ArtifactShelf |

---

## 🏗️ Architecture / 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend (Vue 3)                         │
│  Home · CareerAdvisor · SuperAgent · Knowledge · Artifacts       │
│  Favorites · Usage · TraceDetail · CompareView · LoveMaster      │
└───────────────────────────────┬─────────────────────────────────┘
                                │ SSE / REST (JWT Auth)
┌───────────────────────────────┼─────────────────────────────────┐
│  API Layer: AiController · SessionController · DocumentController│
├───────────────────────────────┼─────────────────────────────────┤
│  AppService: OrchestratorAppService · SessionAppService · ...    │
├───────────────────────────────┼─────────────────────────────────┤
│  Agent Core:                                                    │
│    OrchestratorAgent ─┬─ KeywordRouter (fast path)              │
│                       ├─ NluPipeline (1 LLM call)               │
│                       ├─ SkillExecutor (YAML skills)            │
│                       ├─ ContextInjectionService                │
│                       ├─ QualityReviewHandler                    │
│                       └─ 5 Sub-Agents + Data Employees          │
├─────────────────────────────────────────────────────────────────┤
│  Infrastructure: ChatMemory · VectorStore · Trace · Sandbox      │
│  AccessControl · Artifact · UserProfile · EventBus · MemoryCoord │
└─────────────────────────────────────────────────────────────────┘
```

### Tech Stack / 技术栈

| Layer / 层 | Technology / 技术 |
|------------|------------------|
| Backend / 后端 | Java 21, Spring Boot 3.4, Spring AI 1.0 |
| AI Model / AI模型 | DashScope (deepseek-v4-flash / qwen), Ollama |
| Vector DB / 向量数据库 | PgVector / SimpleVectorStore |
| Streaming / 流式通信 | SSE (SseEmitter + Reactor Flux) |
| Frontend / 前端 | Vue 3, Vite, Vue Router, Axios, marked.js |
| Serialization / 序列化 | Jackson (JSON), Kryo (ChatMemory) |
| Security / 安全 | JWT, Voting Access Control, MCP Trust Levels |

---

## 🚀 Quick Start / 快速开始

### Prerequisites / 前置条件

- JDK 21+
- Node.js 18+
- DashScope API Key ([申请地址](https://dashscope.aliyun.com/))

### Backend / 后端

```bash
# Clone / 克隆
git clone https://github.com/your-username/agent_product.git
cd agent_product

# Configure API Key / 配置 API Key
# Edit src/main/resources/application.yml
# Set spring.ai.dashscope.api-key=your-key

# Run / 启动
mvn spring-boot:run
# Server starts at http://localhost:8123
```

### Frontend / 前端

```bash
cd yu-ai-agent-frontend

# Install dependencies / 安装依赖
npm install

# Run dev server / 启动开发服务器
npm run dev
# Frontend at http://localhost:3000
```

---

## 📦 Project Structure / 项目结构

```
agent_product/
├── src/main/java/com/yupi/yuaiagent/
│   ├── agent/                    # Agent Layer / Agent层
│   │   ├── OrchestratorAgent.java    # Main orchestrator / 主控编排
│   │   ├── ReActAgent.java           # ReAct pattern / ReAct模式
│   │   ├── ToolCallAgent.java        # Tool calling / 工具调用
│   │   ├── YuManus.java              # Super agent / 超级智能体
│   │   ├── runner/                   # V2 AgentRunner adapters / V2适配层
│   │   ├── data/                     # Data employee agents / 数据员工
│   │   └── output/                   # Agent output types / 输出类型
│   ├── nlu/                      # NLU Pipeline / 意图理解管道
│   │   ├── NluPipeline.java          # Pipeline orchestrator / 管道编排
│   │   ├── UnifiedNluExtractor.java  # Single LLM extraction / 单次LLM提取
│   │   ├── KeywordRouter.java        # Fast path routing / 快速路径
│   │   └── ...                       # AliasResolver, IntentReranker, etc.
│   ├── memory/                   # 4-Layer Memory / 四层记忆系统
│   │   ├── MemoryCoordinator.java    # Unified entry / 统一入口
│   │   ├── sliding/                  # L1: Sliding window / 滑动窗口
│   │   ├── fact/                     # L2: Fact store / 事实存储
│   │   ├── summary/                  # L3: Summary / 摘要
│   │   ├── experience/               # L4: Vector experience / 向量经验
│   │   └── extraction/               # Extraction pipeline / 提取管道
│   ├── workflow/                 # Workflow Engine / 工作流引擎
│   ├── sandbox/                  # Sandbox Execution / 沙箱执行
│   ├── access/                   # Access Control / 访问控制
│   ├── quality/                  # Quality Guard / 质量守护
│   ├── eval/                     # Eval Center / 评测中心
│   ├── trace/                    # Trace System / 执行轨迹
│   ├── artifact/                 # Artifact Lifecycle / 交付物生命周期
│   ├── prompt/                   # Prompt Registry / Prompt版本管理
│   ├── registry/                 # Agent Registry / Agent注册中心
│   ├── controller/               # REST Controllers / 接口层
│   └── service/                  # AppService Layer / 业务编排层
├── src/main/resources/
│   ├── skills/                   # YAML skill definitions / 技能定义
│   ├── agents/                   # Agent descriptors / Agent描述符
│   ├── permissions/              # Permission profiles / 权限画像
│   ├── eval/                     # Eval test suites / 评测套件
│   └── application.yml           # Configuration / 配置文件
├── src/test/                     # 41 test files / 41个测试文件
├── yu-ai-agent-frontend/         # Vue 3 Frontend / 前端
│   └── src/
│       ├── views/                # 11 pages / 11个页面
│       ├── components/           # Shared components / 共享组件
│       ├── api/                  # API calls / 接口调用
│       └── router/               # Vue Router / 路由配置
└── docs/                         # Documentation / 文档
    ├── WIKI.md                       # Project wiki / 项目Wiki
    ├── FEATURES.md                   # Feature layers / 功能分层文档
    ├── ARCHITECTURE.md               # Architecture / 架构文档
    ├── INTERVIEW_QA_SKILL.md         # Interview prep / 面试准备
    └── CODE_REVIEW_REPORT_2026-06-25.md  # Code review / 代码审查
```

---

## 🤖 Agent System / Agent 体系

### Routing Flow / 路由流程

```
User Message / 用户消息
  │
  ├─→ KeywordRouter (fast path, 0 LLM) / 快速路径
  │     ├─ Match → Route directly / 命中 → 直接路由
  │     └─ No match → NLU Pipeline / 未命中 → NLU管道
  │
  ├─→ SkillExecutor (YAML skills) / 技能匹配
  │
  ├─→ NluPipeline (1 LLM call) / 意图理解
  │     ├─ Needs clarification → Ask / 需要澄清 → 追问
  │     └─ Clear intent → Route / 明确意图 → 路由
  │
  └─→ Sub-Agent Execution / 子Agent执行
        ├─ ResumeAgent      (Job seeking / 求职)
        ├─ NegotiationAgent (Salary negotiation / 薪资谈判)
        ├─ EscapeAgent      (Resignation / 离职规划)
        ├─ ConsultationAgent(Appointment / 预约咨询)
        └─ GeneralCareerAgent(Career advice / 通用职场)
```

### Data Employees / 数据员工

| Agent / Agent | Output / 产出 |
|---------------|---------------|
| DataAnalystAgent | Analysis report / 数据分析报告 |
| CareerCoachAgent | Coaching plan / 岗位辅导方案 |
| ProfileCuratorAgent | User profile / 用户画像整理 |
| PromotionPlannerAgent | Promotion path / 晋升路径规划 |
| LearningResourceRecommenderAgent | Resources / 学习资源推荐 |

---

## 🧠 Memory System / 记忆系统

```
MemoryCoordinator.assembleContext(userId, chatId, agentType)
  │
  ├─ L1 SlidingWindowLayer  — Recent messages / 最近消息
  ├─ L2 FactStoreLayer      — User facts (KV) / 用户事实
  ├─ L3 SummaryLayer        — Conversation summary / 对话摘要
  └─ L4 ExperienceStoreLayer — Vector experience / 向量经验

  Token Budget: 6000 tokens, priority L1 > L2 > L3 > L4
  Query: CompletableFuture parallel, 2000ms timeout per layer
  Extraction: Async post-conversation (single LLM call)
```

---

## 🔧 Tools & MCP / 工具与MCP

### Built-in Tools / 内置工具

| Tool / 工具 | Class / 类 | Description / 说明 |
|-------------|-----------|-------------------|
| Web Search / 联网搜索 | `WebSearchTool` | SearchAPI powered / SearchAPI驱动 |
| Web Scraping / 网页抓取 | `WebScrapingTool` | Jsoup extraction / Jsoup正文提取 |
| File Operations / 文件操作 | `FileOperationTool` | Local file R/W / 本地文件读写 |
| Resource Download / 资源下载 | `ResourceDownloadTool` | HTTP download / HTTP资源获取 |
| Terminal / 终端命令 | `TerminalOperationTool` | Shell execution / Shell执行 |
| PDF Generation / PDF生成 | `PDFGenerationTool` | iText + Asian fonts / iText+亚洲字体 |
| Terminate / 终止 | `TerminateTool` | Agent self-stop / Agent主动结束 |

### MCP Integration / MCP集成

- External MCP services via Spring AI MCP Client
- SSE + stdio connection modes
- Trust levels: VERIFIED(100) / PARTNER(70) / COMMUNITY(30) / PRIVATE(0)

---

## 📡 API Reference / API 参考

| Category / 分类 | Method | Path / 路径 | Auth / 鉴权 |
|----------------|--------|------------|-------------|
| Orchestrator Chat / 智能路由对话 | GET | `/ai/orchestrator/chat` | JWT |
| Manus Super Agent / 超级智能体 | GET | `/ai/manus/chat` | - |
| Basic Chat / 基础对话 | GET | `/ai/ai_chat/chat/sync\|sse\|sse_emitter` | - |
| RAG Chat / RAG对话 | GET | `/ai/ai_chat/rag/sync` | - |
| Tool Chat / 工具对话 | GET | `/ai/ai_chat/tools/sync` | - |
| Document / 文档管理 | POST/GET/DELETE | `/document/*` | - |
| Session / 会话管理 | ALL | `/session/*` | JWT |
| Favorites / 收藏 | POST/DELETE/GET | `/favorite/*` | JWT |
| Profile / 用户画像 | GET/DELETE | `/profile/me` | JWT |
| Artifacts / 交付物 | GET | `/artifact/*` | JWT |
| Trace / 轨迹查询 | GET | `/trace/*` | JWT |
| Usage / 用量统计 | GET | `/usage/stats` | JWT |
| Export / 导入导出 | GET/POST | `/export/*` | JWT |
| Health / 健康检查 | GET | `/health` | - |

---

## 🧪 Testing / 测试

```bash
# Run all tests / 运行所有测试
mvn test

# Run specific test / 运行指定测试
mvn test -Dtest=MemoryCoordinatorTest
mvn test -Dtest=AgentRoutingEvalTest
```

### Test Coverage / 测试覆盖

| Category / 类别 | Files / 文件数 | Coverage / 覆盖范围 |
|----------------|---------------|-------------------|
| Memory System / 记忆系统 | 9 | L1-L4 + Coordinator + Extraction |
| Execution Trace / 执行轨迹 | 10 | Model + Recorder + Repository + Controller |
| Appointment / 预约咨询 | 5 | Agent + Repository + Calendar + Validator |
| Agent Routing / Agent路由 | 3 | Routing eval + Fast path + Orchestrator |
| Tools / 工具 | 5 | Web search + Scraping + Download + PDF + File |
| RAG | 3 | VectorStore + DocumentLoader + MultiQuery |
| Other / 其他 | 6 | YuManus + AiChatAgent + App startup |
| **Total / 合计** | **41** | |

---

## 📄 Documentation / 文档

| Document / 文档 | Description / 说明 |
|----------------|-------------------|
| [WIKI.md](docs/WIKI.md) | Full project wiki / 完整项目Wiki |
| [FEATURES.md](docs/FEATURES.md) | Feature layers (L0-L27) / 功能分层文档 |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architecture design / 架构设计 |
| [INTERVIEW_QA_SKILL.md](docs/INTERVIEW_QA_SKILL.md) | Interview Q&A (30+ questions) / 面试问答手册 |
| [CODE_REVIEW_REPORT](docs/CODE_REVIEW_REPORT_2026-06-25.md) | Code review report / 代码审查报告 |
| [NLU Design v4.2](docs/nlu-layer-design-v4.2.md) | NLU pipeline design / NLU管道设计 |
| [Multi-Agent Architecture](docs/multi-agent-runtime-architecture.md) | Multi-agent runtime / 多Agent运行时架构 |

---

## 📄 License / 许可证

MIT License. See [LICENSE](LICENSE) for details.

---

> **Built with ❤️ by jsq** · Powered by Java 21 + Spring AI + Vue 3
