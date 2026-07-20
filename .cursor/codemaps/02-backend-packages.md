# 后端包目录 · Java Packages

> 根包：`src/main/java/com/yupi/yuaiagent/`
> 入口类：`AiAgentApplication.java`

---

## 核心业务包

| 包 | 职责 | 关键文件 |
|----|------|----------|
| `agent/` | Agent 运行时、路由、子 Agent | `OrchestratorAgent.java`, `BaseAgent.java`, `ReActAgent.java`, `ToolCallAgent.java`, `YuManus.java` |
| `agent/paradigm/` | 经典推理范式 | `PlanAndSolveAgent.java`, `ReflectionAgent.java`, `ParadigmService.java`, `ParadigmAgentFactory.java` |
| `agent/reflexion/` | 失败记忆 Reflexion | `ReflexionMemory.java`, `ReflexionService.java` |
| `agent/runner/` | V2 AgentRunner 适配层 | `ResumeAgentRunner.java`, `NegotiationAgentRunner.java`, … |
| `agent/data/` | 数据员工 Agent | `DataEmployeeAgent.java`, `DataAnalystAgent.java`, `CareerCoachAgent.java`, … |
| `agent/output/` | Agent 结构化输出 | `AgentOutput.java`, `TextOutput.java` |
| `app/` | 遗留/demo 对话层 | `AiChatAgent.java` |
| `service/` | AppService 业务编排 | `OrchestratorAppService.java`, `SessionAppService.java`, `DocumentAppService.java`, `FavoriteAppService.java`, `ExportAppService.java` |
| `controller/` | REST HTTP 适配 | 11 个 Controller（见 04-backend-api.md） |

---

## AI / Agent 基础设施

| 包 | 职责 | 关键文件 |
|----|------|----------|
| `nlu/` | NLU 意图理解管道 | `NluPipeline.java`, `ClarificationHandler.java`, `IntentAmbiguityDetector.java`, `RuleContextShiftDetector.java`, `ConversationState.java` |
| `memory/` | 四层记忆协调器 | `MemoryCoordinator.java`, `ContextWindow.java` |
| `memory/sliding/` | L1 滑动窗口 | `SlidingWindowLayer.java` |
| `memory/fact/` | L2 事实存储 | `FactStoreLayer.java` |
| `memory/summary/` | L3 对话摘要 | `SummaryLayer.java`, `SummaryChecklist.java` |
| `memory/experience/` | L4 向量经验 | `ExperienceStoreLayer.java`, `ExperienceDocument.java` |
| `memory/context/` | 上下文工程 | `ContextEngineer.java`, `DynamicBudgetAllocator.java`, `ContextRelevanceScorer.java` |
| `memory/extraction/` | 记忆异步提取 | 提取管道相关类 |
| `rag/` | RAG 检索增强 | `QueryRewriter.java`, `RagTool.java`, `PgVectorVectorStoreConfig.java`, `AiChatContextualQueryAugmenterFactory.java` |
| `rag/rerank/` | RAG 重排序 | `RerankService.java` |
| `tools/` | 内置工具 | `WebSearchTool.java`, `WebScrapingTool.java`, `FileOperationTool.java`, `ResourceDownloadTool.java`, `TerminalOperationTool.java`, `PDFGenerationTool.java`, `TerminateTool.java`, `ToolRegistration.java` |
| `tools/registry/` | 动态工具注册 | `ToolRegistry.java`, `ToolDiscovery.java`, `ToolRegistryService.java` |
| `skill/` | YAML 技能系统 | `SkillRegistry.java`, `SkillExecutor.java`, `SkillDefinition.java` |
| `prompt/` | Prompt 版本管理 | `PromptRegistry.java` |
| `registry/` | Agent 注册中心 | `InMemoryAgentRegistry.java`, `AgentDescriptor.java` |

---

## 治理 / 安全 / 质量

| 包 | 职责 | 关键文件 |
|----|------|----------|
| `access/` | 投票式访问控制 | `AccessDecisionService.java`, `AgentPolicyVoter.java` |
| `permission/` | Agent 权限画像 | `AgentPermissionService.java`, `PermissionProfileRegistry.java` |
| `mcp/` | MCP 信任与审计 | `McpTrustService.java`, `McpAuditLog.java` |
| `quality/` | 质量守护 | `QualityGuardAgent.java`, `QualityReview.java` |
| `guard/` | 安全防护 | `PromptInjectionDetector.java`, `EmbeddingLoopDetector.java` |
| `budget/` | Token 预算 | `TokenBudgetManager.java`, `TokenUsageTracker.java` |
| `sandbox/` | 沙箱执行 | `SandboxFactory.java`, `DockerSandbox.java`, `ProcessSandbox.java`, `ToolSandbox.java` |
| `eval/` | 评测中心 | `EvalCenter.java`, `EvalCase.java` |
| `event/` | 治理事件总线 | `EventBusAdapter.java`, `GovernanceEventListener.java`, `GovernanceEvent.java` |

---

## 工作流 / 轨迹 / 交付物

| 包 | 职责 | 关键文件 |
|----|------|----------|
| `workflow/` | 工作流匹配与注册 | `WorkflowMatcher.java`, `WorkflowRegistry.java`, `WorkflowMatchResult.java` |
| `workflow/node/` | 6 种节点类型 | `AgentNode.java`, `ToolNode.java`, `ConditionNode.java`, `ParallelNode.java`, `LoopNode.java`, `ApprovalNode.java` |
| `workflow/runtime/` | 工作流运行时 | `WorkflowRuntime.java`, `WorkflowStatus.java` |
| `trace/` | 执行轨迹 | `TraceRecorder.java`, `TraceRepository.java`, `TraceContext.java` |
| `trace/model/` | 轨迹模型 | `TraceSpan.java`, `TraceStepType.java`, `TraceStatus.java` |
| `artifact/` | 交付物生命周期 | `ArtifactShelf.java`, `ArtifactRepository.java`, `artifact/model/ArtifactSummary.java` |

---

## 用户 / 会话 / 数据

| 包 | 职责 | 关键文件 |
|----|------|----------|
| `auth/` | JWT 鉴权 | `AuthService.java`, `JwtUtil.java` |
| `session/` | 会话生命周期 | `SessionManager.java`, `SessionStatus.java` |
| `message/` | 持久化消息 | `PersistentMessageRepository.java`, `ChatMemoryAdapter.java`, `MessageSource.java` |
| `chatmemory/` | 对话记忆压缩 | `ChatMemoryManager.java`, `CompressionStrategy.java`, `TokenCompressionStrategy.java` |
| `profile/` | 用户画像 | `UserProfileService.java`, `UserProfileRepository.java`, `ProfilePromptBuilder.java` |
| `favorite/` | 收藏 | `FavoriteRepository.java`, `Favorite.java` |
| `feedback/` | 反馈 | `FeedbackRepository.java`, `Feedback.java` |
| `usage/` | 用量追踪 | `UsageTracker.java`, `AgentEfficiencyTracker.java`, `UsageEvent.java` |
| `search/` | 会话搜索 | `ChatSearchService.java` |
| `export/` | 导入导出 | `DataExportService.java`, `DataImportService.java` |
| `document/` | 知识库元数据 | `DocumentMetadataManager.java`, `DocumentMeta.java` |
| `calendar/` | 日历集成 | `CalendarServiceFactory.java`, `FeishuCalendarService.java`, `DingTalkCalendarService.java` |
| `validation/` | 输入校验 | `InfoValidator.java` |

---

## 持久化 / 通用

| 包 | 职责 | 关键文件 |
|----|------|----------|
| `repository/` | JPA 仓储 | `AppointmentRepository.java`, `repository/entity/*.java`, `repository/jpa/*.java` |
| `dto/` | 请求/响应 DTO | `AddFavoriteRequest.java`, `SessionSearchResponse.java`, … |
| `common/` | 通用响应 | `Response.java`, `ResultCode.java` |
| `exception/` | 异常处理 | `BusinessException.java`, `GlobalExceptionHandler.java` |
| `context/` | 运行时上下文 | `RuntimeContext.java`, `ConversationContextBuilder.java` |
| `config/` | Spring 配置 | `AgentConfig.java`, `CorsConfig.java`, `CalendarConfig.java`, `CompressionConfig.java`, `ExecutorConfig.java`, `FollowUpTemplateConfig.java` |
| `advisor/` | Spring AI Advisor | `MyLoggerAdvisor.java`, `ReReadingAdvisor.java` |
| `constant/` | 常量 | `FileConstant.java` |
| `metrics/` | 监控指标 | `AgentMetrics.java`, `AgentCircuitBreaker.java`, `AgentDiagnosticsEndpoint.java`, `AgentMetricsEndpoint.java`, `AgentExecutionMetrics.java`, `AgentHealthIndicator.java` |
| `demo/` | 示例代码 | `demo/invoke/`, `demo/rag/` |

---

## 包依赖原则

```
controller → service (AppService) → agent / domain services
controller ✗→ agent（禁止跨层直接调用）
agent → nlu / memory / trace / tools / skill / workflow
AppService → auth / session / usage（横切编排）
```
