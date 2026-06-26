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

### 核心能力 (L0-L27)

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

### Hello-Agents 优化 (L28-L33)

| Feature / 功能 | Description / 说明 | Component |
|----------------|-------------------|-----------|
| 📈 Performance Metrics / 性能指标 | Actuator + Micrometer + Prometheus / 指标监控 | AgentMetrics |
| 🔄 Classic Paradigms / 经典范式 | ReAct / Plan-and-Solve / Reflection / 三种推理范式 | ParadigmService |
| 🎯 Context Engineering / 上下文工程 | Relevance scoring + Dynamic budget / 相关性评分+动态预算 | ContextEngineer |
| 📦 Tool Registry / 工具注册 | Dynamic registration + Capability discovery / 动态注册+能力发现 | ToolRegistryService |
| 🧠 Reflexion Memory / 失败记忆 | Learn from failures / 从失败中学习 | ReflexionMemory |
| 🔀 RAG Rerank / 重排序 | Keyword overlap + Document quality / 关键词重叠+文档质量 | RerankService |
| 🔌 Circuit Breaker / 断路器 | Auto timeout protection / 超时自动降级 | AgentCircuitBreaker |
| 🔍 Diagnostics / 诊断 | Per-agent metrics + Health assessment / Agent指标+健康评估 | AgentDiagnosticsEndpoint |

---

## 🏗️ Architecture / 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Frontend (Vue 3)                                  │
│  Home · CareerAdvisor · SuperAgent · Knowledge · Artifacts                  │
│  Favorites · Usage · TraceDetail · CompareView · LoveMaster                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │ SSE / REST (JWT Auth)
┌─────────────────────────────────────────────────────────────────────────────┐
│                           API Layer                                         │
│  AiController · SessionController · DocumentController · FeedbackController │
├─────────────────────────────────────────────────────────────────────────────┤
│                           AppService Layer                                  │
│  OrchestratorAppService · SessionAppService · FavoriteAppService            │
├─────────────────────────────────────────────────────────────────────────────┤
│                           Agent Core                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  OrchestratorAgent (主控)                                            │   │
│  │    ├─ KeywordRouter        — Fast path (0 LLM)                      │   │
│  │    ├─ NluPipeline          — Intent understanding (1 LLM)           │   │
│  │    ├─ ParadigmSelector     — Paradigm selection (ReAct/PaS/Reflect) │   │
│  │    ├─ ContextEngineer      — Context optimization                   │   │
│  │    ├─ ReflexionService     — Failure learning                       │   │
│  │    ├─ ContextInjectionService — Context injection                   │   │
│  │    ├─ QualityReviewHandler — Quality review                         │   │
│  │    └─ 5 Sub-Agents + Data Employees                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Paradigm Agents: ReAct · PlanAndSolve · Reflection                 │   │
│  │  Tool System: ToolRegistry · ToolDiscovery · RerankService          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────────┤
│                           Infrastructure                                    │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐      │
│  │  Memory      │ │  Monitoring  │ │  Security    │ │  Workflow    │      │
│  │  Coordinator │ │  AgentMetrics│ │  Access      │ │  Runtime     │      │
│  │  4-Layer     │ │  CircuitBrkr │ │  Sandbox     │ │  6 Nodes     │      │
│  │  Rerank      │ │  Diagnostics │ │  Injection   │ │  Persistence │      │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘      │
└─────────────────────────────────────────────────────────────────────────────┘
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
| Monitoring / 监控 | Actuator, Micrometer, Prometheus |

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

### Monitoring / 监控

```bash
# Health check / 健康检查
curl http://localhost:8123/api/actuator/health

# Agent diagnostics / Agent诊断
curl http://localhost:8123/api/actuator/agent-diagnostics

# Prometheus metrics / Prometheus指标
curl http://localhost:8123/api/actuator/prometheus
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
│   │   ├── paradigm/                 # Classic paradigms / 经典范式
│   │   │   ├── PlanAndSolveAgent.java    # Plan-and-Solve / 规划执行
│   │   │   ├── ReflectionAgent.java      # Reflection / 反思修正
│   │   │   └── ParadigmService.java      # Paradigm service / 范式服务
│   │   ├── reflexion/                # Reflexion memory / 失败记忆
│   │   │   ├── ReflexionMemory.java      # Failure storage / 失败存储
│   │   │   └── ReflexionService.java     # Reflexion service / Reflexion服务
│   │   ├── runner/                   # V2 AgentRunner adapters / V2适配层
│   │   ├── data/                     # Data employee agents / 数据员工
│   │   └── output/                   # Agent output types / 输出类型
│   ├── nlu/                      # NLU Pipeline / 意图理解管道
│   ├── memory/                   # 4-Layer Memory / 四层记忆系统
│   │   ├── MemoryCoordinator.java    # Unified entry / 统一入口
│   │   ├── context/                  # Context engineering / 上下文工程
│   │   │   ├── ContextEngineer.java      # Context optimizer / 上下文优化
│   │   │   ├── DynamicBudgetAllocator.java # Dynamic budget / 动态预算
│   │   │   └── ContextRelevanceScorer.java # Relevance scoring / 相关性评分
│   │   ├── sliding/                  # L1: Sliding window / 滑动窗口
│   │   ├── fact/                     # L2: Fact store / 事实存储
│   │   ├── summary/                  # L3: Summary / 摘要
│   │   ├── experience/               # L4: Vector experience / 向量经验
│   │   └── extraction/               # Extraction pipeline / 提取管道
│   ├── metrics/                  # Monitoring / 监控指标
│   │   ├── AgentMetrics.java         # Custom metrics / 自定义指标
│   │   ├── AgentExecutionMetrics.java # Per-agent metrics / Agent指标
│   │   ├── AgentCircuitBreaker.java  # Circuit breaker / 断路器
│   │   ├── AgentMetricsEndpoint.java # Metrics endpoint / 指标端点
│   │   └── AgentDiagnosticsEndpoint.java # Diagnostics / 诊断端点
│   ├── tools/                    # Tool system / 工具系统
│   │   └── registry/                 # Tool registry / 工具注册
│   │       ├── ToolRegistry.java         # Dynamic registry / 动态注册表
│   │       ├── ToolDiscovery.java        # Auto discovery / 自动发现
│   │       └── ToolRegistryService.java  # Registry service / 注册服务
│   ├── rag/                      # RAG system / RAG系统
│   │   └── rerank/                   # Rerank service / 重排序服务
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
├── stress-test.js                # k6 stress test / k6压测脚本
├── stress-test.sh                # Shell stress test / Shell压测脚本
└── docs/                         # Documentation / 文档
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
  ├─→ ParadigmSelector / 范式选择
  │     ├─ REACT → ToolCallAgent (default / 默认)
  │     ├─ PLAN_AND_SOLVE → PlanAndSolveAgent (complex tasks / 复杂任务)
  │     └─ REFLECTION → ReflectionAgent (high quality / 高质量)
  │
  └─→ Sub-Agent Execution / 子Agent执行
        ├─ ResumeAgent      (Job seeking / 求职)
        ├─ NegotiationAgent (Salary negotiation / 薪资谈判)
        ├─ EscapeAgent      (Resignation / 离职规划)
        ├─ ConsultationAgent(Appointment / 预约咨询)
        └─ GeneralCareerAgent(Career advice / 通用职场)
```

### Classic Paradigms / 经典范式

| Paradigm / 范式 | Use Case / 适用场景 | Flow / 流程 |
|-----------------|---------------------|-------------|
| REACT | Interactive tasks, tool calling / 交互式任务 | Think → Act → Observe → Loop |
| PLAN_AND_SOLVE | Complex multi-step tasks / 复杂多步骤任务 | Plan → Execute → Verify |
| REFLECTION | High quality output / 高质量输出 | Generate → Evaluate → Reflect → Revise |

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
  
  Context Engineering / 上下文工程:
  - Relevance scoring: Keyword overlap + Density / 相关性评分
  - Dynamic budget: By query type (CONVERSATIONAL/FACTUAL/ANALYTICAL)
  - Key info extraction: Entities + Topics + Intent
```

---

## 📡 Monitoring / 监控

### Endpoints / 端点

| Endpoint / 端点 | Description / 说明 |
|-----------------|-------------------|
| `/actuator/health` | Health check / 健康检查 |
| `/actuator/agent-metrics` | Custom agent metrics / 自定义Agent指标 |
| `/actuator/agent-diagnostics` | Per-agent diagnostics / Agent诊断 |
| `/actuator/prometheus` | Prometheus metrics / Prometheus指标 |

### Key Metrics / 关键指标

| Metric / 指标 | Type / 类型 | Description / 说明 |
|---------------|-------------|-------------------|
| `agent_execution_duration` | Timer | Agent execution duration / Agent执行耗时 |
| `agent_execution_success` | Counter | Successful executions / 成功执行次数 |
| `agent_execution_failure` | Counter | Failed executions / 失败执行次数 |
| `agent_execution_timeout` | Counter | Timeout count / 超时次数 |
| `agent_token_consumption` | Summary | Token usage per execution / Token消耗 |
| `agent_active_count` | Gauge | Currently active agents / 活跃Agent数 |

### Circuit Breaker / 断路器

| State / 状态 | Condition / 条件 |
|--------------|-----------------|
| CLOSED | Normal operation / 正常运行 |
| OPEN | Failure rate > 50% or Timeout rate > 30% / 失败率>50%或超时率>30% |
| HALF_OPEN | Auto recovery after 30s / 30秒后自动恢复 |

---

## 🔧 Tools & MCP / 工具与MCP

### Tool Registry / 工具注册

```java
// Dynamic registration / 动态注册
toolRegistryService.register("myTool", "Description", Set.of("search"), callback);

// Capability discovery / 能力发现
ToolCallback[] tools = toolRegistryService.getToolCallbacksByCapability("search");

// Auto discovery from Spring Context / 从Spring Context自动发现
// All ToolCallback beans are auto-registered
```

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

---

## 🧪 Testing / 测试

```bash
# Run all tests / 运行所有测试
mvn test

# Run specific test / 运行指定测试
mvn test -Dtest=MemoryCoordinatorTest
mvn test -Dtest=AgentRoutingEvalTest

# Stress test / 压测
k6 run --vus 10 --duration 30s stress-test.js
./stress-test.sh 5 20
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
| [WIKI.md](docs/WIKI.md) | Full project wiki (L0-L33) / 完整项目Wiki |
| [FEATURES.md](docs/FEATURES.md) | Feature layers / 功能分层文档 |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architecture design / 架构设计 |
| [INTERVIEW_QA_SKILL.md](docs/INTERVIEW_QA_SKILL.md) | Interview Q&A (50+ questions) / 面试问答手册 |
| [CODE_REVIEW_REPORT](docs/CODE_REVIEW_REPORT_2026-06-26.md) | Code review (9.3/10) / 代码审查报告 |
| [HELLO_AGENTS_SUMMARY](docs/HELLO_AGENTS_SUMMARY.md) | Hello-Agents study notes / Hello-Agents学习笔记 |
| [NLU Design v4.2](docs/nlu-layer-design-v4.2.md) | NLU pipeline design / NLU管道设计 |
| [Multi-Agent Architecture](docs/multi-agent-runtime-architecture.md) | Multi-agent runtime / 多Agent运行时架构 |

---

## 📊 Capability Layers / 能力层级

```
L0-L27: Core capabilities / 核心能力
L28: Performance monitoring / 性能监控
L29: Classic paradigms / 经典范式
L30: Context engineering / 上下文工程
L31: Tool registry / 工具注册
L32: Reflexion memory / 失败记忆
L33: RAG rerank / 重排序
```

---

## 📄 License / 许可证

MIT License. See [LICENSE](LICENSE) for details.

---

> **Built with ❤️ by jsq** · Powered by Java 21 + Spring AI + Vue 3
