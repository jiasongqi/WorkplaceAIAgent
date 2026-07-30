# 多模态 Agent 教程落地笔记（mm_agent_tutorial Ch4 → WorkPilot）

> 来源：[第 4 章 Agent Loop：规划-执行-反思的闭环](https://zsc.github.io/mm_agent_tutorial/chapter4.html)  
> 落地日期：2026-07-29  
> 原则：Loop 是主线程——调度、刹车、收尾；不重做 Browser VLA。

---

## 1. 对照结论

| 教程概念 | WorkPilot 原有 | 本次落地 |
|---------|---------------|---------|
| O-T-A-R | ReAct think/act；Reflect 分散 | **步内 StepReflector**（非 NORMAL） |
| ReAct | `ToolCallAgent` / Manus | 补完成态约束 + Claim Guard |
| Plan-and-Execute | `PlanAndSolveAgent` 线性 | **Replanner（最多 1 次）** |
| Reflexion 记忆 | `ReflexionService` | 保留；与步内 Reflect 互补 |
| maxSteps / Stall | 已有 | 保留 |
| Out-of-Budget Wrap-up | 仅「达到最大步骤」 | **`LoopWrapUp` + `AgentLoopResult`** |
| 结构化终态 | Artifact/Trace | **`AgentLoopResult` payload** |
| 「I have done it」幻觉 | 弱 | **Prompt + `CompletionClaimGuard`** |
| Depth Limit | 无 | **`AgentDepthContext`（默认 ≤3）** |

---

## 2. 改动清单

### P0 — Wrap-up

| 组件 | 路径 | 行为 |
|------|------|------|
| `LoopWrapUp` | `agent/loop/LoopWrapUp.java` | 步数耗尽且仍 RUNNING → 强制部分结论 + 未完成项 + 置信度声明 |
| `BaseAgent` | `agent/BaseAgent.java` | `shouldWrapUp()`；SSE/同步路径统一收尾 |
| `AgentLoopResult` | `agent/loop/AgentLoopResult.java` | `SUCCESS` / `PARTIAL_SUCCESS` / `FAILED` + artifacts / needsHumanHelp |

### P1 — P&E Replanner

| 组件 | 行为 |
|------|------|
| `PlanAndSolveAgent` | 步骤失败或 Verify=`partial/failed` → `replanRemaining`（MAX_REPLANS=1）→ 只执行剩余步 |

### P1 — 完成态防幻觉

| 组件 | 行为 |
|------|------|
| `YuManus` System Prompt | 禁止自造 Observation；成功 Tool Output 才能声称已写入/发送 |
| `CompletionClaimGuard` | think 无工具且文本含「已发送/已写入…」且无成功 Tool → 警告 |
| `ReActAgent.step` | 接入 Guard |

### P1 / P2 — Reflect + Depth

| 组件 | 行为 |
|------|------|
| `StepReflector` | 非 NORMAL 工具结果注入 `[Step Reflect]`（模板，无额外 LLM） |
| `AgentDepthContext` | ThreadLocal 深度；`BaseAgent` / `BaseParadigmAgent` / `OrchestratorAgent.invokeExpertSync` |

---

## 3. 未做（诚实边界）

- 每步都跑 LLM Reflect（成本高；非 NORMAL 才模板 Reflect）
- 跨会话持久化 `AgentLoopResult`（当前挂在 Agent 实例 `lastLoopResult`）
- Retry Storm 专用 dirty-json 库（Tool Call 已结构化）

---

## 4. 验证

```bash
mvn "-Dtest=LoopCh4GuardsTest" test
```

---

## 相关代码

- `agent/loop/*`
- `agent/BaseAgent.java` · `ReActAgent.java` · `ToolCallAgent.java` · `YuManus.java`
- `agent/paradigm/PlanAndSolveAgent.java` · `BaseParadigmAgent.java`
- 面试：`docs/interview-perception-goal-reliability.md` Ch4 节
- `docs/FEATURES.md` L4 / L29 / L26
