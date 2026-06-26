# WorkPilot Architecture / 架构设计

> Last updated: 2026-06-26 (v1.5)
> Capability layers: L0-L33

---

## 1. High-Level Architecture / 高层架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Presentation Layer / 展示层                       │
│                              (Vue 3 Frontend)                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │   Home   │ │  Career  │ │  Super   │ │Knowledge │ │ Artifacts│         │
│  │  工作台   │ │  Advisor │ │  Agent   │ │  知识库   │ │  交付物   │         │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │Favorites │ │  Usage   │ │  Trace   │ │ Compare  │ │LoveMaster│         │
│  │   收藏   │ │   用量   │ │   轨迹   │ │   对比   │ │  沟通助手 │         │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘         │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ SSE / REST (JWT Auth)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           API Layer / 接口层                                 │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐               │
│  │  AiController   │ │SessionController│ │DocumentController│              │
│  │  对话接口        │ │  会话管理        │ │  文档管理         │              │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘               │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐               │
│  │TraceController  │ │FeedbackController│ │ArtifactController│             │
│  │  轨迹查询        │ │  反馈管理        │ │  交付物管理       │              │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       AppService Layer / 业务编排层                          │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐               │
│  │OrchestratorApp  │ │SessionAppService│ │FavoriteAppService│              │
│  │Service          │ │  会话编排        │ │  收藏编排         │              │
│  │  主控编排        │ │                 │ │                  │              │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Agent Core / Agent 核心层                          │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    OrchestratorAgent (主控)                          │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  │   │
│  │  │  Keyword    │ │  NLU        │ │  Paradigm   │ │  Context    │  │   │
│  │  │  Router     │ │  Pipeline   │ │  Selector   │ │  Engineer   │  │   │
│  │  │  快速路径    │ │  意图理解    │ │  范式选择    │ │  上下文优化  │  │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘  │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  │   │
│  │  │  Reflexion  │ │  Context    │ │  Quality    │ │  Skill      │  │   │
│  │  │  Service    │ │  Injection  │ │  Review     │ │  Executor   │  │   │
│  │  │  失败学习    │ │  上下文注入  │ │  质量审查    │ │  技能执行    │  │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Sub-Agents / 子 Agent                             │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  │   │
│  │  │  Resume     │ │ Negotiation │ │   Escape    │ │Consultation │  │   │
│  │  │  Agent      │ │   Agent     │ │   Agent     │ │   Agent     │  │   │
│  │  │  求职        │ │  薪资谈判    │ │  离职规划    │ │  预约咨询    │  │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘  │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                   │   │
│  │  │  General    │ │   YuManus   │ │   Data      │                   │   │
│  │  │  Career     │ │  Super Agent│ │  Employees  │                   │   │
│  │  │  通用职场    │ │  超级智能体  │ │  数据员工    │                   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Paradigm Agents / 范式 Agent                      │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                   │   │
│  │  │  ReAct      │ │PlanAndSolve │ │ Reflection  │                   │   │
│  │  │  Agent      │ │   Agent     │ │   Agent     │                   │   │
│  │  │  思考-行动   │ │  规划-执行   │ │  反思-修正   │                   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Infrastructure Layer / 基础设施层                      │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                    Memory System / 记忆系统                           │  │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐       │  │
│  │  │    L1      │ │    L2      │ │    L3      │ │    L4      │       │  │
│  │  │  Sliding   │ │   Fact     │ │  Summary   │ │ Experience │       │  │
│  │  │  Window    │ │   Store    │ │   Layer    │ │   Store    │       │  │
│  │  │  滑动窗口   │ │  事实存储   │ │   摘要     │ │  向量经验   │       │  │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘       │  │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐       │  │
│  │  │  Context   │ │  Token     │ │  Extraction│ │  Memory    │       │  │
│  │  │  Engineer  │ │  Budget    │ │  Pipeline  │ │  Coordinator│      │  │
│  │  │  上下文工程  │ │  预算分配   │ │  提取管道   │ │  记忆协调   │       │  │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                    Monitoring / 监控系统                               │  │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐       │  │
│  │  │  Agent     │ │  Agent     │ │  Agent     │ │  Agent     │       │  │
│  │  │  Metrics   │ │  Execution │ │  Circuit   │ │  Diagnostics│      │  │
│  │  │  自定义指标  │ │  Metrics   │ │  Breaker   │ │  诊断端点   │       │  │
│  │  │            │ │  执行指标   │ │  断路器     │ │            │       │  │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                    Tool System / 工具系统                              │  │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐       │  │
│  │  │   Tool     │ │   Tool     │ │   Tool     │ │   Rerank   │       │  │
│  │  │  Registry  │ │  Discovery │ │  Callbacks │ │  Service   │       │  │
│  │  │  工具注册表  │ │  自动发现   │ │  工具回调   │ │  重排序     │       │  │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                    Security & Workflow / 安全与工作流                   │  │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐       │  │
│  │  │  Access    │ │  Sandbox   │ │  Workflow  │ │  Quality   │       │  │
│  │  │  Control   │ │  沙箱执行   │ │  Runtime   │ │  Guard     │       │  │
│  │  │  访问控制   │ │            │ │  工作流引擎  │ │  质量守护   │       │  │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                    Data Layer / 数据层                                 │  │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐       │  │
│  │  │  Chat      │ │  Vector    │ │  File      │ │  JSON      │       │  │
│  │  │  Memory    │ │  Store     │ │  Storage   │ │  Storage   │       │  │
│  │  │  对话记忆   │ │  向量存储   │ │  文件存储   │ │  JSON存储   │       │  │
│  │  │  (Kryo)    │ │  (PgVector)│ │            │ │            │       │  │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Data Flow / 数据流

### 2.1 Request Flow / 请求流程

```
User Request / 用户请求
    │
    ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   API       │ ──► │  AppService │ ──► │   Agent     │
│   Layer     │     │   Layer     │     │   Core      │
└─────────────┘     └─────────────┘     └─────────────┘
                                              │
        ┌─────────────────────────────────────┼─────────────────────────────────────┐
        │                                     │                                     │
        ▼                                     ▼                                     ▼
┌─────────────┐                       ┌─────────────┐                       ┌─────────────┐
│   NLU       │                       │  Paradigm   │                       │  Sub-Agent  │
│  Pipeline   │                       │  Selector   │                       │  Execution  │
│  意图理解    │                       │  范式选择    │                       │  子Agent执行 │
└─────────────┘                       └─────────────┘                       └─────────────┘
        │                                     │                                     │
        ▼                                     ▼                                     ▼
┌─────────────┐                       ┌─────────────┐                       ┌─────────────┐
│   Route     │                       │  Paradigm   │                       │  Tool       │
│   Hint      │                       │  Agent      │                       │  Calling    │
│  路由提示    │                       │  范式Agent   │                       │  工具调用    │
└─────────────┘                       └─────────────┘                       └─────────────┘
```

### 2.2 Memory Flow / 记忆流程

```
User Message / 用户消息
    │
    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MemoryCoordinator.assembleContext()                       │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ContextEngineer.analyzeQuery()                                     │   │
│  │  ├─ KeyInfoExtractor: Extract entities/topics/intent                │   │
│  │  ├─ DynamicBudgetAllocator: Allocate token budget by query type     │   │
│  │  └─ ContextRelevanceScorer: Score and rank memory items             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│                                      ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  CompletableFuture.allOf() — Parallel Query / 并行查询               │   │
│  │                                                                     │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  │   │
│  │  │ L1 Sliding  │ │ L2 Fact     │ │ L3 Summary  │ │ L4 Experi   │  │   │
│  │  │ Window      │ │ Store       │ │ Layer       │ │ ence Store  │  │   │
│  │  │ 滑动窗口     │ │ 事实存储    │ │ 摘要层       │ │ 向量经验     │  │   │
│  │  │ (2000ms)    │ │ (2000ms)    │ │ (2000ms)    │ │ (2000ms)    │  │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│                                      ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  TokenBudgetAllocator — Allocate by priority / 按优先级分配           │   │
│  │  L1 (60%) > L2 (15%) > L3 (10%) > L4 (15%) — Default               │   │
│  │  Dynamic allocation by query type / 按查询类型动态分配                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│                                      ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  SystemMessage — Inject into prompt / 注入到提示词                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Component Design / 组件设计

### 3.1 Agent Paradigm Architecture / Agent 范式架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ParadigmService — 范式服务                                │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ParadigmSelector — 范式选择器                                       │   │
│  │  ├─ Intent mapping: DATA_QUERY → PLAN_AND_SOLVE                    │   │
│  │  ├─ Keyword heuristics: "分析" → PLAN_AND_SOLVE                    │   │
│  │  └─ Default: REACT                                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│                                      ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  BaseParadigmAgent — 抽象基类                                       │   │
│  │  ├─ ChatClient management                                          │   │
│  │  ├─ Message history                                                │   │
│  │  ├─ Trace integration                                              │   │
│  │  └─ Execution lifecycle                                            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│        ┌─────────────────────────────┼─────────────────────────────┐        │
│        │                             │                             │        │
│        ▼                             ▼                             ▼        │
│  ┌─────────────┐           ┌─────────────┐           ┌─────────────┐       │
│  │  ReAct      │           │PlanAndSolve │           │ Reflection  │       │
│  │  Agent      │           │   Agent     │           │   Agent     │       │
│  │             │           │             │           │             │       │
│  │ while(      │           │ 1. Plan     │           │ 1. Generate │       │
│  │  shouldAct) │           │ 2. Execute  │           │ 2. Evaluate │       │
│  │  think()    │           │ 3. Verify   │           │ 3. Reflect  │       │
│  │  act()      │           │             │           │ 4. Revise   │       │
│  │  observe    │           │             │           │             │       │
│  └─────────────┘           └─────────────┘           └─────────────┘       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Monitoring Architecture / 监控架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Monitoring System / 监控系统                               │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  AgentExecutionMetrics — Per-agent metrics / Agent指标               │   │
│  │  ├─ agent_execution_duration (Timer)                                │   │
│  │  ├─ agent_execution_success (Counter)                               │   │
│  │  ├─ agent_execution_failure (Counter)                               │   │
│  │  ├─ agent_execution_timeout (Counter)                               │   │
│  │  ├─ agent_token_consumption (Summary)                               │   │
│  │  └─ agent_tool_calls (Summary)                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│                                      ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  AgentCircuitBreaker — 断路器                                       │   │
│  │  ├─ CLOSED → OPEN (failure rate > 50%)                              │   │
│  │  ├─ OPEN → HALF_OPEN (30s timeout)                                  │   │
│  │  └─ HALF_OPEN → CLOSED (success)                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│                                      ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  AgentDiagnosticsEndpoint — 诊断端点                                 │   │
│  │  GET /actuator/agent-diagnostics                                    │   │
│  │  ├─ Global overview (active agents, success rate)                   │   │
│  │  ├─ Per-agent metrics (duration, tokens, tools)                     │   │
│  │  ├─ Circuit breaker status                                          │   │
│  │  └─ Health assessment + Recommendations                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Capability Layers / 能力层级

| Layer | Name | Description |
|-------|------|-------------|
| L0 | Basic Chat | Single/multi-turn dialogue + memory persistence |
| L1 | RAG Knowledge | 11 career documents + Multi-Query retrieval |
| L2 | Tool Calling | Web search / File / Scraping / Download / Terminal / PDF |
| L3 | MCP | External MCP services (image search) |
| L4 | Super Agent | ReAct autonomous planning + tool loop |
| L5 | Multi-Agent | Intent recognition → 5 professional agents |
| L6 | Consultation | State machine follow-up + calendar integration |
| L7 | Memory Compression | Token/turn strategy + LLM summary |
| L8 | Blackboard | Artifact shelf + data employees + user profile |
| L9 | Skills | YAML declarative skill hot-loading |
| L10 | Quality Guard | Auto review (Review/RedTeam) + risk grading |
| L11 | Favorites | Message snapshot + orphan marking |
| L12 | Usage Tracking | 7 event types + multi-dimension stats |
| L13 | Import/Export | ZIP full backup/restore |
| L14 | Chat Search | Weighted scoring + time decay |
| L15 | Persistent Messages | Source of Truth + dual indexing |
| L16 | NLU Pipeline | 1 LLM + alias + slots + intent + clarification |
| L17 | Multi-Agent Runtime | Chat mode + Task Orchestrator + AgentRunner |
| L18 | Workflow Engine | 6 node types + instance state + persistence |
| L19 | Sandbox | Docker/Process isolation + 5-layer protection |
| L20 | Access Control | Voting decision + Agent permission + MCP trust |
| L21 | Agent Registry | YAML declarative + Marketplace ready |
| L22 | Eval Center | Regression testing + release evaluation |
| L23 | Prompt Version | Multi-version + grayscale + A/B testing |
| L24 | Artifact Lifecycle | DRAFT→REVIEWING→APPROVED→PUBLISHED→ARCHIVED |
| L25 | Event Bus | Async governance events + audit log |
| L26 | Security | Loop detection + tool result grading + token budget |
| L27 | Memory System | 4-layer memory + async extraction + token budget |
| L28 | Performance Metrics | Actuator + Micrometer + Prometheus |
| L29 | Classic Paradigms | ReAct / Plan-and-Solve / Reflection |
| L30 | Context Engineering | Relevance scoring + dynamic budget |
| L31 | Tool Registry | Dynamic registration + capability discovery |
| L32 | Reflexion | Failure trajectory memory |
| L33 | RAG Rerank | Keyword overlap + document quality |

---

## 5. Design Principles / 设计原则

### 5.1 Core Principles / 核心原则

1. **Permission ≠ Credential** — Authorization and access credentials are decoupled
2. **Progressive Enhancement** — Simple → Iterative → Advanced
3. **Observability First** — Trace everything, metrics everywhere
4. **Fail-Safe** — Circuit breakers, timeouts, graceful degradation

### 5.2 Agent Design / Agent 设计

1. **Deterministic Routing + Autonomous Execution** — Orchestrator routes deterministically, sub-agents execute autonomously
2. **Paradigm Selection** — Match task type to optimal reasoning strategy
3. **Learning from Failures** — Reflexion memory prevents repeated mistakes
4. **Context Optimization** — Dynamic budget allocation based on query type

### 5.3 Monitoring Design / 监控设计

1. **Per-Agent Metrics** — Track each agent independently
2. **Circuit Breaker** — Prevent cascading failures
3. **Diagnostics Endpoint** — One-stop health assessment
4. **Prometheus Integration** — Standard metrics export

---

*Last updated: 2026-06-26*
*Version: 1.5*
