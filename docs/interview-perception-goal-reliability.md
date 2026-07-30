# 面试专题：感知层 / Goal / Tool / Loop 可靠性（2026-07 迭代）

> 配合 `docs/INTERVIEW_QA_SKILL.md`、`.cursor/skills/agent-interview-qa/SKILL.md`、`docs/FEATURES.md` L2/L4/L26/L29/L34、`docs/INTERVIEW-DEFENSE.md` 使用。  
> 原则：讲清 **背景 → 问题 → 根因 → 方案 → 术语 → 诚实边界**。  
> 落地笔记：`mm-agent-tutorial-ch1-落地.md` · `ch3` · `ch4` · `ch5` · [场景对照总结](./mm-agent-tutorial-场景对照总结.md) · 同步日期 2026-07-30

---

## 一、迭代背景（为什么做）

学习 [多模态 Agent 教程 Ch1](https://zsc.github.io/mm_agent_tutorial/chapter1.html) 后，对照 WorkPilot：

| 教程概念 | 项目缺口 | 落地优先级 |
|---------|---------|-----------|
| Model vs Agent（动词法则） | 已有 Orchestrator/Manus | 巩固表达即可 |
| Perception 降维 | 无文档感知，上传曾误走知识库 MD | **P1** |
| Goal 每轮重插 | SharedState 目标弱、ReAct 易忘任务 | **P0** |
| 连续失败烧 Token | 有 LoopDetector，缺「连续失败计数」 | **P0** |
| Budget Awareness | 有 TokenBudget，缺「先便宜解析再精读」 | **P1** |
| 视觉 Prompt Injection | 仅文本检测 | **P3 桩** |

结论：不做完整 Browser/VLA Agent；把**可靠性工程 + 职场文档感知**接到现有分层上。

---

## 二、专业名词速查（面试可脱口）

| 术语 | 含义 | WorkPilot 落点 |
|------|------|----------------|
| **Perception Layer** | 环境观测的降维与结构化，不是裸像素进模型 | `DocumentPerceptionService` |
| **Semantic Stream** | 像素/字节 → 可推理的文本/字段 | `PerceptionResult` / `promptBlock` |
| **Budget Awareness** | 分层策略：便宜 OCR/解析扫一遍，再精读 | PDFBox/POI 优先于 VLM |
| **Goal Anchor / Goal Grounding** | 每轮/每步重插任务目标，抗上下文遗忘 | `GoalAnchor` + system re-inject |
| **Context Forgetting** | 长 Context 后模型忘掉初始 Goal | Ch1 Gotcha；本项目主动重插 |
| **OODA Loop** | Observe-Orient-Decide-Act | ReAct think/act + Perception observe |
| **Fail-fast Fuse** | 连续失败达阈值立即停，避免死循环烧钱 | `ConsecutiveFailureGuard` |
| **HITL Escalation** | 高危或失控时人工接管 | `HumanHandoffService.park` |
| **Perceptual Hallucination** | 看不清却编造读数 | `PerceptionCrossValidator` |
| **Prompt Injection（视觉侧）** | 图中藏指令劫持模型 | `VisualPromptSanitizer` |
| **Hybrid Retrieval** | 文本召回 + 少量视觉引用 | `TextFirstHybridRetrieval`（桩） |
| **EventSource URL Limit** | 浏览器 SSE GET 查询串长度上限 | `preprocess-and-bind` 解耦 |
| **Shared Session State** | 会话级结构化便签，跨专家可读 | `SessionSharedState.lastPerceptionBlock` |
| **Intent Ambiguity** | 多意图/低置信触发澄清 | `IntentAmbiguityDetector` |
| **Fast-path Routing** | 零 LLM 规则路由 | `KeywordRouter` + `suggestIntentFromPerception` |

---

## 三、STAR 故事（可直接口播）

### 故事 A：上传简历却被要求「具体描述领域」

- **Situation**：联调 Perception，上传 `resume.txt`，前端默认发「请根据材料分析…」  
- **Task**：应路由到 ResumeAgent 并基于材料给建议  
- **Action**：查 Trace/日志 → NLU `confidence≈0.05` + Ambiguity 澄清；同时发现 SharedState **1800 字截断**把感知块砍掉  
- **Result**：① 默认话术改为含「简历/Offer」；② `suggestIntentFromPerception` 有绑定材料时走快路径；③ 感知块置顶 + 上限 5000  

**面试金句**：  
> 「问题不在模型笨，而在 **控制面**：模糊用户话触发了澄清策略，加上下文预算截断导致专家根本没看到附件。」

### 故事 B：SSE 塞不下整份简历

- **Situation**：EventSource 只能 GET，`promptBlock` 拼进 `message` query  
- **Task**：长中文 URL 编码后易超浏览器/网关限制  
- **Action**：引入 **preprocess-and-bind**：材料进 SharedState，SSE 只传短指令  
- **Result**：职场顾问路径稳定；Manus 无会话则截断拼接并文档标明限制  

**面试金句**：  
> 「这是 **传输层约束倒逼架构**：感知结果与聊天传输解耦，符合 Controller→AppService→Domain，也符合 Budget Awareness。」

### 故事 C：工具死循环烧 Token

- **Situation**：教程强调连续报错重试瞬间烧钱；项目已有 EmbeddingLoopDetector（重复调用）  
- **Task**：补「连续失败」维度  
- **Action**：`ConsecutiveFailureGuard` 与 `ToolResultClassifier` 联动，阈值可配，可升级 HITL  
- **Result**：与 LoopDetector 职责正交——一个管「重复同一动作」，一个管「连续失败」  

---

## 四、题库增量（Q56–Q62）

### Q56: 什么是 Agent 的 Perception 层？你们怎么做的？

**结论**：Perception 把环境观测变成结构化语义，再给 Brain 决策。  
**落地**：`DocumentPerceptionService`（PDFBox/POI）→ 启发式字段 → bind SharedState → 专家注入。  
**诚实**：扫描件 OCR、真 VLM 精读未上；混合检索是脚手架。

### Q57: Goal Anchor 解决什么问题？

**结论**：对抗 Context Forgetting。  
**落地**：注入链最前 + ReAct 每步 system 重挂 Goal。  
**对比**：只靠 ChatMemory 窗口不够，目标必须显式、高频。

### Q58: LoopDetector 和 ConsecutiveFailureGuard 区别？

| | LoopDetector | ConsecutiveFailureGuard |
|--|--------------|-------------------------|
| 信号 | 调用指纹/embedding 相似 | 结果分级非 NORMAL / 超时 / think 异常 |
| 场景 | 同一工具换汤不换药 | 方向可能对但一直失败 |
| 动作 | 注入引导或终止 | 终止 + 可选 HITL |

### Q59: 为什么不用把图片/PDF 直接给多模态模型？

**Budget Awareness**：多模态 Token 贵、噪声大。  
策略：便宜解析 → 结构化 → 专家文本 Agent；图仅净化；未来才 Top-1 精读。

### Q60: 上传文件后路由怎么保证？

1. Keyword（消息含「简历」）  
2. 感知快路径 `docKind=resume/offer`  
3. 否则 NLU（可能澄清）  
联调教训：缺 1/2 时会被 Ambiguity 误伤。

### Q61: 举一个你亲手修过的线上/联调缺陷？

用 **故事 A 或 B**（见上），强调日志驱动：NLU confidence、截断预算、URL 限制。

### Q62: 感知幻觉怎么防？

交叉验证：VLM/感知假设 vs 工具真值（`PerceptionCrossValidator`）；低一致度以工具为准。Excel 场景对应教程「看图猜数 vs read_cell」。

---

## 附：Ch3 Tool Call 面试速记（2026-07）

> 教程：[Ch3 Tool Call](https://zsc.github.io/mm_agent_tutorial/chapter3.html) · 落地：`docs/mm-agent-tutorial-ch3-落地.md`

| 术语 | 含义 | WorkPilot 落点 |
|------|------|----------------|
| **Schema Engineering** | 接口描述=Prompt；写清何时用/不用 | 各 `@Tool` description |
| **Parallel Tool Use / Fan-out** | 同轮多 call 并发执行 | `ParallelToolCallingSupport` |
| **Submit-Poll** | 长任务先返回 taskId 再轮询 | `start*` + `checkAsyncToolTask` |
| **Observation Sanitizer** | 工具输出进 Context 前清洗截断 | `ObservationSanitizer` |
| **Idempotency** | 副作用超时重试不重复执行 | `ToolIdempotencyStore` + `ToolSideEffectPolicy` |
| **Pass by Reference** | 大文件传 file_id 不传全文 | `FileHandleStore` + `readFileChunk` |
| **HITL Tool Gate** | 高危工具先审批再执行 | `HumanApprovalService`（已有） |

### Q63: 为什么超时不能一律重试工具？

只读（search/read）可重试；写文件/下载/终端有副作用，超时重试可能执行两次。我们用 `ToolSideEffectPolicy` 区分，副作用靠 `ToolIdempotencyStore` 指纹去重。

### Q64: Schema 为什么比再加一个工具更重要？

模型不读实现只读 description。边界写清能直接降低 Tool Confusion，是最便宜的「变聪明」方式。

### Q65: 大文件怎么避免 Context 污染？

`readFile` 超阈值只返回 `file_id` + preview；细节用 `readFileChunk` 按行取。对应教程「传 ID 不传值」。

---

## 附：Ch4 Agent Loop 面试速记（2026-07）

> 教程：[Ch4 Agent Loop](https://zsc.github.io/mm_agent_tutorial/chapter4.html) · 落地：`docs/mm-agent-tutorial-ch4-落地.md`

| 术语 | 含义 | WorkPilot 落点 |
|------|------|----------------|
| **O-T-A-R** | Observe→Think→Act→Reflect | ReAct + `StepReflector` |
| **Wrap-up** | 预算耗尽强制收尾，不 Crash | `LoopWrapUp` / `AgentLoopResult` |
| **Plan-and-Execute + Replanner** | 执行失败后只改剩余计划 | `PlanAndSolveAgent.replanRemaining` |
| **Stall Detection** | 无效循环检测 | `EmbeddingLoopDetector`（已有） |
| **Depth Limit** | 子任务递归深度上限 | `AgentDepthContext`（≤3） |
| **I-have-done-it 幻觉** | 未真正调用工具却声称完成 | `CompletionClaimGuard` + Manus Prompt |
| **Structured Loop Payload** | 终态 status/summary/artifacts | `AgentLoopResult` |

### Q66: maxSteps 触顶应该 Crash 还是怎样？

错误做法是抛异常或只回「达到最大步骤」。正确做法是 **Wrap-up**：基于已有步骤生成部分结论 + 未完成清单 + 置信度声明（`LoopWrapUp` → `PARTIAL_SUCCESS`）。

### Q67: ReAct 和 Plan-and-Execute 怎么选？

短链路 / 动态排查用 ReAct；长流程 / 报告用 P&E。P&E 在步骤失败或 Verify 不达标时走 **Replanner**（最多一次），避免整段 Context 被 ReAct 中间过程撑爆。

### Q68: 什么是 “I have done it” 幻觉？怎么防？

模型在 Thought 里写「已发邮件」但没有成功 Tool Output。防法：1）Prompt 规定只有系统注入的成功回执才能确认完成；2）`CompletionClaimGuard` 在无工具结束时扫描完成态话术并警告。

### Q69: Stall Detection 和连续失败熔断有何不同？

Stall/LoopDetector 管「重复调用像卡住」；ConsecutiveFailureGuard 管「连续失败烧 Token」。正交，可同时触发 HITL。

### Q70: 为什么要 Depth Limit？

子 Agent / 范式嵌套过深说明目标发散。`AgentDepthContext` 默认 ≤3，触顶返回明确错误而非无限递归烧预算。

---

## 五、1 分钟项目介绍（更新版可替换旧稿）

> WorkPilot 是职场全生命周期 AI 智囊：Orchestrator + NLU/Keyword 路由到简历/谈薪等专家。近期补了 Perception / Goal Anchor / 熔断 HITL，Ch3 工具工程（Schema、并行、清洗、幂等、Submit-Poll），以及 Ch4 **Agent Loop**：步数耗尽 Wrap-up、P&E Replanner、完成态防幻觉、Depth Limit。技术栈 Java 21 / Spring Boot 3.4 / Spring AI / Vue 3。

---

## 六、演示路径（背）

1. 登录 → `/chat/career` → 📎 上传 `resume.txt` → 看「感知完成」→ 简历专家流式建议  
2. Network 确认 `preprocess-and-bind` 再短消息 SSE  
3. 可选：Admin Trace 看 PROFILE_INJECTION / Goal metadata  
4. 诚实说明：扫描 PDF / 老 `.doc` / 纯图 OCR 未做  

| **Retrieval Loop** | 空检索反复调用烧 Token | `RagRetrievalAttemptTracker` |
| **Experience Query** | L4 用错 query 召不回经验 | `ExperienceQueryBuilder` |
| **Corpus vs Session Upload** | 知识库语料 vs 会话感知 bind | `/document/*` vs `PerceptionAppService` |

---

## 相关代码

- `perception/*`、`service/PerceptionAppService.java`、`controller/PerceptionController.java`  
- `rag/RetrievalPipeline.java`、`rag/RagTool.java`、`document/pdf/*`、`service/DocumentAppService.java`  
- `agent/goal/GoalAnchor.java`、`guard/ConsecutiveFailureGuard.java`  
- `agent/loop/*`（Wrap-up / Depth / Claim / Reflect）  
- `sessionstate/SessionSharedStateService.java`（`setPerceptionBlock` / `suggestIntentFromPerception`）  
- `yu-ai-agent-frontend/src/views/CareerAdvisor.vue` · `KnowledgeBase.vue`  
- 详设：`docs/mm-agent-tutorial-场景对照总结.md` · `ch1` · `ch3` · `ch4` · `ch5` · `docs/FEATURES.md` L1/L2/L4/L26/L29/L34
