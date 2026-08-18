# WorkPilot 插件化平台改造计划

> 版本：v1  
> 原则：Registry-first、先观测后切换、权限只能收窄、旧路径至少保留一个发布周期。

## 1. 目标

1. 将 Agent、Skill、ExpertPack、Permission 的 YAML 装载收敛到统一契约。
2. 以 `agents/*.yaml` 作为 Agent 元数据事实源，消除重复关键词和能力定义。
3. 打通 `AgentDescriptor` 与 `AgentRunner`，使新增 Agent 不再修改多处 switch。
4. 让 ExpertPack 启停一致作用于 Skill、Agent 和 Permission，并且无法扩大基础权限。
5. 增加 Tool Transformer、Prompt Contributor 和观测 SPI，但不引入动态 Jar 加载。

## 2. 非目标

- 不移植 Datus 的 CLI 子进程、Bash 扫描器和环境变量上下文桥。
- 不使用 `URLClassLoader` 动态加载第三方 Jar。
- 不引入可执行模板沙箱。
- 不重写 NLU、Memory、RAG 和 Consultation 状态机。
- 本轮不做 Workflow Node 插件化。
- 不在同一个 PR 中删除旧实现并切换新实现。

## 3. 必须保持的不变量

- HTTP/SSE 事件名称、字段与顺序保持兼容。
- 现有七类 `AgentIntent` 路由结果不变。
- 权限保持 fail-closed；插件或 Pack 只能收窄权限。
- `doTerminate`、`checkAsyncToolTask` 控制工具始终可用。
- Consultation 的会话锁定和多轮状态机不变。
- DATA_QUERY、DIGITAL_EMPLOYEE 的附加上下文不能丢失。
- 默认 Feature Flag 全部保持旧行为。
- 每个 PR 必须通过 `.\mvnw.cmd -q test`。

## 4. 已确认的现状与风险

1. `agents/*.yaml` 只有 4 份，而 `AgentIntent` 和 `AgentManifestRegistry` 定义了 7 类 Agent。
2. `ExpertPackDefinition.permissionProfiles` 已被读取和校验，但没有进入运行时权限决策。
3. `ExpertPackAppService` 存在“全部关闭后恢复默认 Pack”的回退语义，不能直接复用于权限收窄。
4. 工具执行存在两条链：
   - `ToolCallAgent` 已在请求期过滤，并在执行期再次 fail-closed 校验。
   - `NegotiationAgent`、`EscapeAgent` 使用构造期固化后的工具数组，需要单独改造。
5. `AgentRunner` 当前是同步接口，不能直接替换 Consultation 与主 SSE 路径。
6. `ContextInjectionService` 会创建状态、写 Trace、召回 Artifact；请求内双跑会产生重复副作用。

## 5. Feature Flags

统一放入 `application.yml` 的 `platform` 配置段，默认值均保持旧逻辑：

```yaml
platform:
  manifest:
    loader: legacy          # legacy | dual | unified
  agent:
    metadata-source: legacy # legacy | shadow | registry
    runner:
      enabled: false
      route: off            # off | shadow | primary
  permission:
    namespace-mode: off     # off | observe | enforce
    pack-narrowing: false
  runtime-tools:
    request-filter: false
  tool-transformer:
    enabled: false
  prompt-contributors:
    mode: legacy            # legacy | offline-shadow | primary
  activation-cache:
    enabled: false
```

## 6. 依赖顺序

```text
S0 安全基线
 ├─ S1 ManifestLoader
 │   ├─ S2 Agent 元数据单源
 │   │   └─ S3 AgentRunner 完整化
 │   │       └─ S4 Runner 影子接入
 │   └─ S5a Pack 偏好持久化
 │       └─ S5b 权限三态收窄
 │           └─ S6 请求期工具过滤
 │               └─ S9 安全缓存
 ├─ S7 Tool Transformer
 └─ S8 Prompt Contributor

S10 P2 能力在 S4、S6 稳定后执行
```

可并行项：

- S2 与 S5a 可并行。
- S3/S4 与 S5b/S6 可并行。
- S7 与 S8 可并行，但均在 S0 后开始。

---

## S0：建立安全基线与差异检测

**性质**：只加检测，不切流量。

### 目的

- 在重构前固定当前路由、权限和 SSE 行为。
- 建立工具命名空间的显式映射，识别无效权限模式。

### 涉及位置

- `permission/ToolNameMatcher.java`
- `tools/registry/ToolRegistryService.java`
- `tools/registry/ToolDefinition.java`
- `permission/PermissionProfileRegistry.java`
- `registry/InMemoryAgentRegistry.java`
- `agent/OrchestratorAgent.java`

### 修改

1. 新增 `ToolNamespaceRegistry`，从 `ToolDefinition` 显式读取 namespace/capability。
2. 保留 `ToolDiscovery.inferCapabilities()`，但输出 deprecation 告警，不再作为新增工具的推荐方式。
3. 新增启动期校验：
   - 每个 permission pattern 至少匹配一个已注册工具；
   - 每个 AgentDescriptor 必须能解析到 permission profile；
   - Agent、Permission、Intent 数量差异写入诊断报告。
4. 为现有有效工具集、路由结果和 SSE 事件生成测试快照。

### 测试

- 新增 `PermissionPatternCoverageTest`
- 新增 `AgentPermissionCoverageTest`
- 新增 `EffectiveToolSetSnapshotTest`
- 新增 Consultation 多轮锁定回归测试

```powershell
.\mvnw.cmd -q test
.\mvnw.cmd -q -Dtest='AgentRoutingEvalTest+FastPathRoutingTest+EvalCenterRoutingGateTest' test
.\mvnw.cmd -q -Dtest='*Permission*Test+*Tool*Test' test
```

### 验收

- 能明确报告当前 `calendar.*` 等死模式。
- 快照测试不改变现有行为。
- 全量测试通过。

### 回滚

删除诊断 Bean 或关闭诊断配置，不涉及运行时路径。

---

## S1：统一 Manifest/YAML Loader

**性质**：双读；旧 Loader 仍为主路径。

### 涉及位置

- `skill/SkillRegistry.java`
- `registry/InMemoryAgentRegistry.java`
- `pack/ExpertPackRegistry.java`
- `permission/PermissionProfileRegistry.java`
- 新增 `manifest/ManifestLoader.java`
- 新增 `manifest/ManifestLoadReport.java`
- 新增 `manifest/ManifestLoaderProperties.java`

### 修改

1. 抽取统一资源扫描、UTF-8 YAML 解析、重复键检测、来源记录和 SHA-256 指纹。
2. 严格度按类型区分：
   - Permission：解析失败或未知字段时 fail-fast。
   - Agent、Pack、Skill：单文件失败可隔离，但必须进入 `ManifestLoadReport`。
3. `dual` 模式同时运行新旧 Loader，只比较解析后的对象和 key 集合。
4. 四个旧加载方法保留并标记 `@Deprecated`。
5. 验证 Lombok/Jackson 默认值，避免缺省字段落为 `false/0`。

### 测试

- `ManifestLoaderTest`
- `ManifestDualReadParityTest`
- `ManifestLoaderStrictModeTest`
- `ManifestDefaultValueTest`

### 验收

- 新旧加载结果无差异。
- 坏 Permission 阻断启动；坏 Skill 不拖垮其他 Skill。
- 所有错误包含资源路径、类型和安全重试建议。

### 回滚

`platform.manifest.loader=legacy`。

---

## S2：Agent 元数据单一事实源

**性质**：影子比对；硬编码仍生效。

### 涉及位置

- `registry/AgentDescriptor.java`
- `agent/manifest/AgentManifestRegistry.java`
- `agent/manifest/AgentManifest.java`
- `resources/agents/*.yaml`
- `agent/AgentIntent.java`

### 修改

1. 为 CONSULTATION、DATA_QUERY、DIGITAL_EMPLOYEE 补齐 Agent YAML。
2. `AgentDescriptor` 增加 `intent`、`routingKeywords`、`inputRequirements`。
3. 新增 `AgentManifestFactory`，从 `AgentDescriptor` 派生路由 Manifest。
4. `AgentManifestRegistry` 三态：
   - `legacy`：使用硬编码。
   - `shadow`：使用硬编码结果，同时比较 YAML 派生结果。
   - `registry`：使用 YAML 派生结果。
5. `feedbackBoost` 保持运行时状态，不写入 YAML；改为并发安全结构，reload 时不丢失。
6. 影子比较时将 boost 归一为 1.0，只比较静态路由信息。

### 测试

- `AgentManifestParityTest`
- `AgentDescriptorCoverageTest`
- `AgentManifestRegistryConcurrencyTest`
- 现有路由评测套件

### 验收

- 七类 intent 的静态字段完全一致。
- Shadow 日志无预期外差异。
- `metadata-source=registry` 下路由评测不回归。

### 回滚

`platform.agent.metadata-source=legacy`。

---

## S3：完整化 AgentRunner，但不接管主路径

**性质**：骨架完善；仅服务 DAG/TaskExecutor。

### 涉及位置

- `agent/AgentRunner.java`
- 现有 `*AgentRunner.java`
- `agent/TaskExecutor.java`
- `workflow/dag/DagWorkflowExecutor.java`
- `agent/OrchestratorAgent.java`

### 修改

1. 补齐 CONSULTATION、DATA_QUERY、DIGITAL_EMPLOYEE 的 Runner 描述，但 Consultation 暂不进入通用执行。
2. 为 Runner 增加：
   - `agentCode()`
   - `supportsStreaming()`
   - `runStream(...)`
   - `holdsSession(chatId)`
   - 真实 `TokenUsage`
3. 新增 `AgentRunnerRegistry`，通过 Spring Bean 列表构造，重复 agentCode 启动失败。
4. `TaskExecutor` 与 `DagWorkflowExecutor` 改为构造注入 Registry，禁止初始化前静默使用空 Map。
5. 主 `OrchestratorAgent.streamSingleExpert()` 的 switch 保持不变。

### 测试

- `AgentRunnerRegistryTest`
- `AgentRunnerCoverageTest`
- `RunnerTokenUsageTest`
- `DagWorkflowExecutorTest`

### 验收

- 七类 Agent 均有明确 Runner 策略或显式“不支持通用 Runner”声明。
- Token 预算门能够被真实 usage 触发。
- DAG 与 TaskExecutor 行为不变。

### 回滚

`platform.agent.runner.enabled=false`，回到现有 Map。

---

## S4：Runner 影子接入与有限主路径切换

**性质**：先影子；禁止一次性替换全部 switch。

### 修改

1. Shadow 模式只比较“路由选中的 Runner 身份”，不重复调用 LLM。
2. DATA_QUERY、DIGITAL_EMPLOYEE 的附加 note 必须进入 Runner 上下文并做字符串级回归测试。
3. Consultation 在满足以下条件前保持旧路径：
   - 多轮锁定测试通过；
   - Runner 能表达 `holdsSession`；
   - 状态迁移与旧路径逐状态一致。
4. 主路径切换顺序：
   - RESUME
   - NEGOTIATION
   - ESCAPE
   - GENERAL
   - DATA_QUERY / DIGITAL_EMPLOYEE
   - CONSULTATION 最后单独 PR
5. 每个 intent 可独立配置 `off/shadow/primary`。

### 测试

- `OrchestratorDispatchTest`
- `OrchestratorStreamParityTest`
- `ConsultationSessionLockTest`
- `DataQueryInjectionParityTest`
- `DigitalEmployeeInjectionParityTest`

### Go/No-Go

- Shadow 运行至少 24 小时，无 `DispatchDrift`。
- SSE 事件快照不变。
- 任一 intent 出现路由或状态差异即 No-Go。

### 回滚

将对应 intent 改回 `off`；旧 switch 不删除。

---

## S5a：ExpertPack 偏好持久化

**性质**：权限改造前置条件。

### 目的

解决 `./tmp/expert-packs/user-prefs.json` 在多实例下不一致、并发覆写丢更新的问题。

### 涉及位置

- `service/ExpertPackAppService.java`
- 新增 `ExpertPackPreferenceRepository`
- 新增 file/jdbc 两种实现
- 数据库 migration

### 修改

1. 抽 Repository，保留 file 模式兼容。
2. JDBC 模式使用版本字段或事务锁防并发丢更新。
3. 明确定义三种偏好状态：
   - 从未设置；
   - 显式启用部分 Pack；
   - 显式关闭全部 Pack。
4. 不再用“结果为空”推断“从未设置”。

### 测试

- `ExpertPackPreferenceRepositoryContractTest`
- `ExpertPackPreferenceConcurrencyTest`
- `ExpertPackPreferenceMigrationTest`

### 验收

- 多实例读取一致。
- 显式全关不会自动恢复默认 Pack。

### 回滚

保留 file adapter，并通过 storage flag 切回。

---

## S5b：权限命名空间与 Pack 三态收窄

**性质**：observe 后 enforce。

### 涉及位置

- `permission/ToolNameMatcher.java`
- `permission/PermissionProfileRegistry.java`
- `permission/AgentPermissionService.java`
- `service/ExpertPackAppService.java`
- 新增 `PermissionNarrowingService`

### 权限语义

```text
无用户偏好：
  effective = base profile

显式启用若干 Pack：
  effective = base ∩ union(enabled pack profiles)

显式关闭全部 Pack：
  effective = always-allowed control tools only

系统或用户 deny：
  始终优先，Pack 无法覆盖
```

### 修改

1. 权限模式改用显式 namespace 映射，不再按工具名猜 capability。
2. 非 admin profile 禁止裸 `*`。
3. 每个 Pack permission pattern 必须至少匹配一个已注册工具。
4. `observe` 模式双算有效工具集，但仍使用旧结果。
5. 记录差异原因：新增、移除、死模式、未知工具。

### 测试

- `PermissionNarrowingServiceTest`
- `PackAllDisabledPermissionTest`
- `WildcardProfileGuardTest`
- `PermissionPatternCoverageTest`
- 有效工具集性质测试：`effective ⊆ base`

### Go/No-Go

- Observe 模式不存在意外新增工具。
- 全关场景只剩控制工具。
- 有效工具集快照变化必须人工确认。

### 回滚

`namespace-mode=off`、`pack-narrowing=false`。

---

## S6：仅修复需要请求期过滤的工具链

**性质**：有限切流；暂不建立大型 ActivationService。

### 涉及位置

- `agent/OrchestratorAgent.java`
- `agent/NegotiationAgent.java`
- `agent/EscapeAgent.java`
- `access/PermittedToolFilter.java`
- `agent/ToolCallAgent.java` 只增加回归测试，不改现有双重校验

### 修改

1. Orchestrator 构造期向 Agent 传原始工具集合。
2. Negotiation/Escape 在每次 `chat/chatStream` 前根据 `(userId, agentCode, packState)` 计算请求工具视图。
3. 工具真正执行前再次做权限判断，避免只依赖 LLM 可见工具列表。
4. `ToolCallAgent` 现有 `toolsForLlm()` 和执行期 Predicate 保持不变。
5. 确认 consultation、MCP、异步工具是否绕过新的请求期过滤，并建立覆盖矩阵。

### 测试

- `RuntimeToolFilterTest`
- `NegotiationToolAuthorizationTest`
- `EscapeToolAuthorizationTest`
- `ToolExecutionFailClosedTest`
- `McpToolAuthorizationCoverageTest`

### 验收

- 修改 Pack 后下一请求立即生效，无需重启。
- 被拒工具既不暴露给 LLM，也无法在执行期绕过。
- `ToolCallAgent` 行为无回归。

### 回滚

`platform.runtime-tools.request-filter=false`。

---

## S7：Tool Transformer 中间件

**性质**：先内置策略；暂不开放第三方代码加载。

### 涉及位置

- `tools/ToolRegistration.java`
- `tools/registry/ToolRegistry.java`
- 新增 `tools/transform/ToolTransformer.java`
- 新增 `tools/transform/TransformingToolCallback.java`
- 新增 `tools/transform/ToolTransformerChain.java`

### 修改

1. 支持 `PROCEED`、`REWRITE`、`REJECT`。
2. Transformer 抛异常时 fail-closed。
3. 同时覆写 Spring AI `ToolCallback` 的两个 `call` 重载。
4. 完整透传 `getToolDefinition()` 与 `getToolMetadata()`。
5. 明确覆盖矩阵：
   - `allTools` Bean；
   - Spring AI 内置工具执行；
   - `ParallelToolCallingSupport`；
   - MCP ToolCallbackProvider；
   - 异步任务工具。
6. 第一批只实现路径约束和 URL 安全策略。

### 测试

- `ToolTransformerChainTest`
- `ToolTransformerFailClosedTest`
- `ToolCallbackOverloadTest`
- `ToolMetadataDelegationTest`
- `McpTransformerCoverageTest`

### 验收

- 单参、双参调用均不能绕过 Transformer。
- Transformer 拒绝后原工具零调用。
- `returnDirect` 等 metadata 保持不变。

### 回滚

`platform.tool-transformer.enabled=false`。

---

## S8：PromptSectionContributor

**性质**：先纯函数化，再离线影子，最后切换。

### 涉及位置

- `agent/ContextInjectionService.java`
- 新增 `agent/prompt/PromptSectionContributor.java`
- 新增 `agent/prompt/PromptContext.java`
- 新增各 section contributor

### 修改

1. 先将构造依赖收敛为参数对象，停止增加伸缩构造函数。
2. 将“读取数据”和“渲染 section”分离：
   - 数据准备只执行一次；
   - 渲染为无副作用纯函数。
3. 禁止请求内双跑完整 `buildCombinedInjectionResult()`。
4. Shadow 只能采用：
   - 离线回放；
   - 或复用同一份预加载数据、NoOp TraceRecorder 的纯渲染比较。
5. contributor 失败时只跳过该 section；权限 section 不允许在此链中降级。

### 测试

- `PromptSectionParityTest`
- `PromptContributorOrderTest`
- `PromptContributorFailureIsolationTest`
- `PromptShadowSideEffectTest`

### 验收

- Shadow 前后 Trace span 数量一致。
- `recordOffered` 调用次数不增加。
- Artifact recall、embedding 不重复执行。
- 最终 Prompt 严格或归一化后等价。

### 回滚

`platform.prompt-contributors.mode=legacy`。

---

## S9：激活状态的安全缓存

**性质**：性能优化；最后实施。

### 修改

1. 只缓存静态部分：`base profile ∩ pack profile`。
2. 不缓存最终 AccessDecision。
3. 以下内容每次请求重新计算：
   - `HandoffScopeContext`
   - tool call quota
   - session scope
   - HITL 状态
4. 缓存 key 包含 manifest fingerprint、user preference version 和 agentCode。
5. 订阅 Permission/Pack 变更事件主动驱逐；不能只依赖 TTL。

### 测试

- `ActivationFingerprintTest`
- `PermissionCacheRevocationTest`
- `HandoffScopeCacheIsolationTest`
- `QuotaCacheIsolationTest`

### Go/No-Go

- 撤权后下一请求立即生效。
- Handoff scope 改变不会读取旧权限。
- 任何 stale allow 即 No-Go。

### 回滚

`platform.activation-cache.enabled=false`，回到实时计算。

---

## S10：P2 独立能力

以下每项单独 PR：

1. **用户 Skill 持久化与多层覆盖**
   - 覆盖顺序：SESSION > USER > EXTERNAL > BUILTIN。
   - 用户 Prompt 必须过 Prompt Injection 检测。
2. **ObservabilityExporter SPI**
   - `setup / record / flush / shutdown`。
   - 单 exporter 失败不能阻断主 Agent。
3. **统一 ActionHistory 事件协议**
   - 统一 SSE、Trace、Tool、Workflow、Subagent 的事件 envelope。
   - 先适配器双写，后续版本再删除旧事件模型。

Workflow Node 插件化只有出现实际新增节点需求时再立项。

## 7. PR 划分

| PR | 内容 | 是否切流量 |
|---|---|---|
| PR-0 | 安全基线与差异检测 | 否 |
| PR-1 | ManifestLoader 双读 | 否 |
| PR-2 | Agent 元数据单源 | 影子 |
| PR-3 | AgentRunner 完整化 | 否 |
| PR-4a~4n | 按 Intent 切 Runner | 是，逐个 |
| PR-5a | ExpertPack 偏好持久化 | 存储切换 |
| PR-5b | 权限三态收窄 | Observe → Enforce |
| PR-6 | 请求期工具过滤 | 是 |
| PR-7 | Tool Transformer | 是 |
| PR-8 | Prompt Contributor | 离线影子 → 是 |
| PR-9 | 权限静态结果缓存 | 是 |
| PR-10a~c | P2 独立能力 | 分别评估 |

## 8. 整体完成标准

- 新增普通 Agent 只需一份 YAML 和一个 Runner Bean。
- `OrchestratorAgent` 不再新增 intent switch 分支。
- 四套 YAML 扫描逻辑收敛为一个 Loader。
- ExpertPack 启停对 Skill、Agent、Permission 在下一请求一致生效。
- 非 admin permission profile 不存在裸 `*`。
- Tool Transformer 无法通过任一 ToolCallback 调用路径绕过。
- 旧路径在新路径稳定运行至少一个发布周期后，另立清理 PR 删除。

