# 系统架构 · WorkPilot

## 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│  Frontend (Vue 3 + Vite)                                    │
│  yu-ai-agent-frontend/src/                                  │
│  views/ · components/ · api/index.js · router/index.js      │
└──────────────────────────┬──────────────────────────────────┘
                           │ REST + SSE (JWT Bearer / URL token)
┌──────────────────────────▼──────────────────────────────────┐
│  API Layer — controller/                                    │
│  AiController · SessionController · DocumentController …    │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  AppService Layer — service/                                │
│  OrchestratorAppService · SessionAppService · …               │
│  （Controller 不直接调用 Agent）                               │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  Agent Core — agent/                                        │
│  OrchestratorAgent → 子 Agent / 技能 / 工作流 / 范式           │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  Infrastructure                                             │
│  memory/ · nlu/ · trace/ · access/ · sandbox/ · workflow/ │
│  rag/ · tools/ · quality/ · metrics/ · skill/ · eval/      │
└─────────────────────────────────────────────────────────────┘
```

---

## 主请求链路（Orchestrator 聊天）

```
用户消息 (CareerAdvisor.vue)
  │
  ▼ EventSource GET /api/ai/orchestrator/chat?message=&chatId=&token=
AiController
  │
  ▼
OrchestratorAppService.chatStream()
  │  ├─ JWT 校验 / 会话归属
  │  └─ UsageTracker 记录
  ▼
OrchestratorAgent.runStream()
  │
  ├─→ KeywordRouter          快速路径（0 LLM）
  ├─→ SkillExecutor          YAML 技能匹配
  ├─→ NluPipeline             意图理解（1 LLM）
  │     ├─ 需澄清 → ClarificationHandler
  │     └─ 明确意图 → 路由
  ├─→ ParadigmSelector        ReAct / PlanAndSolve / Reflection
  ├─→ MemoryCoordinator       四层记忆注入
  ├─→ AccessDecisionService   投票式访问控制
  ├─→ 子 Agent 执行
  │     ResumeAgent / NegotiationAgent / EscapeAgent
  │     GeneralCareerAgent / ConsultationAgent
  ├─→ QualityGuardAgent       质量审查（可选）
  └─→ TraceRecorder           轨迹记录 → SSE trace 事件
  │
  ▼ SSE 流式返回
  events: routing · agent-turn · trace · quality-review · clarification · token
```

---

## Agent 继承体系

```
BaseAgent
  └── ReActAgent          思考-行动循环
        └── ToolCallAgent  工具调用 + 超时/重试

BaseParadigmAgent         范式基类
  ├── PlanAndSolveAgent   规划-执行-验证
  └── ReflectionAgent     生成-评估-反思-修正

DataEmployeeAgent         数据员工（产出 Artifact）
  ├── DataAnalystAgent
  ├── CareerCoachAgent
  ├── ProfileCuratorAgent
  ├── PromotionPlannerAgent
  └── LearningResourceRecommenderAgent

独立 Agent:
  OrchestratorAgent       主控路由（非 BaseAgent 子类）
  ConsultationAgent         预约状态机
  ResumeAgent / NegotiationAgent / EscapeAgent / GeneralCareerAgent
  QualityGuardAgent         质量审查
  AiChatAgent (app/)        遗留/demo 对话
  YuManus                   超级工具 Agent
```

---

## 横切关注点

| 关注点 | 实现位置 |
|--------|----------|
| JWT 鉴权 | `auth/JwtUtil.java`, `auth/AuthService.java` |
| 全局异常 | `exception/GlobalExceptionHandler.java` |
| CORS | `config/CorsConfig.java` |
| 会话三态 | `session/SessionManager.java` (ACTIVE/ARCHIVED/TRASH) |
| Token 预算 | `budget/TokenBudgetManager.java` |
| 循环检测 | `guard/EmbeddingLoopDetector.java` |
| Prompt 注入防护 | `guard/PromptInjectionDetector.java` |
| 事件总线 | `event/EventBusAdapter.java` |
| 持久化消息 | `message/PersistentMessageRepository.java` |

---

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 21, Spring Boot 3.4, Spring AI 1.0 |
| AI 模型 | DashScope (deepseek-v4-flash / qwen), Ollama |
| 向量库 | PgVector / SimpleVectorStore |
| 流式 | SSE (SseEmitter + Reactor Flux) |
| 前端 | Vue 3, Vite, Vue Router, Axios |
| 序列化 | Jackson (JSON), Kryo (ChatMemory) |
| 数据库 | PostgreSQL + Flyway |
| 监控 | Actuator, Micrometer, Prometheus |

---

## 关键配置文件

| 文件 | 作用 |
|------|------|
| `src/main/resources/application.yml` | 主配置 |
| `src/main/resources/application-prod.yml` | 生产覆盖 |
| `config/AgentConfig.java` | Agent Bean 装配 |
| `config/CorsConfig.java` | 跨域 |
| `config/CalendarConfig.java` | 飞书/钉钉日历 |

详见 [07-config-and-resources.md](./07-config-and-resources.md)
