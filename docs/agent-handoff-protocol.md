# Agent Handoff 协议设计（WorkPilot）

> 参考：[mm_agent_tutorial 第 6 章 Agent Handoff](https://zsc.github.io/mm_agent_tutorial/chapter6.html)  
> 配套面试稿：[interview-multi-agent-session-state.md](./interview-multi-agent-session-state.md)

---

## 1. 为什么要做

单体 Agent 长链路会注意力稀释、角色混淆、Token 成本失控。Handoff 的本质是一次**熵减**：把前序试错压缩成「结论 + 资产引用」，交给拥有干净上下文的下一个专家。

WorkPilot 拓扑以**星型 Router**（`OrchestratorAgent`）为主；DAG 为**线性接力**；质量失败走 **SELF_REPAIR → GENERAL**；跳数耗尽走 **Human 异步接管**。

---

## 2. 优先级落地对照

| 优先级 | 能力 | 状态 | 入口 |
|--------|------|------|------|
| P0–P2 | Packet / NACK / TTL / scope / Self-Repair / Parser / 证据穿透 / 拓扑标注 | ✅ | 见历史章节 |
| P3 | Human 异步移交 | ✅ | `HumanHandoffService` + `/hitl/handoff/*` |
| P3 | Manifest 语义二次路由 + NACK 降权 | ✅ | `AgentManifestRegistry` |
| P3 | 模型级升级（小→大模型） | 📋 未做 | 可挂在 SELF_REPAIR 上 |

---

## 3. Human 异步移交（事件驱动）

```
hop_ttl / ping_pong
  → HumanHandoffService.park(Packet)  // 落盘 WAITING_FOR_HUMAN
  → SSE human-handoff + 提示文案 + [DONE]
  → 释放线程（不 sleep）

下一轮用户消息 或 POST /hitl/handoff/resume
  → resume → SharedState 注入 humanHandoffInput
  → Orchestrator 继续路由作答
```

### API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/hitl/handoff/pending` | 当前用户待接管列表 |
| GET | `/hitl/handoff/{id}` | 查询单 |
| POST | `/hitl/handoff/resume?handoffId=` | body: `{ "humanInput": "…" }` |
| POST | `/hitl/handoff/cancel?handoffId=` | 取消 |

配置：`app.hitl.human-handoff-ttl-seconds`（默认 86400），存储 `./tmp/hitl/human-handoffs.json`，可选 `notify-webhook`。

与工具级 HITL（日历/写文件）并存：工具级用 `HumanApprovalService`；会话级用 `HumanHandoffService`。

---

## 4. Manifest 二次路由

低置信度（&lt;0.55）或落到 GENERAL 时，用专家 Manifest 关键词重叠打分选人；质量 failover 后对失败专家 `penalize`，自修复成功则 `reward`。

---

## 5. Gotcha 防护

| Gotcha | 状态 |
|--------|------|
| 传声筒 | ✅ 事实优先 + raw 证据穿透 |
| 幻觉引用 | ✅ sanitizeArtifacts |
| 幽灵权限 | ✅ HandoffScopeContext |
| 格式承诺崩溃 | ✅ HandoffPacketParser |

---

## 6. 验收清单

- [ ] hop 超限 → SSE `WAITING_FOR_HUMAN`，进程不挂起
- [ ] 下一轮对话自动 resume 并带上人工补充
- [ ] `POST /hitl/handoff/resume` 可唤醒
- [ ] 低置信度「改简历」→ Manifest 路由到 RESUME
- [ ] 单测：`HumanHandoffServiceTest`、`AgentManifestRegistryTest`

---

## 7. 关键类

| 类 | 包 |
|----|-----|
| `HumanHandoffTicket` / `HumanHandoffService` | `hitl` |
| `HitlController`（handoff API） | `controller` |
| `AgentManifest` / `AgentManifestRegistry` | `agent.manifest` |
| `HandoffPacket*` / `HandoffProtocolService` / `HandoffPacketParser` | `sessionstate` |
| `OrchestratorAgent`（park / auto-resume / Manifest） | `agent` |
