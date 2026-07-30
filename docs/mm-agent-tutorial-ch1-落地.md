# 多模态 Agent 教程落地笔记（mm_agent_tutorial Ch1 → WorkPilot）

> 来源：[第 1 章 多模态智能体概览](https://zsc.github.io/mm_agent_tutorial/chapter1.html)  
> 落地日期：2026-07-29  
> 原则：教程讲 VLM 闭环；WorkPilot 以职场文本 Agent 为主，**只吸收可提高可靠性 / 可扩展感知的工程点**。

---

## 1. 对照结论

| 教程概念 | WorkPilot 原有能力 | 本次落地 |
|---------|-------------------|---------|
| 动词法则 / L3–L4 Agent | Orchestrator + Manus ReAct | 已具备，未改范式 |
| Memory / Brain / Tools | 四层记忆 + 范式 + 工具/沙箱 | 已具备 |
| **每轮重插 Goal** | SharedState.activeGoal（弱） | **Goal Anchor** |
| **连续失败熔断** | LoopDetector / CircuitBreaker | **ConsecutiveFailureGuard → HITL** |
| Perception 降维 | 几乎无（知识库仅 MD） | **DocumentPerceptionService** |
| 感知幻觉交叉验证 | 无 | **PerceptionCrossValidator** |
| 混合检索（Text+Vision） | 纯文本 RAG | **TextFirstHybridRetrieval** 桩 |
| 视觉 Prompt Injection | 文本 `PromptInjectionDetector` | **VisualPromptSanitizer** |

---

## 2. 优先级与改动清单

### P0 — Goal 每轮重插

**问题**：长对话 / ReAct 多步后模型忘掉最初任务（教程 Gotcha「上下文遗忘」）。

**实现**：

| 组件 | 路径 | 行为 |
|------|------|------|
| `GoalAnchor` | `agent/goal/GoalAnchor.java` | 生成 `【本轮任务目标 Goal Anchor】` 块 |
| `ContextInjectionService` | 注入链最前拼接 Goal | 专家 Agent 每轮可见 |
| `OrchestratorAgent` | 会话无 activeGoal 时用本轮用户话种入 | 持久化目标 |
| `ToolCallAgent.think()` | 每步把 Goal 挂回 `system` | Manus 内循环不忘目标 |
| `AiController.doChatWithManus` | `setTurnGoal(...)` | 超级智能体同款 |

**配置**：无额外开关；依赖现有 `SessionSharedState.activeGoal`。

---

### P0 — 连续工具失败 → 终止 / HITL

**问题**：报错→重试死循环烧 Token（教程 Gotcha）。

**实现**：

| 组件 | 路径 | 行为 |
|------|------|------|
| `ConsecutiveFailureGuard` | `guard/ConsecutiveFailureGuard.java` | 计数；达阈值 `shouldStop()` |
| `ToolCallAgent` | think 异常 / TIMEOUT / 非 NORMAL 结果 | 累计；成功清零；触发则 `FINISHED` + 可选 `HumanHandoffService.park` |
| `HitlProperties` | `maxConsecutiveToolErrors` | 默认 3 |

**配置**（`application.yml`）：

```yaml
app.hitl.max-consecutive-tool-errors: ${HITL_MAX_CONSECUTIVE_TOOL_ERRORS:3}
```

---

### P1 — 简历 / Offer Perception 预处理

**问题**：把 PDF/截图直接丢给 VLM 又贵又吵；应先降维成语义流（教程 1.2 Perception + Rule 1.2 预算感知）。

**实现**：

| 组件 | 路径 | 行为 |
|------|------|------|
| `DocumentPerceptionService` | `perception/` | PDF(PDFBox 文字层) / txt / 图片净化 |
| `ResumeOfferStructurer` | 启发式抽 email/phone/薪资/学历 | 无 LLM |
| `PerceptionAppService` + `PerceptionController` | 分层 API | 见下 |

**API**（context-path `/api`）：

```http
POST /api/perception/preprocess
Content-Type: multipart/form-data
file: <简历.pdf|offer.txt|截图.png>
hint: resume | offer
```

响应关键字段：`promptBlock`、`structuredFields`、`confidence`、`injectionRisk`、`boundToSession`。

**职场顾问推荐路径（与代码一致）**：

```http
POST /api/perception/preprocess-and-bind
Content-Type: multipart/form-data
file + hint=resume|offer + chatId
Authorization: Bearer <token>
```

→ 写入 SharedState → 前端发短消息（默认「请帮我优化这份简历…」）→ 感知路由 / Keyword → ResumeAgent。

**不要**把整段 `promptBlock` 拼进 EventSource GET（URL 会爆）。

**格式支持**

| 格式 | 能否分析 | 说明 |
|------|----------|------|
| `.txt` / `.md` | ✅ | 直接读文本 |
| `.pdf`（可选中文字） | ✅ | PDFBox 抽文字层 |
| `.pdf`（扫描件） | ❌ | 无 OCR，请另存文字版或粘贴正文 |
| `.docx` | ✅ | Apache POI 抽正文 |
| `.doc`（老 Word） | ❌ | 请另存 `.docx` |
| 图片 | ⚠️ | 仅净化，暂无 OCR |

**推荐路径（职场顾问）**：避免 SSE GET URL 过长，材料写入 Shared State。

1. 启动后端 `:8123` + 前端 `:3000`
2. 打开 `/chat/career`，登录并进入会话
3. 点 📎 上传 `resume.txt` / 有文字层的 PDF，类型选「简历」或「Offer」
4. 可选补充一句需求，点发送
5. 预期：
   - 输入栏上方出现「感知完成 · 置信度 xx%」
   - Network：`POST /api/perception/preprocess-and-bind`
   - 随后 SSE：`GET /api/ai/orchestrator/chat?message=请帮我优化这份简历…`（短消息，含意图词）
   - 日志可见 `路由感知路径：perception=RESUME` 或 Keyword 快路径
   - Agent 回复应引用结构化字段（邮箱/手机/薪资等）

**curl 烟测**：

```bash
curl -X POST "http://localhost:8123/api/perception/preprocess" ^
  -F "file=@resume.txt" -F "hint=resume"
```

**超级智能体**：同样可上传；因无会话绑定，会截断后拼进 Manus GET 消息（长简历请用职场顾问页）。

**限制**：扫描件 PDF / 纯图暂无 OCR（notes 会提示）；后续可接 PaddleOCR / 云 OCR，不改 Agent 接口。

---

### P2 — 混合检索 + 交叉验证钩子

| 组件 | 路径 | 用途 |
|------|------|------|
| `HybridRetrievalStrategy` / `TextFirstHybridRetrieval` | `rag/hybrid/` | 文本 Top-K + 视觉 Caption Top-1；`toPromptContext` |
| `PerceptionCrossValidator` | `perception/` | 假设 vs 工具观测（数值 2% 容差） |
| `POST /api/perception/cross-check` | hypothesis + observed | 联调 / 评测 |

接入真实 RAG 时：在向量召回后调用 `TextFirstHybridRetrieval.retrieve(...)`，**只把 Top-1 图**留给未来 VLM。

---

### P3 — 视觉注入防护（桩 + 文本扫）

| 组件 | 路径 | 行为 |
|------|------|------|
| `VisualPromptSanitizer` | 图片重采样 + JPEG 压缩 | 破坏对抗像素 |
| 同组件 | OCR/提取文本走 `PromptInjectionDetector` + scrub | 屏蔽 Ignore previous… 等 |

挂在 `DocumentPerceptionService.perceive` 路径上，有图/有文即生效。

---

## 3. 架构位置（OODA 映射）

```
Environment (上传文件 / 用户对话)
        │
   (1) Observe  → DocumentPerceptionService / VisualPromptSanitizer
        │
   (2) Orient   → GoalAnchor + ContextInjection + HybridRetrieval（可选）
        │
   (3) Decide   → Orchestrator / Manus (Brain)
        │
   (4) Act      → Tools + ConsecutiveFailureGuard
        │
   (5) Wait     → ToolResultClassifier / CrossValidator / HITL
```

分层约束未破：`PerceptionController` → `PerceptionAppService` → `perception/*`。

---

## 4. 测试

```bash
mvn -Dtest=GoalAnchorTest,ConsecutiveFailureGuardTest,DocumentPerceptionServiceTest,PerceptionCrossValidatorTest,TextFirstHybridRetrievalTest test
```

---

## 5. 后续可选（未做）

1. 前端 CareerAdvisor 上传按钮 → 自动拼 `promptBlock`
2. 扫描件 OCR 适配器（保持 `DocumentPerceptionService` 接口）
3. 将 `TextFirstHybridRetrieval` 接入 `DocumentAppService` / Query 链路
4. Manus 路径补 `PromptInjectionDetector`（与 Orchestrator 对齐）
5. 图表读数：沙箱 `pandas` 读 Excel + `cross-check` 自动调用

---

## 6. 相关代码地图

- Goal：`agent/goal/`、`ContextInjectionService`、`ToolCallAgent`
- 熔断：`guard/ConsecutiveFailureGuard.java`、`hitl/HitlProperties`
- 感知：`perception/*`、`service/PerceptionAppService.java`、`controller/PerceptionController.java`
- 混合检索：`rag/hybrid/*`
