# 未完成任务汇总

> 生成时间：2026-06-01  
> 说明：`[~]*` 表示可选测试任务（MVP 阶段可跳过）；`[ ]` 表示必须完成的核心任务。

---

## 一、appointment-consultation-intent（预约咨询意图）

**整体进度：全部核心任务已完成，仅剩可选属性测试。**

| 任务 | 类型 | 说明 |
|------|------|------|
| 5.5 | 可选测试 | Property 6：记忆保留属性测试（Req 3.2） |
| 5.6 | 可选测试 | Property 7：Token 阈值触发属性测试（Req 4.1） |
| 5.7 | 可选测试 | Property 8：对话轮数触发属性测试（Req 4.2） |
| 5.8 | 可选测试 | Property 9：压缩摘要内容完整性属性测试（Req 4.6） |
| 7.2 | 可选测试 | Property 11：追问模板使用属性测试（Req 5.3, 6.3） |
| 7.3 | 可选测试 | Property 14：模板占位符替换属性测试（Req 6.2） |
| 9.2 | 可选测试 | Property 10：缺失核心信息触发追问属性测试（Req 5.1） |
| 9.3 | 可选测试 | Property 12：核心信息完整后确认属性测试（Req 5.5） |
| 9.4 | 可选测试 | Property 13：非法输入校验重试属性测试（Req 5.6） |
| 10.2 | 可选测试 | Property 1：意图路由属性测试（Req 1.1, 1.3, 1.4） |

---

## 二、data-employee-agents（数据员工 Agent）

**整体进度：所有核心实现任务已完成，仅剩可选测试任务，以及任务 9（集成画像注入）中的一个可选集成测试。**

| 任务 | 类型 | 说明 |
|------|------|------|
| 1.2 | 可选测试 | Artifact 序列化往返单元测试（Req 1.4, 2.5） |
| 2.2 | 可选测试 | ArtifactRepository 单元测试（Req 2.3, 2.4, 2.6, 2.7） |
| 3.4 | 可选测试 | ArtifactShelf 单元测试（Req 3.1, 3.3, 4.4–4.6, 5.2, 5.4, 6.4） |
| 5.5 | 可选测试 | DataAnalystAgent 单元测试（Req 8.5, 8.6, 7.3） |
| 6.2 | 可选测试 | UserProfile 序列化往返单元测试（Req 10.5, 9.6） |
| 6.4 | 可选测试 | UserProfileRepository 合并单元测试（Req 11.3, 11.4, 9.6, 13.3） |
| 7.4 | 可选测试 | UserProfileService 单元测试（Req 11.5, 12.4, 13.3） |
| 8.2 | 可选测试 | ProfileController 单元测试（Req 13.2, 13.4, 13.5） |
| 9.4 | 可选测试 | 画像注入与触发集成测试（Req 11.1, 11.6, 12.1, 12.4） |
| 11.3 | 可选测试 | 下游取用集成测试（Req 14.2, 14.3, 14.4） |
| 12.5 | 可选测试 | 扩展数据员工单元测试（Req 15.4, 15.5） |
| 14.3 | 可选测试 | 学习资源推荐员单元测试（Req 16.2, 16.4, 16.5） |
| 15.2 | 可选测试 | ArtifactController 单元测试（Req 17.4, 17.5） |
| 18 | Checkpoint | Final Checkpoint（全部测试通过验收） |

---

## 三、agent-execution-trace（Agent 执行轨迹可视化）

**整体进度：P1 + P2 全部核心任务 + 全部 Property 测试完成，剩余集成测试 + P3 前端时间线。**

### P1 — 核心闭环（必须完成）

#### 任务 1：搭建轨迹基础设施骨架、常量与配置
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 1.1 | **核心** | 建包结构、引入 jqwik、新增 `trace.*` 配置项、在 `pom.xml` 添加 jqwik 依赖 | ✅ 已完成 |
| 1.2 | **核心** | 实现 `TraceProperties` 配置类，`@PostConstruct` 钳制取值范围 | ✅ 已完成 |
| 1.3 | 可选测试 | Property 12：配置取值范围钳制属性测试 | ✅ 已存在 |
| 1.4 | 可选测试 | TraceProperties 默认值单元测试 | ✅ 已存在 |

#### 任务 2：实现轨迹数据模型与枚举
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 2.1 | **核心** | 实现 `TraceStatus`、`TraceStepStatus`、`TraceStepType`（10 个取值，含中文 displayName） | ✅ 已完成 |
| 2.2 | 可选测试 | Property 16：步骤类型显示名完整且唯一属性测试 | ✅ 已完成 |
| 2.3 | 可选测试 | 枚举取值集合单元测试 | ✅ 已完成 |
| 2.4 | **核心** | 实现 `TraceSpan`（含 `start`、`isTerminal`、`terminate` 方法） | ✅ 已完成 |
| 2.5 | **核心** | 实现 `ExecutionTrace`（含 `start`、`finalizeStatus` 方法） | ✅ 已完成 |
| 2.6 | 可选测试 | Property 1：终态计时不变量属性测试 | ✅ 已完成 |
| 2.7 | 可选测试 | Property 2：RUNNING 期间无终态字段属性测试 | ✅ 已完成 |
| 2.8 | 可选测试 | Property 4：轨迹状态推导属性测试 | ✅ 已完成 |

#### 任务 3：实现请求级上下文 TraceContext
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 3.1 | **核心** | 实现 `TraceContext`（`appendSpan`、`finishSpan`、`failRunningSpan`、`finalizeTrace`、`noop()`） | ✅ 已完成 |
| 3.2 | 可选测试 | Property 3：步骤序号连续且关联同一轨迹属性测试 | ✅ 已完成 |
| 3.3 | 可选测试 | Property 10：单轨迹 span 容量上限属性测试 | ✅ 已完成 |
| 3.4 | 可选测试 | Property 8：标识在生命周期内不变属性测试 | ✅ 已完成 |

#### 任务 4：实现采集门面 TraceRecorder
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 4.1 | **核心** | 实现 `TraceRecorder`（`startTrace`、`startSpan`、`endSpan`、`failSpan`、`skipSpan`、`endTrace`、`failTrace`，全部 try-catch 容错） | ✅ 已完成 |
| 4.2 | **核心** | 实现 metadata 限额截断（≤50 键、键≤128 字符、值按码点截断）与错误信息处理 | ✅ 已完成 |
| 4.3 | 可选测试 | Property 7：标识全局唯一属性测试 | ✅ 已完成 |
| 4.4 | 可选测试 | Property 5：错误信息非空且有界属性测试 | ✅ 已完成 |
| 4.5 | 可选测试 | Property 6：metadata 限额与码点截断属性测试 | ✅ 已完成 |
| 4.6 | 可选测试 | Property 15：记录器容错——绝不向主流程抛异常属性测试 | ✅ 已完成 |
| 4.7 | 可选测试 | TraceRecorder 三态与异步尾步骤单元测试 | ✅ 已完成 |

#### 任务 5：实现轨迹持久化与保留容量 TraceRepository
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 5.1 | **核心** | 实现 `TraceRepository`（复用 ArtifactRepository 范式，`save`、`findById`、`init`） | ✅ 已完成 |
| 5.2 | **核心** | 实现 `findByChatId`、`findByUserId`（倒序）与单用户保留策略（超上限删最早） | ✅ 已完成 |
| 5.3 | 可选测试 | Property 9：序列化往返一致属性测试 | ✅ 已完成 |
| 5.4 | 可选测试 | Property 11：单用户轨迹保留上限属性测试 | ✅ 已完成 |
| 5.5 | 可选测试 | Property 13：列表查询过滤与倒序属性测试 | ✅ 已完成 |
| 5.6 | 可选测试 | TraceRepository 加载/容错单元测试 | ✅ 已完成 |

#### 任务 6：Checkpoint — 轨迹模型/上下文/记录器/持久化可用
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 6 | **Checkpoint** | 确保 P1 基础设施全部测试通过 | ✅ 核心代码已就绪 |

#### 任务 7：实现轨迹查询 REST 接口 TraceController
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 7.1 | **核心** | 实现 `TraceController`（`GET /trace/{traceId}`、`GET /trace/chat/{chatId}`、`GET /trace/user/{userId}`，含 JWT 鉴权与 Result 包装） | ✅ 已完成 |
| 7.2 | 可选测试 | Property 14：授权过滤绝不泄露他人轨迹属性测试 | ✅ 已完成 |
| 7.3 | 可选测试 | TraceController 各分支单元测试（401/400/404/403/200） | ✅ 已完成 |

#### 任务 8：集成轨迹采集到编排链路
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 8.1 | **核心** | `AiController` 生成并透传 `requestId` | ✅ 已完成 |
| 8.2 | **核心** | `OrchestratorAgent` 注入 `TraceRecorder`，挂接 `chatStream` 生命周期（startTrace / SKILL_MATCH / endTrace / failTrace） | ✅ 已完成 |
| 8.3 | **核心** | `routeToAgent` 插入 10 类采集挂点（INTENT_DETECTION / ROUTING / PROFILE_INJECTION / ARTIFACT_QUERY / ARTIFACT_CONSUME / SUB_AGENT_EXECUTION 等） | ✅ 已完成 |
| 8.4 | **核心** | `triggerProfileUpdate` 记录异步 PROFILE_UPDATE 尾步骤 | ✅ 已完成 |
| 8.5 | **核心** | `ToolCallAgent` 透传 `TraceContext`，记录 TOOL_CALL span | ✅ 已完成 |
| 8.6 | **核心** | `ChatMemoryManager` 记录 MEMORY_COMPRESSION span | ✅ 已完成 |
| 8.7 | 可选测试 | 编排采集集成测试（10 类 stepType 均被记录） | |
| 8.8 | 可选测试 | 非侵入集成测试（采集不增加 LLM/工具调用次数） | |
| 8.9 | 可选测试 | 持久化集成测试（`@PostConstruct` 加载、save 后 findById 命中） | |
| 8.10 | 可选测试 | 采集性能测试（单事件延迟 ≤50ms） | |

#### 任务 9：Checkpoint — P1 核心闭环完成
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 9 | **Checkpoint** | 确保 P1 全部核心任务测试通过 | ✅ P1 核心代码全部就绪 |

### P2 — 实时轨迹事件流

#### 任务 10：实现实时轨迹事件流
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 10.1 | **核心** | `TraceContext` 绑定 `SseEmitter`，增加 `markSseClosed()`/`isSseClosed()` | ✅ 已完成 |
| 10.2 | **核心** | 实现 `TraceStreamPublisher`，接入 `TraceRecorder`，推送 `trace` 事件（与 routing/message/error 并存） | ✅ 已完成 |
| 10.3 | 可选测试 | 实时事件流集成测试（推送失败容错、开关关闭仍持久化） | ✅ 已完成 |

#### 任务 11：Checkpoint — P2 完成
| 子任务 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| 11 | **Checkpoint** | 确保 P2 全部测试通过 | ✅ P2 核心代码全部就绪 |

### P3 — 前端时间线可视化

#### 任务 12：实现前端时间线视图
| 子任务 | 类型 | 说明 |
|--------|------|------|
| 12.1 | **核心** | 前端新增 trace API 调用与 `TraceTimelineView` 组件，按 sequence 升序渲染步骤，ERROR 步骤特殊样式，RUNNING 步骤「进行中」占位 |

#### 任务 13：Final Checkpoint — 全部完成
| 子任务 | 类型 | 说明 |
|--------|------|------|
| 13 | **Checkpoint** | 确保全部测试通过 |

---

## 优先级建议

```
立即开始（阻塞后续）
└── agent-execution-trace P1
    ├── 1.1 建包 + pom.xml 引入 jqwik + application.yml 配置
    ├── 1.2 TraceProperties
    ├── 2.1 三个枚举
    ├── 2.4 TraceSpan
    ├── 2.5 ExecutionTrace
    ├── 3.1 TraceContext
    ├── 4.1 TraceRecorder（容错门面）
    ├── 4.2 metadata 截断
    ├── 5.1 TraceRepository（持久化）
    ├── 5.2 列表查询 + 保留策略
    ├── 7.1 TraceController（REST 接口）
    └── 8.1–8.6 编排链路集成

可并行（不阻塞核心）
├── appointment-consultation-intent 可选属性测试（10 项）
└── data-employee-agents 可选单元/集成测试（14 项）
```
