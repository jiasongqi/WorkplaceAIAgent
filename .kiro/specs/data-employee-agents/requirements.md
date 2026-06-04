# Requirements Document

## Introduction

本功能为现有职场 AI Agent 系统（Java Spring Boot + Spring AI）引入三大能力：**共享交付物货架（Artifact Shelf）**、**数据员工 Agent** 与 **用户画像系统**。

- **共享交付物货架** 是一套多 Agent 协作的"黑板模式"基础设施：上游 Agent 产出交付物放到货架，下游 Agent 按需取用。货架支持两种作用域——按 userId 长期累积的用户画像作用域，以及按 chatId 会话级存储的任务交付物作用域。持久化风格与现有 AppointmentRepository 一致（Jackson + JSON + 读写锁）。
- **数据员工 Agent** 是一类专注于"数据加工"的 Agent。第一期落地 1 个：数据分析师，分析用户对话历史或用户上传文档，产出数据分析报告交付物放到货架。后续可扩展更多数据员工。
- **用户画像系统** 在每次对话结束后自动抽取并更新用户画像（沟通偏好、语气偏好、关注领域、已知背景、历史诉求），并将画像自动注入各 Agent 的 system prompt 实现个性化回答；用户可查看与清空自己的画像。

本需求文档按交付优先级标注：

- **P1（第一期，地基 + 最小闭环）**：共享货架基础设施 + 用户画像系统（含查看/清空） + 数据分析师 Agent（1 个）
- **P2**：更多数据员工（岗位辅导、用户画像整理独立 Agent、晋升路径规划） + 下游 Agent 自动取用货架交付物
- **P3**：学习资源推荐员 + 管理员前端完整展示 + 画像驱动的个性化增强

每个需求标题后以 `[P1]`/`[P2]`/`[P3]` 标注其交付优先级。

## Glossary

- **System**: 职场 AI Agent 系统整体，包含 OrchestratorAgent、各类专业 Agent、数据员工 Agent 及其配套设施
- **Artifact_Shelf**: 共享交付物货架，多 Agent 协作的黑板，负责交付物的放入、读取、查询与消费标记
- **Artifact**: 交付物实体，由某个 Agent 产出并放入 Artifact_Shelf，包含 artifactId、userId、chatId、type、producer、title、content、status、createdAt、updatedAt 等字段
- **Artifact_Repository**: 交付物文件持久化组件，使用 Jackson + JSON + 读写锁，存储风格与现有 AppointmentRepository 一致
- **Artifact_Scope**: 交付物作用域枚举，取值为 USER_PROFILE（按 userId 长期存储，跨会话累积）与 TASK（按 chatId 会话级存储）
- **Artifact_Status**: 交付物状态枚举，取值为 PENDING（生产中）、READY（可被消费）、CONSUMED（已被消费）
- **Data_Employee_Agent**: 数据员工 Agent 的统一抽象类型，专注于数据加工并产出交付物
- **Data_Analyst_Agent**: 数据分析师 Agent，第一期落地的数据员工，分析用户对话历史或上传文档并产出数据分析报告交付物
- **Analysis_Source**: 数据分析师的输入来源枚举，取值为 CONVERSATION（用户对话历史）与 UPLOADED_DOCUMENT（用户上传文档）
- **User_Profile**: 用户画像实体，按 userId 存储，包含沟通偏好、语气偏好、关注领域、已知背景、历史诉求等维度
- **User_Profile_Service**: 用户画像服务，负责画像的抽取、更新、查询、清空与注入
- **User_Profile_Repository**: 用户画像文件持久化组件，使用 Jackson + JSON + 读写锁
- **User_Profile_Extractor**: 用户画像抽取器，在对话结束后基于对话内容抽取画像维度
- **Communication_Preference**: 沟通偏好维度，取值为 CONCISE（简洁）或 DETAILED（详细）
- **OrchestratorAgent**: 主控 Agent，负责意图识别与路由分发，对话结束后触发画像更新
- **Profile_Controller**: 画像 REST 接口，供用户查看与清空自己的画像
- **Artifact_Controller**: 交付物 REST 接口，供管理员在前端查询与展示交付物
- **Admin**: 管理员角色，可在管理界面查看交付物
- **User**: 已登录的终端用户，由 JWT 中的 userId 标识

## Requirements

### Requirement 1: 交付物数据模型 [P1]

**User Story:** 作为系统开发者，我希望有统一的交付物数据模型，以便不同 Agent 能够以一致的结构产出和消费交付物。

#### Acceptance Criteria

1. THE Artifact SHALL 包含以下字段：artifactId、userId、chatId、type、producer、title、content、status、scope、createdAt、updatedAt。
2. THE Artifact SHALL 使用 Artifact_Status 枚举表示状态，取值为 PENDING、READY、CONSUMED。
3. THE Artifact SHALL 使用 Artifact_Scope 枚举表示作用域，取值为 USER_PROFILE、TASK。
4. THE Artifact 的 content 字段 SHALL 同时支持结构化 JSON 内容与纯文本内容。
5. WHEN 一个 Artifact 被创建且未显式指定 artifactId 时，THE Artifact_Shelf SHALL 生成全局唯一的 artifactId。
6. WHEN 一个 Artifact 被创建时，THE Artifact_Shelf SHALL 将 createdAt 与 updatedAt 设置为当前时间。

### Requirement 2: 交付物持久化存储 [P1]

**User Story:** 作为系统开发者，我希望交付物以文件方式持久化，以便服务重启后交付物不丢失，且实现风格与现有存储组件一致。

#### Acceptance Criteria

1. THE Artifact_Repository SHALL 使用 Jackson 将交付物序列化为 JSON 并持久化到文件。
2. THE Artifact_Repository SHALL 使用读写锁保护并发读写操作。
3. WHEN System 启动时，THE Artifact_Repository SHALL 从持久化文件加载已有交付物到内存。
4. WHEN 一个 Artifact 被放入或更新时，THE Artifact_Repository SHALL 将变更写入持久化文件。
5. FOR ALL 有效的 Artifact 对象，将其序列化为 JSON 后再反序列化 SHALL 得到与原对象等价的 Artifact 对象（往返一致性）。
6. IF 持久化文件读取失败，THEN THE Artifact_Repository SHALL 记录错误日志并以空交付物集合完成初始化，且记录日志与空集合初始化两个动作均须完成。
7. WHILE 持久化文件读取成功，THE Artifact_Repository SHALL 加载文件中已有的交付物且不记录读取错误日志。
8. THE Artifact_Repository 的存储目录 SHALL 通过配置项指定，默认值为 `./tmp/artifacts`。

### Requirement 3: 货架放货与读取能力 [P1]

**User Story:** 作为产出交付物的 Agent，我希望能够将交付物放入货架并按 ID 读取，以便实现 Agent 之间的交付物传递。

#### Acceptance Criteria

1. WHEN 一个 Agent 调用放货操作并提交 Artifact 时，THE Artifact_Shelf SHALL 保存该 Artifact 并返回包含 artifactId 的结果。
2. WHEN 一个 Agent 按 artifactId 请求读取交付物时，THE Artifact_Shelf SHALL 返回对应的 Artifact。
3. IF 按 artifactId 请求读取的交付物不存在，THEN THE Artifact_Shelf SHALL 返回空结果而非抛出异常。
4. WHEN 同一个 artifactId 被再次放货时，THE Artifact_Shelf SHALL 更新已存在的 Artifact 并将 updatedAt 设置为当前时间。

### Requirement 4: 货架查询能力 [P1]

**User Story:** 作为消费交付物的 Agent，我希望能够按 userId、chatId、type 查询交付物，以便按需获取上游产出的交付物。

#### Acceptance Criteria

1. WHEN 一个 Agent 按 userId 查询交付物时，THE Artifact_Shelf SHALL 返回该 userId 下的全部交付物。
2. WHEN 一个 Agent 按 chatId 查询交付物时，THE Artifact_Shelf SHALL 返回该 chatId 下的全部交付物。
3. WHEN 一个 Agent 按 type 查询交付物时，THE Artifact_Shelf SHALL 返回该 type 的全部交付物。
4. WHEN 一个 Agent 同时按 userId、chatId、type 中的多个条件查询时，THE Artifact_Shelf SHALL 返回同时满足所有给定条件的交付物。
5. THE Artifact_Shelf SHALL 按 createdAt 倒序返回查询结果。
6. WHEN 查询条件没有匹配的交付物时，THE Artifact_Shelf SHALL 返回空列表。

### Requirement 5: 交付物消费标记 [P1]

**User Story:** 作为消费交付物的 Agent，我希望能够将交付物标记为已消费，以便区分尚未取用与已取用的交付物。

#### Acceptance Criteria

1. WHEN 一个 Agent 请求将某 artifactId 标记为已消费时，THE Artifact_Shelf SHALL 将该 Artifact 的 status 更新为 CONSUMED 并更新 updatedAt。
2. IF 被标记消费的 artifactId 不存在，THEN THE Artifact_Shelf SHALL 返回操作失败结果而非抛出异常。
3. WHEN 一个 Artifact 的 status 被标记为 CONSUMED 后，THE Artifact_Shelf SHALL 在后续查询中仍返回该 Artifact 并保留其 CONSUMED 状态。
4. 对同一个 artifactId 重复执行标记消费操作 SHALL 产生与执行一次相同的最终状态（幂等性）。

### Requirement 6: 货架作用域隔离 [P1]

**User Story:** 作为用户，我希望我的会话级交付物与跨会话画像交付物相互隔离，以便交付物的生命周期符合其作用域语义。

#### Acceptance Criteria

1. WHERE Artifact 的 scope 为 USER_PROFILE，THE Artifact_Shelf SHALL 以 userId 作为该交付物的归属键长期保留。
2. WHERE Artifact 的 scope 为 TASK，THE Artifact_Shelf SHALL 以 chatId 作为该交付物的归属键进行会话级存储。
3. WHEN 一个 Agent 按 userId 查询 USER_PROFILE 作用域交付物时，THE Artifact_Shelf SHALL 返回跨会话累积的全部该作用域交付物。
4. IF 放货时 scope 为 TASK 但未提供 chatId，THEN THE Artifact_Shelf SHALL 拒绝该放货请求并返回参数错误结果。

### Requirement 7: 数据员工 Agent 抽象 [P1]

**User Story:** 作为系统开发者，我希望有统一的数据员工 Agent 抽象，以便后续以一致方式扩展更多数据员工。

#### Acceptance Criteria

1. THE System SHALL 提供 Data_Employee_Agent 抽象类型，定义数据员工产出交付物的统一执行入口。
2. WHEN 一个 Data_Employee_Agent 完成数据加工时，THE Data_Employee_Agent SHALL 将产出结果封装为 Artifact 并通过 Artifact_Shelf 放货。
3. THE Data_Employee_Agent 放货时 SHALL 将 producer 字段设置为该数据员工的标识名称。
4. WHERE 数据员工的产出为会话内任务交付物，THE Data_Employee_Agent SHALL 将 Artifact 的 scope 设置为 TASK。

### Requirement 8: 数据分析师 Agent [P1]

**User Story:** 作为用户，我希望有一个数据分析师能够分析我的对话数据或上传文档并产出分析报告，以便我获得对自身数据的洞察。

#### Acceptance Criteria

1. THE Data_Analyst_Agent SHALL 支持两种 Analysis_Source：CONVERSATION 与 UPLOADED_DOCUMENT。
2. WHERE Analysis_Source 为 CONVERSATION，THE Data_Analyst_Agent SHALL 读取指定 chatId 的对话历史作为分析输入。
3. WHERE Analysis_Source 为 UPLOADED_DOCUMENT，THE Data_Analyst_Agent SHALL 读取用户上传的文档内容作为分析输入。
4. WHEN Data_Analyst_Agent 完成分析时，THE Data_Analyst_Agent SHALL 产出 type 为数据分析报告的 Artifact 并通过 Artifact_Shelf 放货。
5. THE Data_Analyst_Agent 产出的 Artifact SHALL 在放货完成时将 status 设置为 READY。
6. IF 分析输入为空或无法获取，THEN THE Data_Analyst_Agent SHALL 返回描述性错误信息并不产出交付物。
7. THE Data_Analyst_Agent 产出的报告 content SHALL 采用结构化 JSON 格式，包含分析摘要与关键发现字段。

### Requirement 9: 用户画像数据模型 [P1]

**User Story:** 作为系统开发者，我希望有统一的用户画像数据模型，以便各 Agent 能够一致地读取与使用画像。

#### Acceptance Criteria

1. THE User_Profile SHALL 按 userId 唯一标识，且每个 userId 至多对应一份 User_Profile。
2. THE User_Profile SHALL 包含以下维度：沟通偏好、语气偏好、关注领域、已知背景、历史诉求。
3. THE User_Profile 的沟通偏好 SHALL 使用 Communication_Preference 枚举表示，取值为 CONCISE 或 DETAILED。
4. THE User_Profile 的关注领域与历史诉求 SHALL 以列表形式存储多个条目。
5. THE User_Profile SHALL 包含 createdAt 与 updatedAt 时间字段。
6. THE User_Profile SHALL 保证 updatedAt 大于或等于 createdAt。

### Requirement 10: 用户画像持久化存储 [P1]

**User Story:** 作为用户，我希望我的画像被持久化保存，以便跨会话累积并在服务重启后仍然有效。

#### Acceptance Criteria

1. THE User_Profile_Repository SHALL 使用 Jackson 将用户画像序列化为 JSON 并持久化到文件。
2. THE User_Profile_Repository SHALL 使用读写锁保护并发读写操作。
3. WHEN System 启动时，THE User_Profile_Repository SHALL 从持久化文件加载已有画像到内存。
4. WHEN 一个 User_Profile 被更新或清空时，THE User_Profile_Repository SHALL 将变更写入持久化文件。
5. FOR ALL 有效的 User_Profile 对象，将其序列化为 JSON 后再反序列化 SHALL 得到与原对象等价的 User_Profile 对象（往返一致性）。
6. THE User_Profile_Repository 的存储目录 SHALL 通过配置项指定，默认值为 `./tmp/user-profiles`。

### Requirement 11: 对话结束后自动更新画像 [P1]

**User Story:** 作为用户，我希望系统在每次对话结束后自动更新我的画像，以便无需手动维护即可获得个性化体验。

#### Acceptance Criteria

1. WHEN 一次对话结束时，THE System SHALL 触发 User_Profile_Extractor 基于本次对话内容抽取画像维度。
2. WHEN User_Profile_Extractor 完成抽取时，THE User_Profile_Service SHALL 将抽取结果合并到该 userId 的 User_Profile 中并更新 updatedAt。
3. WHEN 抽取到的画像维度与已有画像存在冲突时，THE User_Profile_Service SHALL 以本次抽取的较新值更新对应维度。
4. WHEN 抽取到新的关注领域或历史诉求条目时，THE User_Profile_Service SHALL 将新条目累积追加到对应列表且不产生重复条目。
5. IF 画像抽取过程失败，THEN THE System SHALL 记录错误日志并保留该 userId 已有的 User_Profile 不变。
6. WHILE 画像更新正在后台执行，THE System SHALL 不阻塞向用户返回本次对话响应。

### Requirement 12: 画像注入 Agent 提示词 [P1]

**User Story:** 作为用户，我希望各 Agent 按我的习惯定制回答，以便获得符合我偏好的沟通体验。

#### Acceptance Criteria

1. WHEN System 处理某 userId 的对话请求时，THE System SHALL 读取该 userId 的 User_Profile 并将其注入所调用 Agent 的 system prompt。
2. WHERE User_Profile 的沟通偏好为 CONCISE，THE System SHALL 在 system prompt 中指示 Agent 以简洁方式回答。
3. WHERE User_Profile 的沟通偏好为 DETAILED，THE System SHALL 在 system prompt 中指示 Agent 以详细方式回答。
4. IF 某 userId 尚无 User_Profile，THEN THE System SHALL 使用不含画像信息的默认 system prompt 处理请求。

### Requirement 13: 用户查看与清空画像 [P1]

**User Story:** 作为用户，我希望能够查看并清空我的画像，以便了解系统对我的认知并保护隐私。

#### Acceptance Criteria

1. WHEN User 通过 Profile_Controller 请求查看画像时，THE System SHALL 返回该 User 对应 userId 的 User_Profile。
2. IF User 显式请求查看画像但尚无 User_Profile，THEN THE System SHALL 返回空画像结果。
3. WHEN User 通过 Profile_Controller 请求清空画像时，THE User_Profile_Service SHALL 删除该 userId 对应的 User_Profile 并将删除结果持久化。
4. THE Profile_Controller SHALL 对所有接口请求校验 JWT，并仅允许 User 查看与清空与其 userId 匹配的画像。
5. IF 任一 Profile_Controller 请求未携带有效 JWT，THEN THE Profile_Controller SHALL 拒绝请求并返回未授权结果。

### Requirement 14: 下游 Agent 自动取用交付物 [P2]

**User Story:** 作为用户，我希望专业 Agent 能够自动取用货架上的相关交付物，以便回答更贴合我已有的分析结果与画像。

#### Acceptance Criteria

1. WHEN 一个专业 Agent 处理对话请求时，THE System SHALL 按 userId 与 chatId 查询货架中状态为 READY 的相关交付物。
2. WHEN 查询到状态为 READY 的相关交付物时，THE System SHALL 将交付物内容注入该 Agent 的上下文供其取用。
3. WHEN 一个交付物被某 Agent 取用后，THE System SHALL 通过 Artifact_Shelf 将该交付物标记为 CONSUMED。
4. WHERE 货架中无状态为 READY 的相关交付物，THE Agent SHALL 在不依赖交付物的情况下正常处理请求。

### Requirement 15: 扩展数据员工 Agent [P2]

**User Story:** 作为用户，我希望系统提供更多数据员工，以便覆盖岗位辅导、画像整理与晋升规划等场景。

#### Acceptance Criteria

1. THE System SHALL 提供岗位辅导数据员工，产出岗位辅导建议交付物并放入 Artifact_Shelf。
2. THE System SHALL 提供独立的用户画像整理数据员工，将分散的画像线索整理为结构化的 USER_PROFILE 作用域交付物。
3. THE System SHALL 提供晋升路径规划数据员工，产出晋升路径规划交付物并放入 Artifact_Shelf。
4. WHEN 任一扩展数据员工完成加工时，THE 对应 Data_Employee_Agent SHALL 将 producer 字段设置为该数据员工的标识名称，且该设置独立于交付物是否已放入货架。
5. WHERE 用户画像整理数据员工产出跨会话画像交付物，THE Data_Employee_Agent SHALL 将 Artifact 的 scope 设置为 USER_PROFILE。

### Requirement 16: 学习资源推荐员 [P3]

**User Story:** 作为用户，我希望有学习资源推荐员根据我的画像与关注领域推荐学习资源，以便提升职业能力。

#### Acceptance Criteria

1. THE System SHALL 提供学习资源推荐员数据员工。
2. WHEN 学习资源推荐员执行推荐时，THE 学习资源推荐员 SHALL 读取该 userId 的 User_Profile 关注领域作为推荐依据。
3. WHEN 学习资源推荐员完成推荐时，THE 学习资源推荐员 SHALL 产出学习资源推荐交付物并放入 Artifact_Shelf。
4. IF 该 userId 的 User_Profile 关注领域为空，THEN THE 学习资源推荐员 SHALL 基于本次对话上下文生成推荐而非依赖画像。
5. IF 学习资源推荐员在关注领域为空时仍尝试基于画像推荐，THEN THE 学习资源推荐员 SHALL 允许该尝试优雅失败并回退到基于对话上下文的推荐。

### Requirement 17: 管理员前端交付物展示 [P3]

**User Story:** 作为管理员，我希望在前端管理界面查看交付物，以便监控各数据员工的产出。

#### Acceptance Criteria

1. THE Artifact_Controller SHALL 提供按 userId、chatId、type 查询交付物的接口供管理界面调用。
2. WHEN Admin 在管理界面请求交付物列表时，THE System SHALL 返回交付物的 artifactId、type、producer、title、status、createdAt 字段供展示。
3. WHEN Admin 在管理界面请求查看某交付物详情时，THE System SHALL 返回该 Artifact 的完整 content。
4. THE Artifact_Controller SHALL 校验请求来自具备管理员权限的调用方。
5. IF 请求查看交付物的调用方不具备管理员权限，THEN THE Artifact_Controller SHALL 拒绝请求并确保不返回任何交付物数据。

### Requirement 18: 用户画像前端入口 [P3]

**User Story:** 作为用户，我希望在前端有查看与清空画像的入口，以便方便地管理我的画像。

#### Acceptance Criteria

1. THE System SHALL 在前端提供 User 查看自己画像的入口。
2. WHEN User 在前端触发查看画像时，THE System SHALL 调用 Profile_Controller 并展示该 User 的画像维度。
3. THE System SHALL 在前端提供 User 清空自己画像的入口。
4. WHEN User 在前端确认清空画像时，THE System SHALL 调用 Profile_Controller 清空画像并向 User 反馈清空结果。

### Requirement 19: 画像驱动的个性化增强 [P3]

**User Story:** 作为用户，我希望系统基于画像进一步增强个性化，以便回答更贴合我的语气偏好与已知背景。

#### Acceptance Criteria

1. WHERE User_Profile 包含语气偏好，THE System SHALL 在 Agent 的 system prompt 中按该语气偏好定制回答风格。
2. WHERE User_Profile 包含已知背景，THE System SHALL 在 Agent 的 system prompt 中纳入该背景以减少重复追问。
3. WHEN System 注入画像驱动的个性化提示词时，THE System SHALL 在单次请求内将画像注入控制在配置的字符上限内，默认上限为 1000 字符。
