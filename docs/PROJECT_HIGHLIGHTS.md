# WorkPilot 项目亮点提炼

> 视角：一个 Agent-native、经验丰富的 AI 架构师如何看待这个项目
> 核心问题：这不是一个 demo/玩具，而是一个生产级 AI Agent 平台

---

## 一、架构决策的"反直觉"之处（真正体现功力）

### 1. 路由层确定性 + 子 Agent 层自主性

大多数 Agent 项目要么全自主（ReAct 一切），要么全确定性（硬编码流程）。WorkPilot 的设计是：

```
OrchestratorAgent（确定性路由）
  ├─ KeywordRouter（零 LLM，规则匹配）
  ├─ NluPipeline（单次 LLM，结构化输出）
  └─ 子 Agent（自主性：ReAct / 状态机 / 工具循环）
```

**为什么这是对的？** 路由是高频操作，每次对话都要走。如果路由也用 ReAct，延迟和成本会爆炸。把路由做成确定性（快速路径 + 单次 LLM），子 Agent 内部保持自主性，这是**生产环境的正确取舍**。

### 2. 记忆系统不做"万能向量库"

很多项目把所有记忆都塞进向量数据库。WorkPilot 的四层记忆是**按数据类型选存储**：

| 数据类型 | 存储方式 | 为什么 |
|----------|---------|--------|
| 最近对话 | 滑动窗口（内存） | 最快，保持连贯性 |
| 用户事实 | 键值对（精确匹配） | "用户叫小琪"不需要语义检索 |
| 对话摘要 | 结构化清单（FIFO） | 话题/决策/待办，有序淘汰 |
| 历史经验 | 向量数据库（语义检索） | "上次类似情况怎么处理"需要模糊匹配 |

**为什么这是对的？** 向量检索是模糊的，适合"找相似的"。但"用户叫小琪"用键值对精确匹配更快更准。不同数据类型用不同存储，各取所长。

### 3. 别名不改原文

NLU 管道的 AliasResolver 只输出元数据（AliasMatch），不修改用户原始消息。

**为什么这是对的？** 如果把"阿里"替换为"阿里巴巴"再送给 LLM，LLM 看到的上下文被污染了。如果后续需要回溯用户到底说了什么，替换过的消息会丢失原始意图。元数据分离是**可逆设计**。

### 4. Confidence = Top1 - Top2 差值

不直接用 LLM 自报的概率（overconfidence），而是用排名差值。

**为什么这是对的？** LLM 对自己的置信度校准很差（calibration）。一个 0.95 和 0.93 的差距可能不代表真实的不确定性。但 Top1-Top2 差值大说明意图明确，差值小说明模糊——这个信号更可靠。

### 5. 提取管道"永不阻塞"

ExtractionPipeline 对话后异步运行，所有异常内部捕获，永不阻塞调用者。

**为什么这是对的？** 对话响应是用户可感知的延迟。如果等记忆提取完成再返回，用户要多等 2-5 秒。异步化让对话响应和记忆更新解耦。

---

## 二、生产级工程细节（区分 demo 和生产）

### 6. TraceRecorder 绝不向主流程抛异常

所有 trace 操作都 try-catch 容错。即使 trace 系统挂了，对话功能不受影响。

**为什么重要？** 可观测性系统不应该成为单点故障。很多项目的 trace 代码没有容错，一旦 trace 写入失败，整个请求就挂了。

### 7. SSE onTimeout/onError 回调

SseEmitter 设置了 onTimeout 和 onError 回调，客户端断开时自动清理 trace 资源。

**为什么重要？** 客户端断开是常态（网络波动、用户刷新页面）。如果不处理，服务端会累积大量僵尸 trace。

### 8. ToolCallAgent：超时 + 只读重试 + 并行 Fan-out

- 每工具约 30s 超时；**仅只读工具**超时自动重试（`ToolSideEffectPolicy`），副作用靠幂等指纹防重复执行
- 同轮多个 tool call → `ParallelToolCallingSupport` 并发
- Observation 进 Context 前经 `ObservationSanitizer` 清洗；大文件传 `file_id` + `readFileChunk`
- 长任务 `start*` + `checkAsyncToolTask`（Submit-Poll）

**为什么重要？** 「一律超时重试」会把写文件/下载执行两遍；传全文会撑爆 Context。这是 Ch3 Tool Call 工程的核心取舍。

### 9. EmbeddingLoopDetector 循环检测

不是简单计数（调用 N 次就停），而是用 Embedding 余弦相似度检测"语义重复"。检测到循环后注入引导性消息，让 LLM 自主修正。

**为什么重要？** 简单计数会误杀（有些任务确实需要多次工具调用）。语义检测更精准。而且"注入引导"比"直接终止"更温和——给 LLM 一个修正的机会。

### 9b. ConsecutiveFailureGuard + Loop Wrap-up（Ch1/Ch4）

- 连续非 NORMAL 工具结果达阈值 → 终止并可 HITL park（与 LoopDetector 正交）
- `maxSteps` 触顶且仍 RUNNING → **Wrap-up**（部分结论 + 未完成清单），不 Crash
- P&E 步骤失败 → **Replanner**（最多一次）；嵌套 Depth Limit ≤3

**为什么重要？** Loop 是操作系统：调度、刹车、收尾。死循环烧 Token 是生产第一敌人。

### 10. 投票式访问控制（一票否决）

三个维度独立评估：Agent 权限、MCP 信任等级、调用配额。任何一个拒绝就拒绝。

**为什么重要？** RBAC 是静态的，投票式是动态的。一个 Agent 有权限但 MCP 服务不信任，最终拒绝。这是**最小权限原则**的动态实现。

### 11. Perception 感知层（Ch1）

简历/Offer 先 PDFBox/POI 降维 → SharedState bind → SSE 短消息；Goal Anchor 每步重插目标。

**为什么重要？** 不把 PDF/像素直接塞进 VLM；符合 Budget Awareness，并规避 EventSource URL 长度限制。

---

## 三、AI 架构师会问的真实问题

### Q1: 你的 Agent 会不会"发疯"？

**回答**：多层防护：
1. `maxSteps` 硬上限；触顶走 `LoopWrapUp`（部分成功收尾），不抛异常走人
2. `EmbeddingLoopDetector` 余弦相似度检测（语义重复 / Stall）
3. `ConsecutiveFailureGuard` 连续失败熔断 → 可选 HITL
4. `TokenBudgetManager` + `ObservationSanitizer` 控 Context
5. `AgentDepthContext` 嵌套深度 ≤3；`CompletionClaimGuard` 防「我说做了」幻觉

检测到循环后不是直接终止，而是注入引导 / Reflect；预算耗尽则强制 Wrap-up。

### Q2: 你的系统能扛多少并发？

**回答**：
- 文件持久化 + ReadWriteLock，单机万级会话够用
- Repository 接口抽象，底层可替换（Redis/PostgreSQL）
- SSE 异步执行（CompletableFuture.runAsync），不阻塞主线程
- 记忆查询并行（CompletableFuture 四层并发），不串行

### Q3: LLM 调用失败怎么办？

**回答**：多层降级：
1. OrchestratorAgent 捕获异常 → "该专家暂时无法回答"
2. MemoryCoordinator 超时回退 → last-known-good 缓存
3. SkillExecutor 失败 → 降级到 NLU 路由
4. NLU 路由失败 → 降级到 GENERAL Agent
5. 工具超时 → ToolResultClassifier 分级 → **只读**可重试 / 副作用幂等去重 / 换策略 / start* 异步
6. 步数耗尽 → LoopWrapUp 输出 PARTIAL_SUCCESS + 未完成项

### Q4: 如何保证用户数据不串？

**回答**：全链路隔离：
- 会话按 userId（chatOwner 反向索引）
- 消息按 chatId
- 画像按 userId
- 记忆按 userId + chatId
- 交付物按 userId + scope
- 收藏按 userId

### Q5: 你的 NLU 准确率多少？

**回答**：
- 快速路径覆盖简单消息（~40%），零 LLM 延迟
- 完整 NLU 走单次 LLM 调用，结构化 JSON 输出
- 评测中心（EvalCenter）做回归测试，路由评测（AgentRoutingEvalTest）验证准确率
- Confidence < 阈值时自动触发模板追问（零 LLM）

### Q6: 如何扩展新场景？

**回答**：7 步扩展：
1. 创建 Agent 类（继承 BaseAgent）
2. 创建 AgentRunner 适配器
3. 新增 AgentIntent 枚举值
4. 新增 NluIntent 枚举值
5. OrchestratorAgent switch 路由新增 case
6. TaskExecutor 注册 Runner
7. YAML 技能定义（可选）

不需要改 OrchestratorAgent 核心逻辑，只需新增 case。

### Q7: 你的记忆系统和 Mem0 有什么区别？

**回答**：
- Mem0 是单一事实存储（键值对）
- WorkPilot 是四层架构：滑动窗口 + 事实存储 + 摘要 + 向量经验
- 关键区别：**按数据类型选存储**，不是所有东西都塞进一个存储
- 提取管道：单次 LLM 同时提取事实/摘要/经验，不三次调用

### Q8: 为什么不用 LangChain？

**回答**：
- Spring AI 是 Spring 生态原生，自动配置、依赖注入、AOP 都天然支持
- ChatModel / ToolCallback / VectorStore 抽象符合 Spring 开发习惯
- 换模型只需改配置（DashScope ↔ Ollama ↔ OpenAI）
- 不是不用，是 Spring AI 更适合 Java 项目

---

## 四、技术深度体现

### 1. NLU V4.2 方案迭代

从 V1（单次 LLM 分类）→ V2（多意图 + 槽位）→ V3（上下文感知）→ V4.2（快速路径 + 单次 LLM + 别名元数据分离 + 路由模板），每轮 6-9 个问题迭代。

**体现**：不是一次做好的，是反复推敲、逐步演进的。

### 2. FactStore v1→v2 迁移兼容

新版本的 FactStore 改了字段结构，但保持向后兼容（旧格式自动迁移）。

**体现**：生产系统的数据不能"一刀切"升级，必须平滑迁移。

### 3. 交付物生命周期状态机

DRAFT → REVIEWING → APPROVED → PUBLISHED → ARCHIVED，每步有合法性校验 + 审计事件。

**体现**：不是简单的 CRUD，是有业务语义的状态管理。

### 4. OrchestratorAgent 职责拆分

从 800+ 行的 God Class 拆分为：
- OrchestratorAgent（路由核心）
- ContextInjectionService（上下文注入）
- QualityReviewHandler（质量审查）
- KeywordRouter（快速路由）

**体现**：知道什么时候该拆，怎么拆。

### 5. OrchestratorDependencies record

构造器从 27 个参数聚合为一个 record，类型安全 + IDE 友好。

**体现**：Java 21 特性用得恰当，不是为了用而用。

---

## 五、面试加分点

1. **能说出"为什么不用 X"**：比如"为什么不用向量库做所有记忆"、"为什么副作用工具不能超时一律重试"
2. **能说出"迭代了 N 轮"**：NLU V1→V4.2；多模态教程 Ch1/Ch3/Ch4 对照落地
3. **能说出"取舍"**：路由确定性 vs 全自主；ReAct vs P&E；硬截断 vs 智能摘要
4. **能说出"生产细节"**：trace 容错、SSE 断开、只读超时重试、幂等、Wrap-up、Depth Limit
5. **能说出"扩展路径"**：V1→V2→V3 演进、AgentRunner 适配层、Repository 接口抽象
6. **诚实边界**：扫描 OCR / 真 VLM / Browser Agent **未做**——比吹「全模态」更加分

> 同步：2026-07-29 · Ch1 Perception/Goal · Ch3 Tool · Ch4 Loop  
> 详设：`mm-agent-tutorial-ch1/ch3/ch4-落地.md` · `interview-perception-goal-reliability.md`
