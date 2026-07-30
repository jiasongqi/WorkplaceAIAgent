# 面试防崩溃话术（WorkPilot）

> 原则：**宁可说「脚手架」，不要说「已上线」却答不出调用链。**  
> 同步：2026-07-29 · Ch1 Perception/Goal/熔断 · Ch3 Tool Schema/并行/幂等 · Ch4 Loop Wrap-up/Replanner/Depth

## 30 秒项目定位

全场景职场 AI 智囊：Orchestrator 按意图路由到简历/谈薪/离职/预约/通用顾问；多意图时并行专家辩论再综合；带 Trace、质量审查、HITL、路由评测门禁。工程可靠性上补了 **Perception 感知层**、**Goal Anchor**、**连续失败熔断**、**工具 Schema/并行/幂等/Submit-Poll**，以及 **Agent Loop Wrap-up / P&E Replanner / Depth Limit**。

## 高频追问 → 诚实答案

| 面试官问 | 你怎么答 |
|---------|---------|
| 这是真 Multi-Agent 吗？ | **编排式协作**：单意图一个专家真 SSE；多意图并行辩论 + 综合 + 失败/低质换 GENERAL，黑板写 DEBATE/HANDOFF。不是 AutoGen 式常驻辩论群。 |
| SSE 是真流式吗？ | **单意图是** token 级 `chatStream`；多意图先并行算完再推送（会发 `agent-progress`）。 |
| Workflow / Paradigm / DataEmployee？ | **Workflow DAG `[部分]`**：`workflow.dag.enabled=true` 时 JOB_CHANGE/INTERVIEW 走就绪队列 + 真 AgentRunner。Paradigm 可选 ReAct / P&E(+Replanner) / Reflection；DataEmployee 仍偏脚手架。 |
| 怎么证明变好了？ | `POST /api/eval/gate/routing-suite`（KeywordRouter，零 LLM，ADMIN）；内容 live 另开，费 token。 |
| 工具安全？ | SSRF：`UrlSafetyGuard`；终端/日历/写文件：HITL；沙箱；副作用 **幂等**；连续失败可 HITL park。 |
| 工具超时一律重试？ | **否**。只读可重试；写/下载/终端靠 `ToolIdempotencyStore`，防重复副作用（Ch3）。 |
| 同轮多个工具？ | `ParallelToolCallingSupport` Fan-out 并发；有依赖则模型应串行。 |
| 大文件怎么进 Context？ | 超阈值返回 `file_id` + preview，细节 `readFileChunk`；Observation 经 Sanitizer。 |
| 长任务卡住？ | >~30s 用 `start*` + `checkAsyncToolTask`（Submit-Poll）；Loop 层还有超时。 |
| maxSteps 到了怎么办？ | **Wrap-up**：部分结论 + 未完成项 + 置信度声明（`PARTIAL_SUCCESS`），不 Crash。 |
| 「邮件已发送」但没调工具？ | Prompt 硬约束 + `CompletionClaimGuard`：无成功 Tool Output 不得宣称副作用完成。 |
| RAG 有来源吗？ | Resume 提示词强制引用文档名；空检索明确说未命中再通用回答。 |
| 成本怎么算？ | SSE `usage` 估算；UsageTracker；感知层先便宜解析（Budget Awareness）。 |
| DATA_QUERY？ | **未接业务库**：映射 GENERAL + 诚实说明，不编数字。 |
| 记忆？ | 四层 MemoryCoordinator + Reflexion + Goal Anchor 抗遗忘。 |
| 上传简历/PDF？ | **Perception**：抽文本→结构化→`preprocess-and-bind`；SSE 短句。扫描 OCR / 老 `.doc` **未做**。 |
| 多模态 VLM 全做了吗？ | **没有**。Hybrid/视觉防护是桩；路径是文本专家 + 文档降维，不是 Browser/VLA。 |
| 上传后为什么曾要「澄清领域」？ | 联调坑：默认话术过泛 + SharedState 截断；已用感知路由修复——可当踩坑故事。 |

## 演示路径（建议背）

1. 登录 → `/chat/career` → 📎 上传 `resume.txt` → 感知完成 → 简历专家流式建议  
2. Network：`preprocess-and-bind` → 短消息 SSE  
3. 问「优化简历」→ 真流式 + Trace（可看 Goal / 工具）  
4. 问「我想跳槽并谈薪」→ DAG / 并行辩论（视开关）  
5. 故意触发终端命令 → HITL approve  
6. Admin 跑 `/eval/gate/routing-suite`  
7.（可选）Manus：多搜索并行 / 步数耗尽看 Wrap-up 文案

## 绝对别说

- 「我们有 34 层都生产就绪了」  
- 「多 Agent 一直在辩论投票」  
- 「Token 计量和 OpenAI 一样精确」  
- 「数据查询已经接了公司数仓」  
- 「已经完整支持多模态 VLM / OCR 扫描件」  
- 「上传附件是直接把文件二进制喂给模型」  
- 「工具超时失败了就无脑重试三遍（含写操作）」  

## 延伸阅读

- `docs/FEATURES.md`（L0–L34）  
- `docs/interview-perception-goal-reliability.md`（Q55–Q70）  
- `docs/mm-agent-tutorial-ch1-落地.md` · `ch3` · `ch4`  
- `docs/PROJECT_HIGHLIGHTS.md` · `docs/INTERVIEW_QA_SKILL.md`
