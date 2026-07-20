# 全量索引 · WorkPilot Codemap

> 按「功能 → 文件路径」快速定位。路径均相对于项目根目录 `agent_product/`。

---

## 一、按业务功能

| 功能 | 后端入口 | 前端入口 | 配置/资源 |
|------|----------|----------|-----------|
| **主聊天（Orchestrator）** | `service/OrchestratorAppService.java` → `agent/OrchestratorAgent.java` | `views/CareerAdvisor.vue` | `permissions/*.yaml`, `skills/*.yaml` |
| **超级智能体 Manus** | `agent/YuManus.java` | `views/SuperAgent.vue` | `config/AgentConfig.java` |
| **基础 AI 对话** | `app/AiChatAgent.java` | `views/LoveMaster.vue` | — |
| **会话管理** | `service/SessionAppService.java` | `CareerAdvisor.vue` 侧边栏 | `session/SessionManager.java` |
| **知识库 RAG** | `service/DocumentAppService.java`, `rag/*` | `views/KnowledgeBase.vue` | `document/*.md` |
| **执行轨迹 Trace** | `trace/TraceRecorder.java` | `views/TraceDetail.vue`, `components/TraceTimelineView.vue` | — |
| **交付物 Artifact** | `artifact/ArtifactShelf.java`, `agent/data/*` | `views/ArtifactAdmin.vue` | — |
| **收藏** | `service/FavoriteAppService.java` | `views/Favorites.vue` | — |
| **用量统计** | `usage/UsageTracker.java` | `views/UsageDashboard.vue` | — |
| **用户画像** | `profile/UserProfileService.java` | `CareerAdvisor.vue` 画像面板 | — |
| **预约咨询** | `agent/ConsultationAgent.java` | Orchestrator 内嵌 | `templates/follow-up-templates.yml` |
| **导入导出** | `service/ExportAppService.java` | API 已定义，UI 未接 | — |
| **Agent 对比 A/B** | `controller/AiController.java` | `views/CompareView.vue` | — |
| **管理后台** | 多模块 | `views/AdminDashboard.vue` | — |

---

## 二、按技术模块

| 模块 | 包路径 | 核心类 |
|------|--------|--------|
| Agent 运行时 | `agent/` | `OrchestratorAgent`, `BaseAgent`, `ReActAgent`, `ToolCallAgent` |
| NLU 意图理解 | `nlu/` | `NluPipeline`, `ClarificationHandler`, `KeywordRouter` |
| 四层记忆 | `memory/` | `MemoryCoordinator`, `sliding/`, `fact/`, `summary/`, `experience/` |
| 工具系统 | `tools/` | `WebSearchTool`, `PDFGenerationTool`, `ToolRegistration` |
| 工具注册表 | `tools/registry/` | `ToolRegistryService`, `ToolDiscovery` |
| RAG | `rag/` | `QueryRewriter`, `PgVectorVectorStoreConfig`, `rerank/RerankService` |
| 工作流 | `workflow/` | `WorkflowMatcher`, `WorkflowRuntime`, `node/*` |
| 沙箱 | `sandbox/` | `SandboxFactory`, `DockerSandbox`, `ProcessSandbox` |
| 访问控制 | `access/`, `permission/` | `AccessDecisionService`, `AgentPermissionService` |
| 质量守护 | `quality/` | `QualityGuardAgent`, `QualityReview` |
| 评测中心 | `eval/` | `EvalCenter`, `eval/*.yaml` |
| 技能系统 | `skill/` | `SkillRegistry`, `SkillExecutor` |
| 执行轨迹 | `trace/` | `TraceRecorder`, `TraceRepository` |
| 监控指标 | `metrics/` | `AgentMetrics`, `AgentCircuitBreaker`, `AgentDiagnosticsEndpoint` |
| 范式 | `agent/paradigm/` | `PlanAndSolveAgent`, `ReflectionAgent`, `ParadigmService` |
| Reflexion | `agent/reflexion/` | `ReflexionMemory`, `ReflexionService` |
| 事件总线 | `event/` | `EventBusAdapter`, `GovernanceEventListener` |
| 鉴权 | `auth/` | `AuthService`, `JwtUtil` |
| 持久化 | `repository/`, `message/` | JPA entities, `PersistentMessageRepository` |
| 安全防护 | `guard/`, `budget/` | `PromptInjectionDetector`, `TokenBudgetManager` |

---

## 三、按 API 前缀

| 前缀 | Controller | 说明 |
|------|------------|------|
| `/api/session/*` | `SessionController` | 登录、会话 CRUD、归档、搜索 |
| `/api/ai/*` | `AiController` | 聊天 SSE、RAG、工具、Manus |
| `/api/document/*` | `DocumentController` | 知识库文档 |
| `/api/trace/*` | `TraceController` | 执行轨迹查询 |
| `/api/artifact/*` | `ArtifactController` | 交付物（admin） |
| `/api/favorite/*` | `FavoriteController` | 收藏 |
| `/api/profile/*` | `ProfileController` | 用户画像 |
| `/api/export/*` | `ExportController` | 导入导出 |
| `/api/usage/*` | `UsageController` | 用量统计 |
| `/api/feedback/*` | `FeedbackController` | 反馈 |
| `/api/health` | `HealthController` | 健康检查 |
| `/api/actuator/*` | Spring Actuator | 监控、诊断、Prometheus |

---

## 四、按前端路由

| 路由 | 页面 | 主要后端 API |
|------|------|-------------|
| `/` | Home.vue | `/session/list` |
| `/chat/career` | CareerAdvisor.vue | `/ai/orchestrator/chat` (SSE) |
| `/chat/super` | SuperAgent.vue | `/ai/manus/chat` (SSE) |
| `/knowledge` | KnowledgeBase.vue | `/document/*` |
| `/artifacts` | ArtifactAdmin.vue | `/artifact/*` |
| `/favorites` | Favorites.vue | `/favorite/*` |
| `/usage` | UsageDashboard.vue | `/usage/stats` |
| `/compare` | CompareView.vue | `/ai/orchestrator/chat` ×2 |
| `/trace/:traceId` | TraceDetail.vue | `/trace/{traceId}` |
| `/admin` | AdminDashboard.vue | 导航 hub |
| `/love-master` | LoveMaster.vue | `/ai/ai_chat/chat/sse` |

---

## 五、测试文件分布

| 类别 | 路径模式 | 数量 |
|------|----------|------|
| 记忆系统 | `src/test/**/memory/**` | ~9 |
| 执行轨迹 | `src/test/**/trace/**` | ~10 |
| Agent 路由 | `src/test/**/agent/**`, `*Routing*` | ~3 |
| 工具 | `src/test/**/tools/**` | ~5 |
| RAG | `src/test/**/rag/**` | ~3 |
| 预约咨询 | `src/test/**/consultation/**` | ~5 |

运行：`mvn test` 或 `mvn test -Dtest=ClassName`

---

## 六、开发检查清单

- [ ] Controller 只做 HTTP 适配，业务逻辑放 AppService
- [ ] 新 Agent 需在 NLU + Orchestrator + AgentConfig 三处注册
- [ ] SSE 接口 token 通过 URL 参数传递（EventSource 不支持 Header）
- [ ] 前端 API 基址：dev `http://localhost:8123/api`，prod `/api`
- [ ] 权限画像放 `permissions/`，技能放 `skills/`
- [ ] 全局 API 前缀 `/api`（`application.yml` → `server.servlet.context-path`）
