# Implementation Plan: 数据员工 Agent、共享交付物货架与用户画像系统

## Overview

本实施计划基于 design.md，将三套能力（共享交付物货架、数据员工 Agent、用户画像系统）拆解为可逐个执行的编码任务。任务严格按需求文档的 **P1 / P2 / P3** 优先级分期组织，P1 在前（地基 + 最小闭环）。

技术栈：**Java 21 + Spring Boot 3.4 + Spring AI 1.0**。持久化组件复用现有 `AppointmentRepository` 范式（`ObjectMapper` + `JavaTimeModule` + `ConcurrentHashMap` + `ReadWriteLock` + `@PostConstruct` 加载 + `@Value` 配置目录）。

每个任务遵循增量推进顺序：**数据模型 → 持久化 → 货架/服务 → Agent → Controller → 集成**，后续任务在前序任务之上构建，最终在集成阶段把组件接入 `OrchestratorAgent` / `AiController`，不留孤立代码。

设计文档无 "Correctness Properties" 章节，因此测试任务采用单元测试与集成测试（标注 `*` 为可选），其中往返一致性（Req 2.5 / 10.5）与幂等性（Req 5.4）作为边界用例覆盖。

---

## Tasks

### P1 阶段 — 地基 + 最小闭环

- [ ] 1. 搭建交付物数据模型与全局配置 [P1]
  - [x] 1.1 创建 Artifact 实体与枚举
    - 在 `com.yupi.yuaiagent.artifact.model` 下创建 `Artifact`（Lombok `@Data/@Builder/@NoArgsConstructor/@AllArgsConstructor`），字段：artifactId、userId、chatId、type、producer、title、content（String 承载 JSON 或纯文本）、status、scope、createdAt、updatedAt
    - 创建 `ArtifactStatus` 枚举（PENDING / READY / CONSUMED）与 `ArtifactScope` 枚举（USER_PROFILE / TASK）
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [ ]* 1.2 编写 Artifact 序列化往返单元测试
    - 使用配置了 `JavaTimeModule` 的 `ObjectMapper` 验证 Artifact 序列化为 JSON 再反序列化得到等价对象
    - 覆盖结构化 JSON content 与纯文本 content 两种情形
    - _Requirements: 1.4, 2.5_

  - [x] 1.3 创建 ArtifactQuery 查询条件
    - 在 `com.yupi.yuaiagent.artifact.model` 下创建 `ArtifactQuery`（`@Data/@Builder`），全部字段可选：userId、chatId、type、scope、status（null 表示不约束）
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [x] 1.4 新增货架/画像/注入配置项
    - 在 `application.yml` 新增 `artifact.storage.dir`（默认 `./tmp/artifacts`）、`user-profile.storage.dir`（默认 `./tmp/user-profiles`）、`profile.injection.max-chars`（默认 1000）
    - _Requirements: 2.8, 10.6, 19.3_

- [x] 2. 实现交付物持久化存储 ArtifactRepository [P1]
  - [x] 2.1 实现 ArtifactRepository
    - 在 `com.yupi.yuaiagent.artifact` 下创建 `@Repository ArtifactRepository`，复用 AppointmentRepository 范式：`ObjectMapper + JavaTimeModule`、`ConcurrentHashMap<String, Artifact>`、`ReadWriteLock`、`@PostConstruct init()` 从文件加载、`@Value` 读取存储目录、`writerWithDefaultPrettyPrinter` 写盘
    - 实现 `save`（未指定 id 时生成 UUID；新建设置 createdAt/updatedAt，更新保留原 createdAt 并刷新 updatedAt）、`findById`、`findAll`、`updateStatus`
    - `init()` 中文件读取失败时记录错误日志并以空集合完成初始化；读取成功则加载且不记录错误日志
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6, 2.7, 1.5, 1.6, 3.4_

  - [ ]* 2.2 编写 ArtifactRepository 单元测试
    - 测试保存后从文件加载、并发读写、更新保留 createdAt、读取失败回退空集合等场景
    - _Requirements: 2.3, 2.4, 2.6, 2.7_

- [x] 3. 实现共享货架 ArtifactShelf [P1]
  - [x] 3.1 实现放货与读取（put / get）
    - 在 `com.yupi.yuaiagent.artifact` 下创建 `@Component ArtifactShelf`，注入 `ArtifactRepository`
    - 实现 `put(Artifact)`：作用域校验（TASK 必须有 chatId，USER_PROFILE 必须有 userId，否则返回 `PutResult.fail`），委托仓库保存并返回含 artifactId 的 `PutResult`；同一 artifactId 再次放货更新并刷新 updatedAt
    - 实现 `get(artifactId)`：返回 `Optional`，不存在返回空且不抛异常
    - 定义 `PutResult` record（success / artifactId / errorMessage / artifact，含 `ok` / `fail` 工厂方法）
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 6.1, 6.2, 6.4_

  - [x] 3.2 实现多条件查询（query）
    - 实现 `query(ArtifactQuery)`：按 userId / chatId / type / scope / status 做 AND 过滤，按 createdAt 倒序返回，无匹配返回空列表
    - 支持按 userId 查询 USER_PROFILE 作用域交付物，返回跨会话累积结果
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 6.3_

  - [x] 3.3 实现消费标记（markConsumed）
    - 实现 `markConsumed(artifactId)`：将 status 置为 CONSUMED 并刷新 updatedAt；id 不存在返回 false 不抛异常；幂等（重复调用最终状态一致）；标记后查询仍返回该交付物并保留 CONSUMED 状态
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [ ]* 3.4 编写 ArtifactShelf 单元测试
    - 覆盖放货返回 id、按 id 读取、作用域校验拒绝（TASK 缺 chatId）、多条件查询与倒序、消费标记幂等、查询空结果等
    - _Requirements: 3.1, 3.3, 4.4, 4.5, 4.6, 5.2, 5.4, 6.4_

- [x] 4. Checkpoint — 货架基础设施可用
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. 实现数据员工抽象与数据分析师 [P1]
  - [x] 5.1 创建数据员工辅助模型
    - 在 `com.yupi.yuaiagent.agent.data` 下创建 `AnalysisSource` 枚举（CONVERSATION / UPLOADED_DOCUMENT）、`ProductionContext` record（userId、chatId、source、memoryAgentType、documentContent）、`ProductionResult` record（含 `ok` / `fail` 工厂方法）、`AnalysisReport`（summary、keyFindings、metrics、recommendations）
    - _Requirements: 7.1, 8.1, 8.7_

  - [x] 5.2 实现 DataEmployeeAgent 抽象基类
    - 在 `com.yupi.yuaiagent.agent.data` 下创建抽象类 `DataEmployeeAgent`，注入 `ArtifactShelf`
    - 定义 `producerName()`、`doProduce(ProductionContext)` 抽象方法与 `final produce(ProductionContext)` 模板方法：加工失败不放货；成功时组装 Artifact（producer=producerName、scope 默认 TASK、status=READY）并通过货架放货
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x] 5.3 实现 DataAnalystAgent
    - 在 `com.yupi.yuaiagent.agent.data` 下创建 `DataAnalystAgent extends DataEmployeeAgent`，注入 `ChatModel`、`ChatMemoryManager`、`ArtifactShelf`，构建带 `MyLoggerAdvisor` 的 `ChatClient`
    - `producerName()` 返回 "数据分析师"；`doProduce` 按 source 解析输入（CONVERSATION 读取 chatId 对话历史，UPLOADED_DOCUMENT 读取上传文档内容），输入为空返回描述性错误且不产出；调用分析提示词产出结构化 JSON 报告（含 summary、keyFindings），并做合法报告 JSON 兜底，type=DATA_ANALYSIS_REPORT、status=READY
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [x] 5.4 在 AgentConfig 装配 DataAnalystAgent Bean
    - 在 `config/AgentConfig` 新增 `@Bean dataAnalystAgent(ChatModel, ChatMemoryManager, ArtifactShelf)`，与既有 `orchestratorAgent` Bean 风格一致
    - _Requirements: 7.1, 8.4_

  - [ ]* 5.5 编写 DataAnalystAgent 单元测试
    - mock ChatModel/ChatMemoryManager，验证空输入返回错误不放货、有效输入产出 READY 报告交付物且 producer 正确
    - _Requirements: 8.5, 8.6, 7.3_

- [x] 6. 实现用户画像数据模型与持久化 [P1]
  - [x] 6.1 创建 UserProfile 实体与枚举
    - 在 `com.yupi.yuaiagent.profile.model` 下创建 `UserProfile`（userId、communicationPreference、tonePreference、focusAreas 列表、knownBackground、historicalDemands 列表、createdAt、updatedAt）与 `CommunicationPreference` 枚举（CONCISE / DETAILED）
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

  - [ ]* 6.2 编写 UserProfile 序列化往返单元测试
    - 验证 UserProfile 序列化为 JSON 再反序列化得到等价对象，覆盖列表维度与时间字段
    - _Requirements: 10.5, 9.6_

  - [x] 6.3 实现 UserProfileRepository 及合并逻辑
    - 在 `com.yupi.yuaiagent.profile` 下创建 `@Repository UserProfileRepository`，复用 AppointmentRepository 范式（`ConcurrentHashMap<userId, UserProfile>` 保证每 userId 唯一一份），实现 `findByUserId`、`deleteByUserId`（删除并持久化）
    - 实现 `merge(userId, extracted)`：base 为空则新建并设 createdAt/updatedAt；否则标量维度新值非空则覆盖、列表维度追加去重（保序）、刷新 updatedAt 保证 updatedAt ≥ createdAt
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 9.1, 9.6, 11.2, 11.3, 11.4, 13.3_

  - [ ]* 6.4 编写 UserProfileRepository 合并单元测试
    - 覆盖首次写入、标量取新值、列表追加去重不重复、清空后持久化、updatedAt ≥ createdAt
    - _Requirements: 11.3, 11.4, 9.6, 13.3_

- [x] 7. 实现画像抽取、注入与服务编排 [P1]
  - [x] 7.1 实现 UserProfileExtractor
    - 在 `com.yupi.yuaiagent.profile` 下创建 `@Component UserProfileExtractor`，注入 `ChatModel` 构建 ChatClient，基于对话消息 LLM 抽取画像维度并返回 `UserProfile`
    - _Requirements: 11.1_

  - [x] 7.2 实现 ProfilePromptBuilder
    - 在 `com.yupi.yuaiagent.profile` 下创建 `@Component ProfilePromptBuilder`，`@Value` 读取 `profile.injection.max-chars`
    - `build(UserProfile)`：拼接沟通偏好（CONCISE→简洁 / DETAILED→详细）、语气偏好、已知背景、关注领域；超出上限时截断；画像为空返回空串
    - _Requirements: 12.2, 12.3, 19.1, 19.2, 19.3_

  - [x] 7.3 实现 UserProfileService
    - 在 `com.yupi.yuaiagent.profile` 下创建 `@Service UserProfileService`，注入 Repository / Extractor / PromptBuilder
    - 实现 `updateAsync(userId, conversation)`：`CompletableFuture.runAsync` 内抽取→合并→持久化，异常时记录日志并保留原画像不变；`get(userId)`；`clear(userId)`；`buildPromptInjection(userId)`（无画像返回空串）
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 12.4, 13.3_

  - [ ]* 7.4 编写 UserProfileService 单元测试
    - 覆盖抽取失败保留原画像、无画像注入返回空串、清空调用仓库删除
    - _Requirements: 11.5, 12.4, 13.3_

- [x] 8. 实现画像 REST 接口 ProfileController [P1]
  - [x] 8.1 实现 ProfileController
    - 在 `controller` 下创建 `ProfileController`（`@RequestMapping("/profile")`），注入 `UserProfileService`、`JwtUtil`
    - `GET /profile/me`：校验 JWT 取 userId，返回本人画像，无画像返回空结果；`DELETE /profile/me`：校验 JWT 清空本人画像并反馈；userId 始终取自 JWT，无有效 JWT 返回未授权
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5_

  - [ ]* 8.2 编写 ProfileController 单元测试
    - 覆盖无 JWT 返回未授权、有效 JWT 查看/清空、无画像返回空结果
    - _Requirements: 13.2, 13.4, 13.5_

- [ ] 9. 集成画像注入与对话结束触发 [P1]
  - [x] 9.1 扩展子 Agent 支持画像注入
    - 为子 Agent（ResumeAgent / NegotiationAgent / EscapeAgent / GeneralCareerAgent / ConsultationAgent）的 system prompt 构建增加可选 `profileInjection` 参数，通过 `ChatClient.prompt().system(baseSystem + injection)` 动态拼接而非固定 defaultSystem
    - _Requirements: 12.1, 12.2, 12.3_

  - [x] 9.2 在 OrchestratorAgent 集成画像注入与对话结束触发
    - 构造函数注入 `UserProfileService`；`chatStream`/`routeToAgent` 增加 `userId` 参数；路由前调用 `buildPromptInjection(userId)` 透传给子 Agent；在子 Agent 流的 `doOnComplete` 回调中调用 `updateAsync(userId, conversation)` 不阻塞 SSE 输出
    - 同步更新 `AgentConfig.orchestratorAgent` Bean 方法传入 `UserProfileService`
    - _Requirements: 11.1, 11.6, 12.1, 12.4_

  - [x] 9.3 AiController 透传 userId
    - `doChatWithOrchestrator` 将已解析的 userId 透传到 `orchestratorAgent.chatStream(message, chatId, userId)`，保持既有鉴权与会话归属逻辑不变
    - _Requirements: 12.1_

  - [ ]* 9.4 编写画像注入与触发集成测试
    - 验证对话结束后异步触发画像更新、有画像时 system prompt 含注入片段、无画像走默认 prompt
    - _Requirements: 11.1, 11.6, 12.1, 12.4_

- [x] 10. Checkpoint — P1 最小闭环完成
  - Ensure all tests pass, ask the user if questions arise.

### P2 阶段 — 下游取用 + 扩展数据员工

- [x] 11. 下游 Agent 自动取用货架交付物 [P2]
  - [x] 11.1 查询 READY 交付物并注入上下文
    - 在 `OrchestratorAgent` 处理对话请求时，按 userId + chatId 查询 status=READY 的相关交付物，将 content 注入子 Agent 上下文；无 READY 交付物时正常处理不依赖交付物
    - _Requirements: 14.1, 14.2, 14.4_

  - [x] 11.2 取用后标记消费
    - 交付物被取用后通过 `ArtifactShelf.markConsumed` 标记为 CONSUMED
    - _Requirements: 14.3_

  - [ ]* 11.3 编写下游取用集成测试
    - 覆盖有 READY 注入并标记消费、无 READY 正常处理
    - _Requirements: 14.2, 14.3, 14.4_

- [x] 12. 扩展数据员工 Agent [P2]
  - [x] 12.1 实现岗位辅导数据员工
    - 在 `com.yupi.yuaiagent.agent.data` 下创建 `CareerCoachAgent extends DataEmployeeAgent`，产出岗位辅导建议交付物，producer 设为标识名
    - _Requirements: 15.1, 15.4_

  - [x] 12.2 实现用户画像整理数据员工
    - 创建 `ProfileCuratorAgent extends DataEmployeeAgent`，将分散画像线索整理为结构化交付物，scope 设为 USER_PROFILE，producer 设为标识名
    - _Requirements: 15.2, 15.4, 15.5_

  - [x] 12.3 实现晋升路径规划数据员工
    - 创建 `PromotionPlannerAgent extends DataEmployeeAgent`，产出晋升路径规划交付物，producer 设为标识名
    - _Requirements: 15.3, 15.4_

  - [x] 12.4 在 AgentConfig 装配扩展数据员工 Bean
    - 为三个扩展数据员工新增 `@Bean` 装配，注入所需协作者
    - _Requirements: 15.1, 15.2, 15.3_

  - [ ]* 12.5 编写扩展数据员工单元测试
    - 验证各员工 producer 设置与 scope（画像整理为 USER_PROFILE）正确
    - _Requirements: 15.4, 15.5_

- [x] 13. Checkpoint — P2 完成
  - Ensure all tests pass, ask the user if questions arise.

### P3 阶段 — 推荐员 + 前端展示 + 个性化增强

- [x] 14. 实现学习资源推荐员 [P3]
  - [x] 14.1 实现学习资源推荐员数据员工
    - 创建 `LearningResourceRecommenderAgent extends DataEmployeeAgent`，注入 `UserProfileService`；读取 userId 画像关注领域作为推荐依据并产出学习资源推荐交付物；关注领域为空时回退到基于本次对话上下文生成推荐，并保证基于画像的尝试可优雅失败回退
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5_

  - [x] 14.2 在 AgentConfig 装配学习资源推荐员 Bean
    - 新增 `@Bean` 装配，注入 ChatModel / UserProfileService / ArtifactShelf
    - _Requirements: 16.1_

  - [ ]* 14.3 编写学习资源推荐员单元测试
    - 覆盖有关注领域基于画像推荐、关注领域为空回退对话上下文
    - _Requirements: 16.2, 16.4, 16.5_

- [x] 15. 实现管理员交付物接口 ArtifactController [P3]
  - [x] 15.1 实现 ArtifactSummary 与 ArtifactController
    - 创建 `ArtifactSummary`（artifactId、type、producer、title、status、createdAt）；创建 `ArtifactController`（`@RequestMapping("/artifact")`）：`GET /artifact/list` 按 userId/chatId/type 返回摘要列表，`GET /artifact/{artifactId}` 返回完整 content；校验管理员权限，无权限拒绝且不返回任何交付物数据
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5_

  - [ ]* 15.2 编写 ArtifactController 单元测试
    - 覆盖非管理员被拒绝且无数据返回、管理员查询摘要与查看详情
    - _Requirements: 17.4, 17.5_

- [x] 16. 实现前端用户画像入口 [P3]
  - [x] 16.1 前端查看与清空画像入口
    - 在前端新增画像 API 调用与视图入口：查看时调用 `GET /profile/me` 展示画像维度，清空时确认后调用 `DELETE /profile/me` 并反馈结果
    - _Requirements: 18.1, 18.2, 18.3, 18.4_

- [x] 17. 实现前端管理员交付物展示 [P3]
  - [x] 17.1 前端交付物列表与详情展示
    - 在前端管理界面新增 API 调用与视图：列表调用 `GET /artifact/list` 展示摘要字段，点击查看详情调用 `GET /artifact/{artifactId}` 展示完整 content
    - _Requirements: 17.1, 17.2, 17.3_

- [ ] 18. Final Checkpoint — 全部完成
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- 标注 `*` 的子任务为可选（单元测试 / 集成测试），可为加速 MVP 跳过；核心实现任务不可跳过。
- 每个任务标注了 P1/P2/P3 优先级与具体需求子条款编号，便于追溯。
- P1（任务 1–10）为完整最小闭环：货架基础设施 → 数据员工抽象 + 数据分析师 → 用户画像系统（含查看/清空与注入）。
- 持久化组件（ArtifactRepository、UserProfileRepository）严格复用现有 `AppointmentRepository` 风格（Jackson + JSON + 读写锁）。
- Checkpoint 任务用于阶段性验证；往返一致性与幂等性以边界用例单元测试覆盖（设计无 Correctness Properties 章节，故不含属性测试）。

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.4", "6.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "2.1", "5.1", "6.2", "6.3", "7.1", "7.2"] },
    { "id": 2, "tasks": ["2.2", "3.1", "6.4", "7.3"] },
    { "id": 3, "tasks": ["3.2", "7.4", "8.1"] },
    { "id": 4, "tasks": ["3.3", "8.2"] },
    { "id": 5, "tasks": ["3.4", "5.2", "9.1"] },
    { "id": 6, "tasks": ["5.3", "9.2"] },
    { "id": 7, "tasks": ["5.4", "5.5", "9.3", "11.1"] },
    { "id": 8, "tasks": ["9.4", "11.2", "12.1", "12.2", "12.3"] },
    { "id": 9, "tasks": ["11.3", "12.4", "12.5", "14.1", "15.1"] },
    { "id": 10, "tasks": ["14.2", "14.3", "15.2", "16.1", "17.1"] }
  ]
}
```
