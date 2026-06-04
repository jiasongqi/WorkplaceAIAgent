# Requirements Document

## Introduction

本功能旨在增强职场 AI Agent 系统的预约咨询能力，包含预约咨询意图识别、企业日历对接、对话记忆管理优化三大核心模块。系统需识别用户预约咨询请求，通过追问机制收集必要信息，对接飞书/钉钉日历 API 创建实际预约，并优化长短期记忆管理机制以提升对话质量。

## Glossary

- **System**: 职场 AI Agent 系统，包含 OrchestratorAgent、各类专业 Agent 及其配套设施
- **OrchestratorAgent**: 主控 Agent，负责意图识别和路由分发
- **ConsultationAgent**: 新增预约咨询 Agent，处理预约咨询请求
- **AgentIntent**: 意图枚举类型，用于标准化意图识别结果
- **ChatMemoryManager**: 对话记忆管理器，统一管理各 Agent 的 ChatMemory 实例
- **FileBasedChatMemory**: 基于文件持久化的对话记忆实现
- **CompressedMemory**: 压缩后的对话记忆，保留关键信息摘要
- **CalendarProvider**: 日历服务提供商枚举（FEISHU、DINGTALK）
- **CalendarService**: 日历服务接口，对接企业日历 API
- **Appointment**: 预约记录实体，包含预约人信息、时间、状态等
- **FollowUpQuestion**: 追问问题实体，用于引导用户补充信息
- **CoreInformation**: 核心预约信息，包含姓名、联系方式、预约时间

## Requirements

### Requirement 1: 预约咨询意图识别

**User Story:** 作为用户，我希望系统能够识别我的预约咨询请求，以便我可以便捷地预约咨询服务。

#### Acceptance Criteria

1. WHEN 用户发送包含预约咨询意图的消息时，THE System SHALL 将该消息路由到 ConsultationAgent 进行处理。
2. THE AgentIntent 枚举 SHALL 包含 CONSULTATION 意图类型，描述为"预约咨询专家"。
3. WHEN OrchestratorAgent 执行意图识别时，THE System SHALL 能够正确识别包含"预约"、"咨询"、"预约咨询"等关键词的用户意图为 CONSULTATION。
4. WHEN 意图被识别为 CONSULTATION 时，THE System SHALL 调用 ConsultationAgent 处理用户请求。

### Requirement 2: 企业日历对接

**User Story:** 作为用户，我希望系统能够在我确认预约后自动创建日历事件，以便我可以在企业日历中查看预约安排。

#### Acceptance Criteria

1. THE System SHALL 提供统一的 CalendarService 接口，支持飞书日历 API 和钉钉日历 API 两种实现。
2. WHEN 用户确认预约信息后，THE System SHALL 调用配置的 CalendarService 在对应企业日历中创建预约事件。
3. WHEN 日历事件创建成功时，THE System SHALL 返回事件 ID 和日历链接给用户。
4. IF 日历 API 调用失败，THEN THE System SHALL 记录错误日志并向用户返回友好的错误提示，建议用户稍后重试或联系人工客服。
5. WHERE CalendarProvider 配置为 FEISHU，THE System SHALL 使用飞书开放平台 API 创建日历事件。
6. WHERE CalendarProvider 配置为 DINGTALK，THE System SHALL 使用钉钉开放平台 API 创建日历事件。
7. THE System SHALL 将预约记录持久化存储，包含预约人姓名、联系方式、预约时间、日历事件 ID、预约状态等信息。

### Requirement 3: 短期记忆管理增强

**User Story:** 作为用户，我希望系统能够高效管理对话记忆，以便在长时间对话中保持响应速度和上下文理解能力。

#### Acceptance Criteria

1. THE FileBasedChatMemory SHALL 支持对话记忆的压缩功能，将历史对话压缩为关键信息摘要。
2. WHEN 对话记忆压缩被触发时，THE System SHALL 保留最近 N 轮对话的完整内容，N 值可通过配置指定，默认值为 5。
3. THE System SHALL 提供压缩策略接口，支持基于 Token 阈值触发压缩和基于对话轮数触发压缩两种策略。
4. WHEN 压缩完成后，THE System SHALL 将压缩后的摘要作为系统消息添加到对话上下文中，保留关键信息。
5. THE ChatMemoryManager SHALL 提供压缩状态查询接口，返回当前会话的记忆压缩状态。

### Requirement 4: 长短期记忆压缩自动化

**User Story:** 作为用户，我希望系统能够自动整理对话记忆，以便我无需手动操作即可享受高效的对话体验。

#### Acceptance Criteria

1. WHEN 对话 Token 数量超过配置的阈值时，THE System SHALL 自动触发记忆压缩，阈值默认为 4000 Tokens。
2. WHEN 对话轮数超过配置的阈值时，THE System SHALL 自动触发记忆压缩，阈值默认为 20 轮。
3. WHEN 记忆压缩开始执行时，THE System SHALL 向前端推送"正在整理对话记忆..."状态消息。
4. WHEN 记忆压缩完成时，THE System SHALL 向前端推送压缩完成状态消息，并继续响应用户请求。
5. THE System SHALL 在压缩过程中保持对话连续性，确保用户不会感知到服务中断。
6. THE 压缩后的记忆摘要 SHALL 包含：用户关键需求、已确认的信息、未解决的问题、重要决策和约定。

### Requirement 5: 必要追问机制

**User Story:** 作为用户，我希望在预约咨询时系统能够引导我提供必要信息，以便预约能够准确创建。

#### Acceptance Criteria

1. WHEN 用户发起预约咨询请求但未提供核心信息时，THE ConsultationAgent SHALL 触发追问流程。
2. THE 核心信息 SHALL 包含：预约人姓名、联系方式、期望预约时间。
3. WHEN 追问核心信息时，THE System SHALL 使用预定义的追问模板，确保信息收集的准确性和一致性。
4. WHEN 收集非核心信息（如咨询主题、备注等）时，THE System SHALL 由 AI 根据对话上下文智能生成追问问题。
5. WHEN 所有核心信息收集完成后，THE System SHALL 向用户展示预约信息确认页面。
6. IF 用户在追问过程中提供的信息格式不正确，THEN THE System SHALL 提示用户正确的信息格式并重新追问。
7. WHEN 用户确认预约信息后，THE System SHALL 调用日历服务创建预约事件。

### Requirement 6: 追问模板管理

**User Story:** 作为系统管理员，我希望能够配置追问模板，以便适配不同的业务场景和语言风格。

#### Acceptance Criteria

1. THE System SHALL 提供核心信息追问模板配置，支持姓名、联系方式、预约时间三类模板。
2. THE 追问模板 SHALL 支持变量占位符，便于动态插入上下文信息。
3. WHERE 追问模板未配置，THE System SHALL 使用默认模板进行追问。
4. THE System SHALL 支持追问模板的热更新，无需重启服务即可生效。
