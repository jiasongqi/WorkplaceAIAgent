# Implementation Plan: Agent 执行轨迹可视化（Execution Trace / Timeline）

## Overview

本实施计划基于 `requirements.md` 与 `design.md`，将「Agent 执行轨迹可视化」能力拆解为可逐个执行的编码任务。任务严格按需求文档的 **P1 / P2 / P3** 优先级分期组织，P1 在前（数据模型 → 上下文 → 记录器 → 持久化 → 查询接口 → 编排集成的完整闭环）。

技术栈：**Java 21 + Spring Boot 3.4 + Spring AI 1.0**（设计全程使用 Java，无伪代码，无需选择实现语言）。持久化组件严格复用现有 `AppointmentRepository` / `ArtifactRepository` 范式（`ObjectMapper` + `JavaTimeModule` + `ConcurrentHashMap` + `ReadWriteLock` + `@PostConstruct` 加载 + `@Value` 配置目录 + `writerWithDefaultPrettyPrinter` 写盘）。

每个任务遵循增量推进顺序，后续任务在前序任务之上构建，最终在集成阶段把 `TraceRecorder` 接入 `OrchestratorAgent` / `AiController` / `ToolCallAgent` / `ChatMemoryManager`，不留孤立代码。

设计包含「Correctness Properties」章节（16 条属性，使用 **jqwik** 做属性测试），因此本计划为每条属性安排了独立的属性测试子任务（标注 `*` 为可选），与单元测试、集成测试互补。`net.jqwik:jqwik`（test scope）尚未在 `pom.xml` 中，需在 P1 起步任务中引入。

---

## Tasks

### P1 阶段 — 核心闭环（数据模型 → 持久化 → 采集 → 查询接口 → 编排集成）

- [x] 1. 搭建轨迹基础设施骨架、常量与配置 [P1]
  - [x] 1.1 建包结构、引入 jqwik、新增配置与常量
    - 创建包 `com.yupi.yuaiagent.trace` 与 `com.yupi.yuaiagent.trace.model`
    - 在 `com.yupi.yuaiagent.trace.model` 下创建 `TraceConstants`（`ABSOLUTE_MAX_SPANS=1000`、`MAX_METADATA_ENTRIES=50`、`MAX_METADATA_KEY_CHARS=128`、`MAX_ERROR_CHARS=2048`，私有构造）
    - 在 `application.yml` 新增 `trace.*` 配置块（`trace.storage.dir` 默认 `./tmp/traces`、`trace.stream.enabled` 默认 `true`、`trace.max-spans-per-trace` 默认 `200`、`trace.metadata.max-value-chars` 默认 `2000`、`trace.max-traces-per-user` 默认 `500`）
    - `pom.xml` 中 `net.jqwik:jqwik`（test scope）已存在，无需重复添加
    - _Requirements: 1.1, 1.6, 3.9, 5.3, 9.5, 11.1, 11.3, 11.5_

  - [x] 1.2 实现 TraceProperties 配置类与取值钳制
    - 在 `com.yupi.yuaiagent.trace` 下创建 `@Component @ConfigurationProperties(prefix="trace")` 的 `TraceProperties`（`streamEnabled`、`maxSpansPerTrace`、`metadataMaxValueChars`、`maxTracesPerUser`）
    - 在 `@PostConstruct clampToValidRanges()` 中将 `maxSpansPerTrace` 钳制到 [1,1000]、`metadataMaxValueChars` 钳制到 [1,4096]、`maxTracesPerUser` 钳制到 [1,100000]
    - _Requirements: 9.5, 11.1, 11.3, 11.5_

  - [x]* 1.3 编写配置取值范围钳制属性测试
    - 实现于 `test/trace/TracePropertiesClampPropertyTest.java`，5 个属性测试，每个 200 次迭代
    - **Property 12: 配置取值范围钳制**
    - **Validates: Requirements 11.1, 11.3, 11.5**

  - [x]* 1.4 编写 TraceProperties 默认值单元测试
    - 实现于 `test/trace/TracePropertiesTest.java`，覆盖默认值、边界值、幂等性、streamEnabled 开关
    - _Requirements: 3.9, 9.5, 11.1, 11.3, 11.5_

- [ ] 2. 实现轨迹数据模型与枚举 [P1]
  - [ ] 2.1 实现三个状态/类型枚举
    - 在 `com.yupi.yuaiagent.trace.model` 下创建 `TraceStatus`（RUNNING / COMPLETED / FAILED）、`TraceStepStatus`（RUNNING / SUCCESS / ERROR / SKIPPED）、`TraceStepType`（恰好 10 个取值，每个带唯一非空中文 `displayName`，1–50 字符）
    - _Requirements: 1.3, 1.4, 1.5, 2.1, 2.2, 2.3_

  - [ ]* 2.2 编写步骤类型显示名完整且唯一属性测试
    - **Property 16: 步骤类型显示名完整且唯一**
    - **Validates: Requirements 2.2**

  - [ ]* 2.3 编写枚举取值集合单元测试
    - 验证 `TraceStepType` 恰好 10 个、`TraceStepStatus` 恰好 4 个、`TraceStatus` 恰好 3 个取值且无其它取值
    - _Requirements: 1.3, 1.4, 1.5, 2.1, 2.3_

  - [ ] 2.4 实现 TraceSpan 步骤实体
    - 在 `com.yupi.yuaiagent.trace.model` 下创建 `TraceSpan`（Lombok `@Data/@Builder/@NoArgsConstructor/@AllArgsConstructor`），字段：spanId、traceId、sequence、stepType、stepName、status、startTime、endTime、durationMs、metadata、errorMessage
    - 实现 `start(...)`（RUNNING、startTime=now、生成 spanId）、`isTerminal()`、`terminate(status, now, meta, error)`（设 endTime/durationMs=非负毫秒差、合并 metadata、ERROR 时 errorMessage 非空兜底「未知错误」）
    - _Requirements: 1.2, 5.1, 5.2, 5.3, 5.6, 5.7_

  - [ ] 2.5 实现 ExecutionTrace 轨迹实体
    - 在 `com.yupi.yuaiagent.trace.model` 下创建 `ExecutionTrace`（Lombok 注解），字段：traceId、requestId、chatId、userId、status、startTime、endTime、durationMs、spans（默认空列表）
    - 实现 `start(requestId, chatId, userId)`（生成唯一 traceId、RUNNING、startTime=now）与 `finalizeStatus(now)`（endTime=now、任一 span 为 ERROR→FAILED 否则 COMPLETED、durationMs=非负毫秒差）
    - _Requirements: 1.1, 1.7, 1.8, 1.9, 1.10, 1.11, 1.12, 5.5_

  - [ ]* 2.6 编写终态计时不变量属性测试
    - **Property 1: 终态计时不变量**
    - **Validates: Requirements 1.1, 1.2, 5.2, 5.4, 5.7**

  - [ ]* 2.7 编写 RUNNING 期间无终态字段属性测试
    - **Property 2: RUNNING 期间无终态字段**
    - **Validates: Requirements 1.8, 1.12, 5.1, 5.6**

  - [ ]* 2.8 编写轨迹状态推导属性测试
    - **Property 4: 轨迹状态推导**
    - **Validates: Requirements 1.10, 1.11, 5.5**

- [ ] 3. 实现请求级上下文 TraceContext [P1]
  - [ ] 3.1 实现 TraceContext 同步上下文
    - 在 `com.yupi.yuaiagent.trace` 下创建 `TraceContext`（持有 `ExecutionTrace`、内部 monitor 锁、`sseClosed` 标志、`noop()` 工厂、`isNoop()`、`traceId()`）
    - 实现 `appendSpan(type, stepName, maxSpans)`（sequence 从 1 连续递增、达 `min(maxSpans, ABSOLUTE_MAX_SPANS)` 上限返回 null 并记 warn、span.traceId 关联本轨迹）、`finishSpan(...)`（终态幂等）、`failRunningSpan(error)`、`finalizeTrace()`
    - _Requirements: 1.9, 4.13, 4.14, 7.3, 7.6, 11.2_

  - [ ]* 3.2 编写步骤序号连续且关联同一轨迹属性测试
    - **Property 3: 步骤序号连续且关联同一轨迹**
    - **Validates: Requirements 1.9, 4.13, 7.3**

  - [ ]* 3.3 编写单轨迹 span 容量上限属性测试
    - **Property 10: 单轨迹 span 容量上限**
    - **Validates: Requirements 11.2**

  - [ ]* 3.4 编写标识在生命周期内不变（含匿名）属性测试
    - **Property 8: 标识在生命周期内不变（含匿名）**
    - **Validates: Requirements 7.4, 7.6**

- [ ] 4. 实现采集门面 TraceRecorder [P1]
  - [ ] 4.1 实现 TraceRecorder 容错采集入口
    - 在 `com.yupi.yuaiagent.trace` 下创建 `@Component TraceRecorder`，注入 `TraceRepository`、`TraceProperties`、`@Resource(required=false) TraceStreamPublisher`（P2 占位，可空）
    - 实现 `startTrace`（异常时返回 `TraceContext.noop()`）、`startSpan`、`endSpan`、`failSpan`、`skipSpan`、`endTrace`、`failTrace`、私有 `finish(...)`；每个公开方法 try-catch 包裹，绝不抛给调用方，异常时记录含失败步骤标识与原因的 `log.error`
    - _Requirements: 1.7, 1.8, 2.4, 2.5, 4.1, 4.15, 5.1, 5.2, 5.3, 6.1, 7.4_

  - [ ] 4.2 实现 metadata 限额截断与错误信息处理
    - 实现 `sanitize(metadata)`（≤50 键、键≤128 字符、值按 Unicode 码点截断到 `metadataMaxValueChars`，保序 `LinkedHashMap`）
    - 实现 `describe(Throwable)`（类名+message，空兜底）与 `truncate(s, MAX_ERROR_CHARS)` 按码点截断
    - _Requirements: 1.6, 5.3, 11.4_

  - [ ]* 4.3 编写标识全局唯一属性测试
    - **Property 7: 标识全局唯一**
    - **Validates: Requirements 1.7, 7.1, 7.5**

  - [ ]* 4.4 编写错误信息非空且有界属性测试
    - **Property 5: 错误信息非空且有界**
    - **Validates: Requirements 5.3**

  - [ ]* 4.5 编写 metadata 限额与码点截断属性测试
    - **Property 6: metadata 限额与码点截断**
    - **Validates: Requirements 1.6, 11.4**

  - [ ]* 4.6 编写记录器容错——绝不向主流程抛异常属性测试
    - **Property 15: 记录器容错——绝不向主流程抛异常**
    - **Validates: Requirements 4.15, 6.1**

  - [ ]* 4.7 编写 TraceRecorder 三态与异步尾步骤单元测试
    - 验证 `skipSpan`/`endSpan`/`failSpan` 三态可达、`failRunningSpan` 行为、PROFILE_UPDATE 作为异步尾步骤
    - _Requirements: 2.4, 2.5, 4.10_

- [ ] 5. 实现轨迹持久化与保留容量 TraceRepository [P1]
  - [ ] 5.1 实现 TraceRepository 持久化骨架
    - 在 `com.yupi.yuaiagent.trace` 下创建 `@Repository TraceRepository`，复用 `ArtifactRepository` 范式：`ObjectMapper + JavaTimeModule`、`ConcurrentHashMap<String,ExecutionTrace>`、`ReadWriteLock`、`@Value("${trace.storage.dir:./tmp/traces}")`、`@PostConstruct init()`（`mkdirs` + `loadFromFile`）、`writerWithDefaultPrettyPrinter` 写盘
    - 实现 `save`（先更新内存索引再写盘）、`findById`；`init` 读取失败时记 error 日志并以空集合完成初始化，读取成功不记错误日志；写盘失败仅记日志保留内存其它轨迹
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 3.8, 3.10, 3.11_

  - [ ] 5.2 实现列表查询与单用户保留策略
    - 实现 `findByChatId`（startTime 倒序）、`findByUserId`（startTime 倒序）、私有 `enforceUserRetention(userId)`（超 `maxTracesPerUser` 时按 startTime 升序删最早直至不超上限，并在 `save` 内持久化）
    - _Requirements: 8.2, 8.3, 8.4, 11.6_

  - [ ]* 5.3 编写序列化往返一致属性测试
    - **Property 9: 序列化往返一致**
    - **Validates: Requirements 3.6**

  - [ ]* 5.4 编写单用户轨迹保留上限属性测试
    - **Property 11: 单用户轨迹保留上限**
    - **Validates: Requirements 11.6**

  - [ ]* 5.5 编写列表查询过滤与倒序属性测试
    - **Property 13: 列表查询过滤与倒序**
    - **Validates: Requirements 8.2, 8.3, 8.4**

  - [ ]* 5.6 编写 TraceRepository 加载/容错单元测试
    - 覆盖损坏文件→空集合、合法文件→加载、写盘失败→内存其它轨迹保留
    - _Requirements: 3.7, 3.8, 3.11_

- [ ] 6. Checkpoint — 轨迹模型/上下文/记录器/持久化可用
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. 实现轨迹查询 REST 接口 TraceController [P1]
  - [ ] 7.1 实现 TraceController 三个查询接口
    - 在 `controller` 下创建 `@RestController @RequestMapping("/trace")` 的 `TraceController`，注入 `TraceRepository`、`JwtUtil`、`SessionManager`
    - 实现 `GET /trace/{traceId}`（JWT + 仅本人，缺参 400 / 未授权 401 / 不存在 404 / 归属他人 403）、`GET /trace/chat/{chatId}`（`SessionManager.isOwner` 校验、归属后返回该 chatId 全部轨迹不再按单条 userId 过滤）、`GET /trace/user/{userId}`（`userId==jwtUserId` 校验）；私有 `requireUserId(authHeader)`；全部以 `Result` 包装，绝不抛异常
    - _Requirements: 8.1, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10, 8.11, 8.12, 8.13, 8.14_

  - [ ]* 7.2 编写授权过滤绝不泄露他人轨迹属性测试
    - **Property 14: 授权过滤绝不泄露他人轨迹**
    - **Validates: Requirements 8.9**

  - [ ]* 7.3 编写 TraceController 各分支单元测试
    - 覆盖 401/400/404/403/200 各分支、`Result` 包装、`isOwner` 校验、跨用户 chatId 语义
    - _Requirements: 8.1, 8.5, 8.6, 8.7, 8.8, 8.10, 8.11, 8.12, 8.13, 8.14_

- [ ] 8. 集成轨迹采集到编排链路 [P1]
  - [ ] 8.1 AiController 生成并透传 requestId
    - 在 `AiController.doChatWithOrchestrator` 生成全局唯一 `requestId`（如 `UUID`）并透传给 `orchestratorAgent.chatStream(message, chatId, userId, requestId)`，保持既有鉴权与会话归属逻辑不变
    - _Requirements: 7.1, 7.2_

  - [ ] 8.2 OrchestratorAgent 注入 TraceRecorder 并挂接 chatStream 生命周期
    - 在 `AgentConfig.orchestratorAgent` 构造注入 `TraceRecorder`；新增带 `requestId` 的 `chatStream` 重载（旧重载内部兜底生成）；在 `chatStream` 中 `startTrace`、记录 SKILL_MATCH（metadata.matched）、命中技能分支 `endTrace`、异常分支 `failTrace`
    - _Requirements: 4.1, 4.2, 4.12, 4.14, 4.15, 6.1, 7.2_

  - [ ] 8.3 routeToAgent 插入各环节采集挂点
    - 在 `routeToAgent` 记录 INTENT_DETECTION（metadata.intent）、ROUTING（metadata.agent）、PROFILE_INJECTION（metadata.chars）、ARTIFACT_QUERY（metadata.count + artifactIds）、ARTIFACT_CONSUME（有 READY 记录 ids / 无 READY 记 SKIPPED）、SUB_AGENT_EXECUTION（围绕子 Agent `Flux` 的 `doOnComplete`/`doOnError` 记 SUCCESS/ERROR）；全部复用既有执行结果，不额外触发 LLM/子 Agent/工具
    - _Requirements: 4.3, 4.4, 4.5, 4.7, 4.8, 4.9, 2.4_

  - [ ] 8.4 triggerProfileUpdate 记录异步 PROFILE_UPDATE 尾步骤
    - 在对话结束 `doOnComplete` 先 `endTrace` 固化一次，再在异步画像更新任务里记录 PROFILE_UPDATE 步骤（匿名用户记 SKIPPED）并 `endTrace` 再次持久化（save 幂等覆盖更新）
    - _Requirements: 4.10, 9.6_

  - [ ] 8.5 ToolCallAgent 透传 TraceContext 记录 TOOL_CALL
    - 在 `ToolCallAgent` 工具调用处新增可空 `TraceContext` 参数，每次工具调用记录 stepType=TOOL_CALL 的 span（metadata 记被调用工具名称）；参数为 null 时静默跳过，保证既有调用方零影响
    - _Requirements: 4.6_

  - [ ] 8.6 ChatMemoryManager 记录 MEMORY_COMPRESSION
    - 在 `ChatMemoryManager`（或压缩触发处）新增可空 `TraceContext` 参数，记忆压缩触发时记录 stepType=MEMORY_COMPRESSION 的 span；未传 `TraceContext` 时静默跳过
    - _Requirements: 4.11_

  - [ ]* 8.7 编写编排采集集成测试
    - mock 子 Agent/技能/货架运行对话，断言 10 类 stepType 的 span 被记录且 metadata 含规定键、恰好一条轨迹、COMPLETED/FAILED 且落盘
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 4.11, 4.12, 4.14, 7.2_

  - [ ]* 8.8 编写非侵入集成测试
    - 计数 mock：有/无采集两次运行，LLM/子 Agent/工具调用次数相等（增量为 0），用户响应完整未截断
    - _Requirements: 6.1, 6.3_

  - [ ]* 8.9 编写持久化集成测试
    - 验证 `@PostConstruct` 加载、存储目录创建、`save` 后 `findById` 命中且文件含该 id
    - _Requirements: 3.4, 3.5, 3.10_

  - [ ]* 8.10 编写采集性能测试
    - 验证单 SSE 事件采集延迟 ≤50ms、单请求采集额外耗时 ≤ max(5%, 50ms)
    - _Requirements: 6.4, 6.5_

- [ ] 9. Checkpoint — P1 核心闭环完成
  - Ensure all tests pass, ask the user if questions arise.

### P2 阶段 — 实时轨迹事件流

- [ ] 10. 实现实时轨迹事件流 [P2]
  - [ ] 10.1 TraceContext 绑定 SseEmitter 与关闭标志
    - 为 `TraceContext` 增加 `SseEmitter` 引用（或轻量回调）与 `markSseClosed()`/`isSseClosed()`，由 `OrchestratorAgent` 在 `startTrace` 后绑定、在 `emitter.complete()` 时置位
    - _Requirements: 9.6_

  - [ ] 10.2 实现 TraceStreamPublisher 并接入 TraceRecorder
    - 在 `com.yupi.yuaiagent.trace` 下创建 `@Component TraceStreamPublisher`，`publish(ctx, span)`：开关关闭或 SSE 已关闭则不推送；否则以独立命名事件 `trace` 推送 `{sequence, stepType, stepName, status}`（与 routing/message/error 并存），推送失败仅记日志不中断对话、不影响持久化
    - 在 `TraceRecorder.startSpan`/`finish` 的预留点调用 `streamPublisher.publish(...)`（fire-and-forget）
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

  - [ ]* 10.3 编写实时事件流集成测试
    - 覆盖 trace 事件与 routing/message/error 并存、推送失败容错、开关关闭仍持久化、SSE 关闭后异步 span 不推送但持久化
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

- [ ] 11. Checkpoint — P2 完成
  - Ensure all tests pass, ask the user if questions arise.

### P3 阶段 — 前端时间线可视化

- [ ] 12. 实现前端时间线视图 [P3]
  - [ ] 12.1 前端 trace API 与 TraceTimelineView 组件
    - 在前端（`yu-ai-agent-frontend`，Vue）新增 trace API 调用与 `TraceTimelineView` 组件及入口；打开时以选中 `traceId` 调用 `GET /api/trace/{traceId}`（携带 JWT），按 `sequence` 升序渲染各步骤
    - 每个步骤展示 `stepType` 中文显示名、`status`、`durationMs`；ERROR 步骤用区别于 SUCCESS 的样式并展示 `errorMessage`；RUNNING 步骤以「进行中」占位；切换 traceId 重新拉取渲染；未找到/加载失败展示可读错误提示而非空白
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7_

- [ ] 13. Final Checkpoint — 全部完成
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- 标注 `*` 的子任务为可选（属性测试 / 单元测试 / 集成测试 / 性能测试），可为加速 MVP 跳过；核心实现任务不可跳过。
- 每个任务标注了 P1/P2/P3 优先级与具体需求子条款编号，便于追溯。
- P1（任务 1–9）为完整核心闭环：数据模型与枚举 → 请求级上下文 → 容错记录器 → 文件持久化（含保留/容量）→ 查询 REST 接口 → 编排链路集成。
- 持久化组件 `TraceRepository` 严格复用现有 `ArtifactRepository` / `AppointmentRepository` 风格（Jackson + JavaTimeModule + JSON + 读写锁 + `@PostConstruct` 加载）。
- 属性测试使用 **jqwik**（`@Property(tries = 100)`），与设计 Correctness Properties 一节的 16 条属性一一对应；每条属性以注释 `// Feature: agent-execution-trace, Property {number}: ...` 标注。
- Req 11 的服务端容量/保留逻辑（span 上限、metadata 截断、单用户保留）在 P1 的 `TraceProperties`/`TraceContext`/`TraceRecorder`/`TraceRepository` 中落地（属性 P6/P10/P11/P12 覆盖）；其前端展示属于 P3。
- Checkpoint 任务用于阶段性验证。

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1"] },
    { "id": 2, "tasks": ["1.3", "1.4", "2.2", "2.3", "2.4"] },
    { "id": 3, "tasks": ["2.5"] },
    { "id": 4, "tasks": ["2.6", "2.7", "2.8", "3.1", "5.1"] },
    { "id": 5, "tasks": ["3.2", "3.3", "3.4", "5.2", "4.1"] },
    { "id": 6, "tasks": ["4.2", "5.3", "5.4", "5.5", "5.6"] },
    { "id": 7, "tasks": ["4.3", "4.4", "4.5", "4.6", "4.7", "7.1"] },
    { "id": 8, "tasks": ["7.2", "7.3", "8.1", "8.2", "8.5", "8.6"] },
    { "id": 9, "tasks": ["8.3"] },
    { "id": 10, "tasks": ["8.4"] },
    { "id": 11, "tasks": ["8.7", "8.8", "8.9", "8.10"] },
    { "id": 12, "tasks": ["10.1"] },
    { "id": 13, "tasks": ["10.2"] },
    { "id": 14, "tasks": ["10.3"] },
    { "id": 15, "tasks": ["12.1"] }
  ]
}
```
