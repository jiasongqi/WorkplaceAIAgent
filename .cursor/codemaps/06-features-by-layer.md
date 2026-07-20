# 功能分层 L0-L33 → 代码映射

> 完整说明见 `docs/FEATURES.md`，本文档提供**快速定位表**。

---

## L0-L5 · 基础能力

| 层 | 功能 | 后端关键文件 | 前端 | API |
|----|------|-------------|------|-----|
| L0 | 基础对话 + 记忆 | `app/AiChatAgent.java`, `chatmemory/ChatMemoryManager.java` | LoveMaster | `/ai/ai_chat/chat/*` |
| L1 | RAG 知识库 | `rag/QueryRewriter.java`, `rag/PgVectorVectorStoreConfig.java` | KnowledgeBase | `/document/*`, `/ai/ai_chat/rag/sync` |
| L2 | 工具调用 | `tools/*.java`, `tools/ToolRegistration.java` | — | `/ai/ai_chat/tools/sync` |
| L3 | MCP 外部服务 | `mcp/McpTrustService.java`, `mcp-servers.json` | — | `/ai/ai_chat/mcp/sync` |
| L4 | Manus 超级智能体 | `agent/YuManus.java` | SuperAgent | `/ai/manus/chat` |
| L5 | Multi-Agent 路由 | `agent/OrchestratorAgent.java`, `nlu/NluPipeline.java` | CareerAdvisor | `/ai/orchestrator/chat` |

---

## L6-L15 · 业务功能

| 层 | 功能 | 后端关键文件 | 前端 | API |
|----|------|-------------|------|-----|
| L6 | 预约咨询 | `agent/ConsultationAgent.java`, `calendar/*`, `validation/InfoValidator.java` | Orchestrator 内 | — |
| L7 | 记忆压缩 | `chatmemory/TokenCompressionStrategy.java`, `chatmemory/CompressionStrategy.java` | — | — |
| L8 | 黑板协作/交付物 | `artifact/ArtifactShelf.java`, `agent/data/*` | ArtifactAdmin | `/artifact/*` |
| L9 | YAML 技能 | `skill/SkillRegistry.java`, `skill/SkillExecutor.java` | — | `skills/*.yaml` |
| L10 | 质量守护 | `quality/QualityGuardAgent.java` | CareerAdvisor (SSE) | SSE `quality-*` |
| L11 | 收藏 | `favorite/FavoriteRepository.java`, `service/FavoriteAppService.java` | Favorites | `/favorite/*` |
| L12 | 用量追踪 | `usage/UsageTracker.java`, `usage/AgentEfficiencyTracker.java` | UsageDashboard | `/usage/stats` |
| L13 | 导入导出 | `export/DataExportService.java`, `export/DataImportService.java` | API 已定义 | `/export/*` |
| L14 | 对话搜索 | `search/ChatSearchService.java` | CareerAdvisor 搜索 | `/session/search` |
| L15 | 持久化消息 | `message/PersistentMessageRepository.java` | — | `/session/{chatId}/messages` |

---

## L16-L27 · 平台能力

| 层 | 功能 | 后端关键文件 | 配置 |
|----|------|-------------|------|
| L16 | NLU 意图理解 | `nlu/NluPipeline.java`, `nlu/ClarificationHandler.java` | — |
| L17 | 多 Agent 运行时 | `agent/OrchestratorAgent.java`, `workflow/WorkflowMatcher.java` | — |
| L18 | 工作流引擎 | `workflow/runtime/WorkflowRuntime.java`, `workflow/node/*` | — |
| L19 | 沙箱执行 | `sandbox/SandboxFactory.java`, `sandbox/DockerSandbox.java` | `application.yml` sandbox |
| L20 | 访问控制 | `access/AccessDecisionService.java`, `permission/AgentPermissionService.java` | `permissions/*.yaml` |
| L21 | Agent 注册中心 | `registry/InMemoryAgentRegistry.java` | `agents/*.yaml` |
| L22 | 评测中心 | `eval/EvalCenter.java` | `eval/*.yaml` |
| L23 | Prompt 版本管理 | `prompt/PromptRegistry.java` | — |
| L24 | 交付物生命周期 | `artifact/ArtifactShelf.java` | — |
| L25 | 事件总线 | `event/EventBusAdapter.java`, `event/GovernanceEventListener.java` | — |
| L26 | 安全防护 | `guard/PromptInjectionDetector.java`, `budget/TokenBudgetManager.java` | — |
| L27 | 四层记忆 | `memory/MemoryCoordinator.java`, `memory/sliding|fact|summary|experience/` | `application.yml` memory |

---

## L28-L33 · Hello-Agents 优化

| 层 | 功能 | 后端关键文件 | 端点 |
|----|------|-------------|------|
| L28 | 性能监控 | `metrics/AgentMetrics.java`, `metrics/AgentCircuitBreaker.java` | `/actuator/agent-*` |
| L29 | 经典范式 | `agent/paradigm/PlanAndSolveAgent.java`, `ReflectionAgent.java` | — |
| L30 | 上下文工程 | `memory/context/ContextEngineer.java`, `DynamicBudgetAllocator.java` | — |
| L31 | 工具注册 | `tools/registry/ToolRegistryService.java`, `ToolDiscovery.java` | — |
| L32 | Reflexion 失败记忆 | `agent/reflexion/ReflexionMemory.java`, `ReflexionService.java` | — |
| L33 | RAG Rerank | `rag/rerank/RerankService.java` | — |

---

## 横切关注点

| 关注点 | 文件 |
|--------|------|
| JWT 鉴权 | `auth/JwtUtil.java`, `auth/AuthService.java` |
| 会话三态 | `session/SessionManager.java` (ACTIVE/ARCHIVED/TRASH) |
| AppService 编排 | `service/*AppService.java` |
| 全局异常 | `exception/GlobalExceptionHandler.java` |
| 用户画像 | `profile/UserProfileService.java` |
| 执行轨迹 | `trace/TraceRecorder.java` → TraceDetail 页 |
| CORS | `config/CorsConfig.java` |

---

## 按「我要改 X」快速定位

| 需求 | 先看 | 再看 |
|------|------|------|
| 改聊天回复质量 | `quality/QualityGuardAgent.java` | `agent/OrchestratorAgent.java` |
| 改意图识别 | `nlu/NluPipeline.java` | `docs/nlu-layer-design-v4.2.md` |
| 改记忆行为 | `memory/MemoryCoordinator.java` | `memory/context/ContextEngineer.java` |
| 加新工具 | `tools/XxxTool.java` | `tools/ToolRegistration.java` |
| 改 RAG 召回 | `rag/QueryRewriter.java` | `rag/rerank/RerankService.java` |
| 改工作流 | `workflow/runtime/WorkflowRuntime.java` | `workflow/node/*` |
| 改权限控制 | `access/AccessDecisionService.java` | `permissions/*.yaml` |
| 改前端聊天 UI | `views/CareerAdvisor.vue` | `components/TraceTimelineView.vue` |
| 改监控指标 | `metrics/AgentMetrics.java` | `metrics/AgentDiagnosticsEndpoint.java` |
| 加评测用例 | `eval/EvalCenter.java` | `eval/*.yaml` |
