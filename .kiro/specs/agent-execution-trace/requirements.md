# Requirements Document

## Introduction

本功能为现有职场 AI Agent 系统（Java 21 + Spring Boot 3.4 + Spring AI 1.0）新增 **Agent 执行轨迹可视化（Execution Trace / Timeline）** 能力。系统需在每一次对话请求的完整执行链路上记录可回放的执行轨迹，覆盖：技能匹配（SkillExecutor）、LLM 意图识别（AgentIntent）、路由到专业子 Agent（ResumeAgent / NegotiationAgent / EscapeAgent / ConsultationAgent / GeneralCareerAgent）、工具调用、共享货架（ArtifactShelf）的 READY 交付物查询与 CONSUMED 标记、用户画像注入（UserProfileService.buildPromptInjection）与对话结束后的异步画像更新（UserProfileService.updateAsync）、对话记忆压缩。

记录下来的轨迹需可在前端以时间线形式回放，用于演示完整 Multi-Agent 流转（强调演示与简历价值）。轨迹采集必须满足两个硬约束：**对主对话流程零阻塞、零破坏**（采集失败时主流程不受影响），以及**性能开销最小化**。轨迹数据需持久化（服务重启后不丢失），并通过受 JWT 保护的 REST 接口按 traceId / chatId / userId 查询，用户仅能访问属于自己的轨迹。

持久化风格与现有 `ArtifactRepository`、`AppointmentRepository`、`UserProfileRepository`、`SessionManager` 一致：Jackson + JavaTimeModule 序列化为 JSON、内存 `ConcurrentHashMap` 索引、`ReadWriteLock` 保护并发、`@PostConstruct` 加载、`@Value` 注入存储目录（默认 `./tmp/traces`）。REST 响应统一使用 `com.yupi.yuaiagent.common.Result` 包装。

本需求文档按交付优先级标注：

- **P1（第一期，核心闭环）**：轨迹数据模型 + 轨迹持久化 + 编排流程轨迹采集 + 计时/状态记录 + 容错非侵入 + 轨迹标识关联 + 查询 REST 接口
- **P2**：实时轨迹事件流（与对话 SSE 并行推送）
- **P3**：前端时间线可视化入口 + 数据保留与容量限制

每个需求标题后以 `[P1]`/`[P2]`/`[P3]` 标注其交付优先级。

## Glossary

- **System**: 职场 AI Agent 系统整体，包含 OrchestratorAgent、各专业子 Agent、共享货架、用户画像系统及本功能新增的执行轨迹设施
- **OrchestratorAgent**: 主控 Agent，对话请求的统一入口，负责技能匹配、意图识别、路由分发、画像与交付物注入、对话结束触发画像更新
- **Execution_Trace**: 执行轨迹实体，表示一次对话请求的完整执行链路，包含 traceId、requestId、chatId、userId、startTime、endTime、durationMs、status、spans（步骤列表）等字段
- **Trace_Span**: 执行轨迹中的单个步骤记录（步骤/跨度），表示链路中的一个执行环节，包含 spanId、traceId、sequence、stepType、stepName、status、startTime、endTime、durationMs、metadata、errorMessage 等字段
- **Trace_Step_Type**: 轨迹步骤类型枚举，取值为 SKILL_MATCH、INTENT_DETECTION、ROUTING、SUB_AGENT_EXECUTION、TOOL_CALL、ARTIFACT_QUERY、ARTIFACT_CONSUME、PROFILE_INJECTION、PROFILE_UPDATE、MEMORY_COMPRESSION
- **Trace_Step_Status**: 轨迹步骤状态枚举，取值为 RUNNING（进行中）、SUCCESS（成功）、ERROR（失败）、SKIPPED（跳过）
- **Trace_Status**: 整条轨迹的状态枚举，取值为 RUNNING、COMPLETED、FAILED
- **Trace_Recorder**: 轨迹采集组件，提供开始轨迹、开始步骤、结束步骤、结束轨迹的统一入口，被 OrchestratorAgent 及相关组件调用以记录 Trace_Span
- **Trace_Repository**: 轨迹文件持久化组件，使用 Jackson + JavaTimeModule + JSON + 读写锁，存储风格与现有 ArtifactRepository 一致
- **Trace_Controller**: 轨迹查询 REST 接口，供前端按 traceId / chatId / userId 查询轨迹，受 JWT 保护
- **Trace_Stream**: 实时轨迹事件流，在对话 SSE 连接上以独立命名事件向前端推送步骤级轨迹事件
- **Trace_Timeline_View**: 前端时间线可视化视图，按时间顺序展示一条 Execution_Trace 的各 Trace_Span
- **Request_Id**: 单次对话请求的唯一标识，由 System 在请求进入时生成，用于关联同一请求产生的轨迹
- **Trace_Id**: Execution_Trace 的全局唯一标识
- **User**: 已登录的终端用户，由 JWT 中的 userId 标识
- **JwtUtil**: JWT 工具组件，负责校验 Token 并解析出 userId
- **SessionManager**: 会话管理组件，负责校验 chatId 与 userId 的归属关系
- **Result**: 统一 REST 响应包装类 `com.yupi.yuaiagent.common.Result`

## Requirements

### Requirement 1: 执行轨迹与步骤数据模型 [P1]

**User Story:** 作为系统开发者，我希望有统一的执行轨迹与步骤数据模型，以便完整描述一次对话请求的执行链路并支持前端回放。

#### Acceptance Criteria

1. THE Execution_Trace SHALL 包含以下字段：traceId、requestId、chatId、userId、status、startTime、endTime、durationMs、spans；其中 spans 列表最多包含 1000 个 Trace_Span，durationMs SHALL 等于 endTime 与 startTime 之差并以毫秒表示。
2. THE Trace_Span SHALL 包含以下字段：spanId、traceId、sequence、stepType、stepName、status、startTime、endTime、durationMs、metadata、errorMessage；其中 durationMs SHALL 等于该 Trace_Span 的 endTime 与 startTime 之差并以毫秒表示。
3. THE Execution_Trace 的 status 字段 SHALL 使用 Trace_Status 枚举表示，取值为 RUNNING、COMPLETED、FAILED。
4. THE Trace_Span 的 status 字段 SHALL 使用 Trace_Step_Status 枚举表示，取值为 RUNNING、SUCCESS、ERROR、SKIPPED。
5. THE Trace_Span 的 stepType 字段 SHALL 使用 Trace_Step_Type 枚举表示。
6. THE Trace_Span 的 metadata 字段 SHALL 以键值对形式存储该步骤的附加信息，且最多包含 50 个键值对，每个键长度不超过 128 个字符，每个值长度不超过 4096 个字符。
7. WHEN 一个 Execution_Trace 被创建且未显式指定 traceId 时，THE Trace_Recorder SHALL 生成全局唯一的 traceId。
8. WHEN 一个 Execution_Trace 被创建时，THE Trace_Recorder SHALL 将 startTime 设置为当前时间并将 status 设置为 RUNNING。
9. THE Execution_Trace 的 spans 列表 SHALL 按 sequence 字段升序排列，sequence 从 1 开始且连续递增，并反映各步骤的实际发生顺序。
10. WHEN 一个 Execution_Trace 的全部 Trace_Span 均以 SUCCESS 或 SKIPPED 状态结束时，THE Trace_Recorder SHALL 将该 Execution_Trace 的 status 设置为 COMPLETED、将 endTime 设置为当前时间，并将 durationMs 设置为 endTime 与 startTime 之差（毫秒）。
11. IF 一个 Execution_Trace 中存在任一 Trace_Span 以 ERROR 状态结束，THEN THE Trace_Recorder SHALL 将该 Execution_Trace 的 status 设置为 FAILED、将 endTime 设置为当前时间、将 durationMs 设置为 endTime 与 startTime 之差（毫秒），并在对应 Trace_Span 的 errorMessage 字段记录指示失败原因的错误信息。
12. WHILE 一个 Execution_Trace 的 status 为 RUNNING，THE Execution_Trace 的 endTime 与 durationMs SHALL 保持为空（未设置）。

### Requirement 2: 轨迹步骤类型与状态枚举 [P1]

**User Story:** 作为系统开发者，我希望有标准化的步骤类型与状态枚举，以便前端按类型渲染时间线并区分每一步的成败。

#### Acceptance Criteria

1. THE Trace_Step_Type 枚举 SHALL 恰好包含以下 10 个取值且不包含任何其他取值：SKILL_MATCH、INTENT_DETECTION、ROUTING、SUB_AGENT_EXECUTION、TOOL_CALL、ARTIFACT_QUERY、ARTIFACT_CONSUME、PROFILE_INJECTION、PROFILE_UPDATE、MEMORY_COMPRESSION。
2. THE Trace_Step_Type 的每个枚举值 SHALL 提供用于前端展示的中文显示名称，该显示名称非空、长度为 1 至 50 个字符，且每个枚举值与其显示名称一一对应（不同枚举值的显示名称互不相同）。
3. THE Trace_Step_Status 枚举 SHALL 恰好包含以下 4 个取值且不包含任何其他取值：RUNNING、SUCCESS、ERROR、SKIPPED。
4. WHEN 一个执行环节被采集但未实际执行（例如技能未匹配或无 READY 交付物）时，THE Trace_Recorder SHALL 将对应 Trace_Span 的 status 记录为 SKIPPED。
5. WHILE 一个执行环节实际被执行，THE Trace_Recorder SHALL 将对应 Trace_Span 的 status 记录为 RUNNING、SUCCESS 或 ERROR 之一。

### Requirement 3: 轨迹持久化存储 [P1]

**User Story:** 作为系统开发者，我希望执行轨迹以文件方式持久化，以便服务重启后轨迹不丢失，且实现风格与现有存储组件一致。

#### Acceptance Criteria

1. THE Trace_Repository SHALL 使用 Jackson 并注册 JavaTimeModule 将 Execution_Trace 序列化为 JSON 并持久化到文件。
2. THE Trace_Repository SHALL 使用读写锁保护并发读写操作。
3. THE Trace_Repository SHALL 使用 ConcurrentHashMap 维护 traceId 到 Execution_Trace 的内存索引。
4. WHEN System 启动时，THE Trace_Repository SHALL 通过 `@PostConstruct` 从持久化文件加载已有轨迹到内存。
5. WHEN 一个 Execution_Trace 被保存或更新时，THE Trace_Repository SHALL 先更新 traceId 到 Execution_Trace 的内存索引，并在 save 操作返回前将变更写入持久化文件。
6. THE Trace_Repository SHALL 保证任一有效 Execution_Trace 对象序列化为 JSON 后再反序列化得到的对象与原对象逐字段相等（含 spans 列表顺序与毫秒级时间精度），即往返一致性。
7. IF 持久化文件读取失败，THEN THE Trace_Repository SHALL 记录错误日志并以空轨迹集合完成初始化，且记录日志与空集合初始化两个动作均须完成。
8. WHEN 持久化文件读取成功，THE Trace_Repository SHALL 加载文件中已有的轨迹且不记录读取错误日志。
9. THE Trace_Repository 的存储目录 SHALL 通过配置项 `trace.storage.dir` 指定，默认值为 `./tmp/traces`。
10. IF 配置的存储目录不存在，THEN THE Trace_Repository SHALL 在初始化时创建该存储目录。
11. IF 持久化写入失败，THEN THE Trace_Repository SHALL 记录错误日志并保留内存中其他已加载的轨迹不受影响。

### Requirement 4: 编排流程轨迹采集 [P1]

**User Story:** 作为开发者，我希望系统在 OrchestratorAgent 的每一个执行环节自动采集轨迹步骤，以便完整还原 Multi-Agent 的流转过程。

#### Acceptance Criteria

1. WHEN OrchestratorAgent 开始处理一次对话请求时，THE Trace_Recorder SHALL 创建一条 Execution_Trace、将其 status 设置为 RUNNING、将 startTime 设置为当前时间，并关联本次请求的 requestId、chatId 与 userId。
2. WHEN OrchestratorAgent 执行技能匹配时，THE Trace_Recorder SHALL 记录一个 stepType 为 SKILL_MATCH 的 Trace_Span，并在 metadata 中记录是否命中技能（布尔值 true/false）。
3. WHEN OrchestratorAgent 执行 LLM 意图识别时，THE Trace_Recorder SHALL 记录一个 stepType 为 INTENT_DETECTION 的 Trace_Span，并在 metadata 中记录识别出的 AgentIntent 结果。
4. WHEN OrchestratorAgent 将请求路由到某个专业子 Agent 时，THE Trace_Recorder SHALL 记录一个 stepType 为 ROUTING 的 Trace_Span，并在 metadata 中记录被选中的子 Agent 名称。
5. WHEN 某个专业子 Agent 开始处理请求时，THE Trace_Recorder SHALL 记录一个 stepType 为 SUB_AGENT_EXECUTION 的 Trace_Span，并在 metadata 中记录该子 Agent 名称。
6. WHEN 某个子 Agent 发起一次工具调用时，THE Trace_Recorder SHALL 记录一个 stepType 为 TOOL_CALL 的 Trace_Span，并在 metadata 中记录被调用的工具名称。
7. WHEN OrchestratorAgent 查询货架中状态为 READY 的交付物时，THE Trace_Recorder SHALL 记录一个 stepType 为 ARTIFACT_QUERY 的 Trace_Span，并在 metadata 中记录命中的交付物数量（大于或等于 0 的整数）与其 artifactId 列表。
8. WHEN OrchestratorAgent 将交付物标记为 CONSUMED 时，THE Trace_Recorder SHALL 记录一个 stepType 为 ARTIFACT_CONSUME 的 Trace_Span，并在 metadata 中记录被标记的 artifactId 列表。
9. WHEN OrchestratorAgent 构建用户画像注入片段时，THE Trace_Recorder SHALL 记录一个 stepType 为 PROFILE_INJECTION 的 Trace_Span，并在 metadata 中记录注入内容的字符长度（大于或等于 0 的整数）。
10. WHEN 对话结束后触发异步画像更新时，THE Trace_Recorder SHALL 记录一个 stepType 为 PROFILE_UPDATE 的 Trace_Span。
11. WHEN System 执行对话记忆压缩时，THE Trace_Recorder SHALL 记录一个 stepType 为 MEMORY_COMPRESSION 的 Trace_Span。
12. WHEN 一次对话请求的执行链路成功结束且未发生异常时，THE Trace_Recorder SHALL 将该 Execution_Trace 的 endTime 设置为当前时间、status 由 RUNNING 更新为 COMPLETED，并通过 Trace_Repository 持久化该轨迹。
13. THE Trace_Recorder SHALL 将其记录的每个 Trace_Span 关联至当前 status 为 RUNNING 的 Execution_Trace，并记录该 Trace_Span 的 startTime 与 endTime。
14. IF 一次对话请求的执行链路因异常而中断，THEN THE Trace_Recorder SHALL 将该 Execution_Trace 的 endTime 设置为当前时间、status 由 RUNNING 更新为 FAILED，并通过 Trace_Repository 持久化该轨迹。
15. IF Trace_Recorder 在记录任一 Trace_Span 或持久化 Execution_Trace 过程中发生异常，THEN THE Trace_Recorder SHALL 捕获该异常并允许 OrchestratorAgent 继续执行本次对话请求的主流程而不中断本次对话请求。

### Requirement 5: 步骤计时与状态记录 [P1]

**User Story:** 作为开发者，我希望每个轨迹步骤都记录耗时与执行结果，以便在时间线上定位耗时瓶颈与失败环节。

#### Acceptance Criteria

1. WHEN 一个 Trace_Span 开始时，THE Trace_Recorder SHALL 将该 Trace_Span 的 startTime 设置为当前时间并将 status 设置为 RUNNING。
2. WHEN 一个 Trace_Span 未抛出异常地正常结束时，THE Trace_Recorder SHALL 将该 Trace_Span 的 endTime 设置为当前时间、status 设置为 SUCCESS，并将 durationMs 设置为 endTime 与 startTime 的毫秒差。
3. IF 一个执行环节抛出异常，THEN THE Trace_Recorder SHALL 将对应 Trace_Span 的 endTime 设置为当前时间、status 设置为 ERROR、durationMs 设置为 endTime 与 startTime 的毫秒差，并将异常的描述信息记录到 errorMessage 字段（非空、最多 2048 个字符，超出时截断）。
4. WHILE 一个 Trace_Span 的 status 为 SUCCESS、ERROR 或 SKIPPED，THE Trace_Span 的 durationMs SHALL 为非负整数（毫秒）。
5. IF 一个 Execution_Trace 中存在 status 为 ERROR 的 Trace_Span，THEN THE Trace_Recorder SHALL 将该 Execution_Trace 的 status 设置为 FAILED。
6. WHILE 一个 Trace_Span 的 status 为 RUNNING，THE Trace_Span 的 endTime 与 durationMs SHALL 保持为空（未设置）。
7. WHEN 一个 Trace_Span 以 SKIPPED 状态结束时，THE Trace_Recorder SHALL 将该 Trace_Span 的 endTime 设置为当前时间并将 durationMs 设置为 endTime 与 startTime 的毫秒差。

### Requirement 6: 轨迹采集的容错与非侵入 [P1]

**User Story:** 作为用户，我希望轨迹采集绝不影响我的正常对话，以便即使轨迹功能异常我仍能获得完整回答。

#### Acceptance Criteria

1. IF 任一轨迹采集操作（创建轨迹、记录步骤、结束步骤、结束轨迹、持久化）抛出异常，THEN THE System SHALL 捕获该异常、记录包含失败步骤标识与原因的错误日志，并向用户返回完整且未被截断的对话响应。
2. IF Trace_Repository 持久化轨迹失败，THEN THE System SHALL 记录错误日志，且本次对话的响应内容与 SSE 事件序列与持久化成功时保持一致。
3. THE 轨迹采集 SHALL 复用 OrchestratorAgent 主流程已有的执行结果，因轨迹采集而额外触发的 LLM 意图识别、子 Agent 与工具调用次数 SHALL 均为 0。
4. WHEN System 向用户流式返回对话响应时，THE 轨迹采集引入的单个 SSE 事件发送延迟 SHALL 不超过 50 毫秒。
5. THE 单次对话请求的轨迹采集额外耗时 SHALL 不超过该请求总耗时的 5% 与 50 毫秒中的较大者。

### Requirement 7: 轨迹标识与请求关联 [P1]

**User Story:** 作为开发者，我希望每条轨迹都能通过稳定标识与具体请求、会话和用户关联，以便按需检索和回放对应的执行链路。

#### Acceptance Criteria

1. WHEN System 接收一次对话请求时，THE System SHALL 为该请求生成在 System 已处理与已存储的全部对话请求范围内唯一的 requestId。
2. WHEN System 处理一次对话请求时，THE System SHALL 为该请求创建恰好一条 Execution_Trace，并在该轨迹中记录其所属的 requestId、chatId 与 userId。
3. THE Trace_Recorder SHALL 将同一次请求（即同一 requestId）产生的所有 Trace_Span 关联到该轨迹唯一的 traceId。
4. WHERE 对话请求来自无法解析出 userId 的匿名调用方，THE Trace_Recorder SHALL 在 userId 字段为 null 或空字符串的情况下仍创建并记录 Execution_Trace，且仍生成 requestId 与 traceId。
5. THE Trace_Id SHALL 在 System 已存储的全部轨迹范围内保持唯一。
6. THE Execution_Trace 的 traceId、requestId、chatId 与 userId SHALL 在该轨迹整个生命周期内保持不变。

### Requirement 8: 轨迹查询 REST 接口 [P1]

**User Story:** 作为用户，我希望通过接口按 traceId、chatId 或自己的 userId 查询执行轨迹，以便在前端回放某次对话的完整流转。

#### Acceptance Criteria

1. WHEN User 通过 Trace_Controller 按 traceId 请求查询轨迹时，THE Trace_Controller SHALL 返回对应的 Execution_Trace。
2. WHEN User 通过 Trace_Controller 按 chatId 请求查询轨迹列表时，THE Trace_Controller SHALL 返回该 chatId 下的全部 Execution_Trace；无匹配轨迹时返回空列表。
3. WHEN User 通过 Trace_Controller 按 userId 请求查询轨迹列表时，THE Trace_Controller SHALL 返回 userId 与该请求 JWT 解析出的 userId 匹配的全部 Execution_Trace；无匹配轨迹时返回空列表。
4. THE Trace_Controller SHALL 按 startTime 倒序返回轨迹列表查询结果。
5. THE Trace_Controller SHALL 对成功、未找到、未授权与拒绝访问等所有结果均使用 Result 包装类返回响应。
6. IF 按 traceId 查询的轨迹不存在，THEN THE Trace_Controller SHALL 返回表示未找到的 Result 结果而非抛出异常。
7. THE Trace_Controller SHALL 对所有接口请求通过 JwtUtil 校验 JWT 并解析出 userId，且无论被请求轨迹的归属如何都执行该校验。
8. IF 任一 Trace_Controller 请求未携带有效 JWT，THEN THE Trace_Controller SHALL 返回未授权的 Result 结果且不返回任何轨迹数据。
9. WHEN User 按 traceId 或 userId 请求查询轨迹时，THE Trace_Controller SHALL 仅返回 userId 与该请求 JWT 解析出的 userId 匹配的轨迹。
10. IF User 按 traceId 请求查询的轨迹归属于其他 userId，THEN THE Trace_Controller SHALL 返回拒绝访问的 Result 结果、不返回任何轨迹数据且不抛出异常。
11. WHEN User 按 chatId 请求查询轨迹列表时，THE Trace_Controller SHALL 通过 SessionManager 校验该 chatId 归属于该请求 JWT 解析出的 userId。
12. WHERE chatId 归属于该 User，THE Trace_Controller SHALL 返回该 chatId 下的全部 Execution_Trace 而不再按单条轨迹的 userId 过滤。
13. IF User 按 chatId 请求查询的 chatId 不归属于该 User，THEN THE Trace_Controller SHALL 返回拒绝访问的 Result 结果、不返回任何轨迹数据且不抛出异常。
14. IF Trace_Controller 请求缺少所需的查询参数（traceId、chatId 或 userId 均未提供或为空）, THEN THE Trace_Controller SHALL 返回表示参数错误的 Result 结果而非抛出异常。

### Requirement 9: 实时轨迹事件流 [P2]

**User Story:** 作为用户，我希望在对话进行中实时看到执行步骤的发生，以便直观感受 Multi-Agent 的流转过程。

#### Acceptance Criteria

1. WHILE 对话的 SSE 连接处于打开状态，WHEN 一个 Trace_Span 的 status 转变为 RUNNING 或转变为终态（SUCCESS、ERROR、SKIPPED）时，THE System SHALL 在该对话的 SSE 连接上以独立命名事件 `trace` 推送该步骤的轨迹事件，推送延迟不超过 50 毫秒。
2. THE Trace_Stream 推送的 `trace` 事件 SHALL 与现有 `routing`、`message`、`error` 命名事件并存且不替换它们。
3. THE Trace_Stream 推送的每个 `trace` 事件 SHALL 包含该步骤的 sequence、stepType、stepName 与推送时刻的 status，且 stepType 取自 Trace_Step_Type、status 取自 Trace_Step_Status。
4. IF Trace_Stream 推送某个轨迹事件失败，THEN THE System SHALL 记录包含该步骤标识与失败原因的错误日志、继续推送对话的 `message` 事件、不中断对话响应，且该步骤的持久化采集不受影响。
5. WHERE 实时轨迹事件流被配置项关闭（该配置项默认启用），THE System SHALL 不推送 `trace` 事件且仍正常完成轨迹的持久化采集。
6. IF 一个 Trace_Span 在对话的 SSE 连接已关闭之后才被记录（例如异步的 PROFILE_UPDATE），THEN THE System SHALL 不推送 `trace` 事件且仍正常完成该步骤的持久化采集。

### Requirement 10: 前端时间线可视化入口 [P3]

**User Story:** 作为用户，我希望在前端有一个时间线视图查看某次对话的执行轨迹，以便完整演示 Multi-Agent 流转过程。

#### Acceptance Criteria

1. THE System SHALL 在前端提供查看某条 Execution_Trace 的 Trace_Timeline_View 入口。
2. WHEN User 在前端打开 Trace_Timeline_View 时，THE System SHALL 以所选 traceId 调用 Trace_Controller 获取该 Execution_Trace 并按 sequence 升序展示各 Trace_Span。
3. THE Trace_Timeline_View SHALL 为每个 Trace_Span 展示其步骤显示名称、status 与 durationMs。
4. WHERE 某个 Trace_Span 的 status 为 ERROR，THE Trace_Timeline_View SHALL 以不同于 status 为 SUCCESS 的 Trace_Span 的视觉样式展示该步骤并显示其 errorMessage。
5. WHEN User 在前端切换查看不同的 traceId 时，THE Trace_Timeline_View SHALL 展示所选 traceId 对应的轨迹。
6. WHERE 某个 Trace_Span 的 status 为 RUNNING（durationMs 未设置），THE Trace_Timeline_View SHALL 以占位形式展示该步骤而非展示空白或报错。
7. IF Trace_Controller 返回未找到或加载失败，THEN THE Trace_Timeline_View SHALL 向 User 展示可读的错误提示而非空白页面。

### Requirement 11: 数据保留与容量限制 [P3]

**User Story:** 作为系统维护者，我希望轨迹存储有保留策略与容量上限，以便长期运行时存储占用可控。

#### Acceptance Criteria

1. THE 单条 Execution_Trace 持有的 Trace_Span 数量上限 SHALL 通过配置项指定，取值范围为 1 至 1000，默认值为 200。
2. WHEN 向一条 Execution_Trace 追加 Trace_Span 且其 Trace_Span 数量已达到配置的上限时，THE Trace_Recorder SHALL 不追加新的 Trace_Span、保留已记录的 Trace_Span 不受影响，并记录一条达到上限的提示日志。
3. THE Trace_Span 的 metadata 单个值的字符长度上限 SHALL 通过配置项指定，取值范围为 1 至 4096，默认值为 2000 字符。
4. IF 某个 metadata 值的字符长度（按 Unicode 码点计数）超过配置的上限，THEN THE Trace_Recorder SHALL 将该值截断至上限长度后再记录。
5. THE System 每个 userId 保留的 Execution_Trace 数量上限 SHALL 通过配置项指定，取值范围为 1 至 100000，默认值为 500。
6. WHEN Trace_Repository 保存某 userId 的新轨迹后该 userId 的 Execution_Trace 数量超过配置的上限时，THE Trace_Repository SHALL 按 startTime 升序删除最早的轨迹直至不超过上限，并将删除结果持久化。
