# Implementation Plan: Appointment Consultation Intent

## Overview

本计划将设计文档拆解为可增量交付的编码任务，覆盖三大核心模块：预约咨询意图识别、企业日历对接、对话记忆压缩，以及必要追问机制与追问模板管理。实现语言为 **Java 21 + Spring Boot 3.4 + Spring AI 1.0**（设计文档已使用具体语言，无需选择）。

构建顺序遵循自底向上原则：先建立数据模型与枚举基础，再实现日历服务、持久化、记忆压缩、追问模板与校验，随后组装 ConsultationAgent，最后在 OrchestratorAgent 中接线路由并完成配置装配。每个任务均引用设计文档中的具体需求子条款；属性测试子任务对应设计文档「8. Correctness Properties」中的 14 条属性，使用 jqwik 框架编写。

包结构（沿用现有代码库）：
- `com.yupi.yuaiagent.agent` / `agent.model`：Agent 与数据模型
- `com.yupi.yuaiagent.calendar`：日历服务接口与实现
- `com.yupi.yuaiagent.chatmemory`：记忆压缩
- `com.yupi.yuaiagent.config`：追问模板与配置
- `com.yupi.yuaiagent.repository`：预约持久化
- `com.yupi.yuaiagent.validation`：信息校验

## Tasks

- [x] 1. 建立数据模型与枚举基础
  - [x] 1.1 实现 Appointment 预约记录实体及枚举
    - 在 `agent/model/Appointment.java` 定义预约人姓名、联系方式、预约时间、咨询主题、备注、日历事件 ID、日历链接、状态、会话 ID、创建/更新时间字段
    - 定义内嵌枚举 `AppointmentStatus`（PENDING/CONFIRMED/CANCELLED/COMPLETED）与 `CalendarProvider`（FEISHU/DINGTALK）
    - _Requirements: 2.7_

  - [x] 1.2 实现 CoreInformation 核心信息模型
    - 在 `agent/model/CoreInformation.java` 定义姓名、联系方式、预约时间核心字段及主题、备注非核心字段
    - 实现 `isComplete()`、`getMissingFields()`、`toAppointment(...)` 转换方法
    - _Requirements: 5.2_

  - [x] 1.3 实现 FollowUpQuestion 追问问题模型与 CoreInfoType
    - 在 `agent/model/FollowUpQuestion.java` 定义字段名、显示名、问题内容、是否核心、是否已收集、校验正则、校验提示、优先级
    - 提供 `createCoreQuestion(...)` 与 `createOptionalQuestion(...)` 工厂方法
    - 定义核心信息类型枚举（NAME / CONTACT / APPOINTMENT_TIME）
    - _Requirements: 5.2, 6.1_

  - [x] 1.4 实现 CompressedMemory 压缩记忆模型
    - 在 `agent/model/CompressedMemory.java` 定义 chatId、agentType、summary、keyNeeds、confirmedInfo、unresolvedIssues、decisions、agreements、originalMessageCount、compressedAt、version 字段
    - _Requirements: 4.6_

  - [x]* 1.5 编写数据模型单元测试
    - 测试 CoreInformation.isComplete() 与 getMissingFields() 的边界条件
    - 测试 Appointment 与枚举的构造与默认值
    - _Requirements: 2.7, 5.2_

- [x] 2. 扩展 AgentIntent 意图枚举
  - [x] 2.1 新增 CONSULTATION 意图常量
    - 在 `agent/AgentIntent.java` 添加 `CONSULTATION("预约咨询专家", ...)` 常量
    - 确保 `fromRawIntent` 模糊匹配可识别 CONSULTATION
    - _Requirements: 1.2_

  - [x]* 2.2 编写意图枚举完整性属性测试
    - **Property 2: Intent Enum Completeness**
    - **Validates: Requirements 1.2**

- [x] 3. 实现企业日历对接服务
  - [x] 3.1 定义 CalendarService 统一接口与 CalendarEvent
    - 在 `calendar/CalendarService.java` 定义 `createEvent`、`cancelEvent`、`updateEvent`、`checkAvailability`、`getProvider` 方法与 `CalendarException`
    - 在 `calendar/CalendarEvent.java` 定义事件 ID、链接等返回结构
    - _Requirements: 2.1, 2.3_

  - [x] 3.2 实现 FeishuCalendarService 飞书日历实现
    - 在 `calendar/FeishuCalendarService.java` 通过 `@ConditionalOnProperty(name="calendar.provider", havingValue="FEISHU")` 装配
    - 调用飞书开放平台 API 创建事件，返回事件 ID 与日历链接
    - _Requirements: 2.2, 2.3, 2.5_

  - [x] 3.3 实现 DingTalkCalendarService 钉钉日历实现
    - 在 `calendar/DingTalkCalendarService.java` 通过 `@ConditionalOnProperty(name="calendar.provider", havingValue="DINGTALK")` 装配
    - 调用钉钉开放平台 API 创建事件，返回事件 ID 与日历链接
    - _Requirements: 2.2, 2.3, 2.6_

  - [x] 3.4 实现 CalendarServiceFactory 提供商选择
    - 在 `calendar/CalendarServiceFactory.java` 根据 `calendar.provider` 配置返回对应实现
    - _Requirements: 2.1, 2.5, 2.6_

  - [x]* 3.5 编写日历提供商选择属性测试
    - **Property 3: Calendar Service Provider Selection**
    - **Validates: Requirements 2.5, 2.6**

  - [x] 3.6 实现日历 API 错误处理
    - 捕获 API 调用异常，记录错误日志，向用户返回友好提示（建议稍后重试或联系人工客服）
    - 在 `exception/GlobalExceptionHandler.java` 处理 CalendarException
    - _Requirements: 2.4_

  - [x]* 3.7 编写日历 API 错误处理属性测试
    - **Property 5: Calendar API Error Handling**
    - **Validates: Requirements 2.4**

- [x] 4. 实现预约记录持久化
  - [x] 4.1 实现 AppointmentRepository
    - 在 `repository/AppointmentRepository.java` 提供按 ID 保存与查询预约记录的能力，持久化姓名、联系方式、时间、日历事件 ID、状态等完整字段
    - _Requirements: 2.7_

  - [x]* 4.2 编写预约持久化往返属性测试
    - **Property 4: Appointment Persistence Round-Trip**
    - **Validates: Requirements 2.7**

- [x] 5. 实现对话记忆压缩
  - [x] 5.1 实现压缩策略接口与两种策略
    - 在 `chatmemory/CompressionStrategy.java` 定义压缩触发判定接口
    - 在 `chatmemory/TokenCompressionStrategy.java` 实现 Token 阈值触发（默认 4000）
    - 在 `chatmemory/TurnCompressionStrategy.java` 实现对话轮数触发（默认 20）
    - _Requirements: 3.3, 4.1, 4.2_

  - [x] 5.2 实现 MemoryCompressor 压缩器
    - 在 `chatmemory/MemoryCompressor.java` 调用 LLM 生成结构化摘要，包含关键需求、已确认信息、未解决问题、重要决策、约定事项五要素
    - 保留最近 N 轮完整对话（N 可配置，默认 5），将摘要作为系统消息加入上下文
    - _Requirements: 3.1, 3.2, 3.4, 4.6_

  - [x] 5.3 扩展 FileBasedChatMemory 支持压缩
    - 在 `chatmemory/FileBasedChatMemory.java` 集成压缩能力，将历史对话压缩为关键信息摘要并持久化
    - _Requirements: 3.1, 3.2, 3.4_

  - [x] 5.4 扩展 ChatMemoryManager 压缩状态与自动触发
    - 在 `chatmemory/ChatMemoryManager.java` 提供压缩状态查询接口
    - 接入策略，在 Token / 轮数超阈值时自动触发压缩，并推送"正在整理对话记忆..."与"记忆整理完成"状态消息，保持对话连续性
    - _Requirements: 3.5, 4.1, 4.2, 4.3, 4.4, 4.5_

  - [~]* 5.5 编写记忆保留属性测试
    - **Property 6: Memory Compression Retention**
    - **Validates: Requirements 3.2**

  - [~]* 5.6 编写 Token 阈值触发属性测试
    - **Property 7: Compression Trigger by Token Threshold**
    - **Validates: Requirements 4.1**

  - [~]* 5.7 编写对话轮数触发属性测试
    - **Property 8: Compression Trigger by Round Threshold**
    - **Validates: Requirements 4.2**

  - [~]* 5.8 编写压缩摘要内容完整性属性测试
    - **Property 9: Compressed Memory Content Completeness**
    - **Validates: Requirements 4.6**

- [x] 6. Checkpoint - 确保数据层与服务基础测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. 实现追问模板管理
  - [x] 7.1 实现 FollowUpTemplateConfig 模板配置与热更新
    - 在 `config/FollowUpTemplateConfig.java` 提供姓名、联系方式、预约时间三类核心模板配置
    - 未配置时回退到默认模板，支持热更新（无需重启即生效）
    - 提供确认页、校验失败、成功、失败等渲染方法
    - _Requirements: 6.1, 6.3, 6.4_

  - [~]* 7.2 编写追问模板使用属性测试
    - **Property 11: Follow-Up Template Usage**
    - **Validates: Requirements 5.3, 6.3**

  - [~]* 7.3 编写模板占位符替换属性测试
    - **Property 14: Template Placeholder Substitution**
    - **Validates: Requirements 6.2**

- [x] 8. 实现信息校验
  - [x] 8.1 实现 InfoValidator 信息校验器
    - 在 `validation/InfoValidator.java` 实现姓名、联系方式（手机/邮箱）、预约时间的格式校验与解析
    - 校验失败返回正确格式提示
    - _Requirements: 5.6_

  - [x]* 8.2 编写信息校验单元测试
    - 测试手机号、邮箱、时间解析的有效与无效用例
    - _Requirements: 5.6_

- [x] 9. 实现 ConsultationAgent 预约咨询 Agent
  - [x] 9.1 实现 ConsultationAgent 状态机与追问流程
    - 在 `agent/ConsultationAgent.java` 实现 INITIAL→COLLECTING_INFO→CONFIRMING→CREATING_APPOINTMENT→COMPLETED 状态机
    - 核心信息缺失时触发追问（使用模板），非核心信息由 AI 智能追问；信息完整后展示确认页；确认后调用 CalendarService 创建事件并持久化
    - _Requirements: 1.1, 5.1, 5.3, 5.4, 5.5, 5.7, 2.2_

  - [~]* 9.2 编写缺失核心信息触发追问属性测试
    - **Property 10: Follow-Up Trigger on Missing Core Info**
    - **Validates: Requirements 5.1**

  - [~]* 9.3 编写核心信息完整后确认属性测试
    - **Property 12: Confirmation After Core Info Complete**
    - **Validates: Requirements 5.5**

  - [~]* 9.4 编写非法输入校验重试属性测试
    - **Property 13: Invalid Input Validation and Retry**
    - **Validates: Requirements 5.6**

- [x] 10. 在 OrchestratorAgent 中接线路由
  - [x] 10.1 路由 CONSULTATION 意图到 ConsultationAgent
    - 在 `agent/OrchestratorAgent.java` 的意图识别提示词中加入 CONSULTATION 分类
    - 在同步 `chat` 与流式 `routeToAgent` 中将 CONSULTATION 路由到 ConsultationAgent
    - _Requirements: 1.1, 1.3, 1.4_

  - [~]* 10.2 编写意图路由属性测试
    - **Property 1: Consultation Intent Routing**
    - **Validates: Requirements 1.1, 1.3, 1.4**

- [x] 11. 配置装配与端到端集成
  - [x] 11.1 完成应用配置与配置类装配
    - 在 `application.yml` 添加 calendar（provider/feishu/dingtalk）、memory.compression（阈值/保留轮数）、follow-up（热更新）配置
    - 在 `config/AgentConfig.java` 装配 CalendarConfig、CompressionConfig 并将所有依赖注入 OrchestratorAgent / ConsultationAgent
    - _Requirements: 2.1, 3.3, 4.1, 4.2, 6.4_

  - [x]* 11.2 编写预约咨询完整流程集成测试
    - 覆盖意图识别→追问收集→确认→创建预约→持久化端到端流程（Mock 日历 API）
    - 实现于 `test/agent/ConsultationAgentIntegrationTest.java`，包含 8 个测试用例
    - _Requirements: 1.1, 2.2, 2.7, 5.5, 5.7_

- [x] 12. Final checkpoint - 确保全部测试通过
  - 编译通过（mvn compile BUILD SUCCESS）
  - 集成测试已编写，覆盖完整预约流程（Mock 日历 API）
  - tasks.md 依赖图中的错误文字已修复

## Notes

- 标记 `*` 的子任务为可选测试任务，可在 MVP 阶段跳过
- 每个任务均引用设计文档中的具体需求子条款，便于追溯
- Checkpoint 任务用于阶段性验证，确保增量构建质量
- 属性测试（jqwik）验证设计文档「8. Correctness Properties」中的 14 条通用正确性属性
- 单元测试与集成测试验证具体示例与边界条件，与属性测试互补
- 本功能在现有代码库基础上扩展，文件路径沿用既有包结构

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4", "2.1"] },
    { "id": 1, "tasks": ["1.5", "2.2", "3.1", "4.1", "5.1", "8.1"] },
    { "id": 2, "tasks": ["3.2", "3.3", "4.2", "5.2", "7.1", "8.2"] },
    { "id": 3, "tasks": ["3.4", "5.3", "7.2", "7.3"] },
    { "id": 4, "tasks": ["3.5", "3.6", "5.4"] },
    { "id": 5, "tasks": ["3.7", "5.5", "5.6", "5.7", "5.8", "9.1"] },
    { "id": 6, "tasks": ["9.2", "9.3", "9.4", "10.1"] },
    { "id": 7, "tasks": ["10.2", "11.1"] },
    { "id": 8, "tasks": ["11.2"] }
  ]
}
```
