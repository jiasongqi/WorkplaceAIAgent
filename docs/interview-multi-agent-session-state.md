# 面试题：同对话框切换 Agent，上下文为什么丢？怎么做才对？

> 场景来自 WorkPilot：用户刚预约成功，换到「职场通用顾问」问「看下我的日程」，对方说还没预约。

---

## 1. 一句话结论

**聊天文字可以共享，业务状态不能指望下一个 LLM「从聊天里猜出来」。**  
同会话切换专家必须有一层**与模型无关的 Shared Session State**；人设（伙伴/数字员工）和能力（专家路由）要分开。

---

## 2. 问题本质（面试官爱听的拆解）

| 维度 | 表现 | 根因 |
|------|------|------|
| 路由 | 「看日程」没命中预约关键词 → GENERAL | 规则/NLU 覆盖不全 |
| 状态隔离 | 预约只在 ConsultationAgent + AppointmentRepository | 通用顾问无读权限 |
| 记忆架构 | 各 Agent 独立 ChatMemory（resume/general/…） | 按 agentType 隔离，默认不互通 |
| 伪共享 | 只塞最近 10 条聊天摘要 | 摘要会被截断；结构化字段丢失 |
| 实现坑 | `syncCrossAgentMemory(chatId, type, type)` 上一任传成自己 | 跨 memory 同步失效 |

用户体感是「同一个对话框换助理就失忆」——本质是 **Session Truth（对话）≠ Domain State（预约）**。

---

## 3. 业内常见做法（分层记忆）

不要「所有 Agent 共用一整段无限聊天上下文」，而是：

1. **Session Truth（会话真相源）**  
   同一 `chatId` 的消息落库（PersistentMessage）。所有角色可读摘要。

2. **Shared Scratchpad / Session State（结构化共享状态）**  
   预约、当前目标、交接说明、关键事实（姓名/联系方式）用 JSON/表存。  
   **任意专家注入时可读；写权限按域隔离**（只有预约域写 appointment）。

3. **Persona vs Capability 分离**  
   - 职场伙伴 / 数字员工 → **人设与偏好**（绑 `userId`，跨会话）  
   - 简历/谈薪/预约专家 → **能力路由**（绑意图）  
   ChatGPT Custom GPT / Claude Project 也是「共享知识 + 当前对话，工具按任务切换」。

4. **Explicit Handoff（显式交接）**  
   切换时写结构化 **Handoff Packet**（Meta / Mission / Context / Artifacts），见 [agent-handoff-protocol.md](./agent-handoff-protocol.md)。  
   LangGraph / AutoGen / OpenAI Agents 都强调 structured handoff，而不是默默换人。

5. **外部记忆层（model-agnostic）**  
   状态不放在某一个 LLM 的 context window 里，而在 Redis/DB/文件；MCP/A2A 只管调用，**不管共享状态**——状态要自己建。

---

## 4. WorkPilot 落地（你可以讲「我做了什么」）

### 4.1 路由补洞
- 「日程 / 我的预约 / 查看预约」→ `CONSULTATION`
- ConsultationAgent：查日程时读 `AppointmentRepository`，不重新开填表

### 4.2 SessionSharedState 层
包：`com.yupi.yuaiagent.sessionstate`

| 组件 | 职责 |
|------|------|
| `SessionSharedState` | appointments / activeGoal / lastHandoff / facts |
| `SessionSharedStateStore` | 按 chatId 文件持久化 |
| `SessionSharedStateService` | upsert 预约、recordHandoff、buildPromptInjection |

写入时机：预约创建成功 → `upsertAppointment`  
读取时机：`ContextInjectionService.buildCombinedInjection` → **所有专家** system 注入「会话共享状态」  
空 scratchpad 时：从 `AppointmentRepository.findByChatId` **回填**（兼容旧数据）

### 4.3 显式交接 + 修跨 Agent Memory
- Orchestrator 路由后：`recordHandoffDetailed` → 四象限 Packet + hop TTL + scope
- NACK（缺资产 / TTL / 踢皮球）→ 修复注入；严重时强制 GENERAL
- `lastAgentMemoryByChat` 记录上一任 memoryType
- `syncCrossAgentMemory(chatId, target, previous)` **previous 不再传错成自己**
- 详情：[agent-handoff-protocol.md](./agent-handoff-protocol.md)

### 4.4 人设层（已有，面试可串起来）
- `UserCompanionService`：个人伙伴，userId 级
- `DigitalEmployeeAppService`：当前数字员工，userId 级  
→ 与 Shared State 叠加：**人设长期 + 状态会话级 + 对话历史**

---

## 5. 架构一句话（画图口述）

```
用户消息
  → Orchestrator（路由 + handoff 写入 SharedState）
  → ContextInjection：画像 + 伙伴 + 数字员工 + SharedState + 近聊摘要 + Artifact
  → Specialist Agent（各有 ChatMemory，但能读共享结构化状态）
  → 领域写回：预约 → AppointmentRepo + SharedState
```

关键原则：**写窄读宽**——谁产生状态谁写；谁接棒谁读。

---

## 6. 面试追问速答

**Q：为什么不把完整历史同步给每个 Agent？**  
A：Token 贵、噪声大、易幻觉；结构化状态 + 短摘要更稳，也符合 context engineering。

**Q：Shared State 和四层记忆（滑动窗口/事实/摘要/经验）什么关系？**  
A：四层记忆偏「长期个性化」；Shared State 偏「本会话任务白板」。一个绑 user，一个绑 chat。

**Q：跨会话怎么查预约？**  
A：现状按 chatId；进阶按 userId 聚合 AppointmentRepo，再投影进 Shared State / 用户级事实库。

**Q：数字员工切换会丢上下文吗？**  
A：人设会变，但 Shared State 与 PersistentMessage 仍在；要避免「员工私有记忆」另起炉灶。

**Q：如何验收？**  
A：同会话：预约成功 → 切通用顾问问「我的日程」→ 注入块含预约编号与时间，回答不再要求重新报姓名。

---

## 7. 可背的电梯稿（30 秒）

「多 Agent 同会话最容易踩的坑是把聊天记录当成业务状态。我们拆成三层：消息真相源、会话 Shared Scratchpad、用户级人设。预约这类结构化结果写入 Shared State 并在路由时做显式 handoff，任意专家注入时都能读到，同时修了跨 Agent memory 同步把上一任传错的 bug。这样切换助理不会『失忆』，也符合业界分层记忆和 structured handoff 的做法。」
