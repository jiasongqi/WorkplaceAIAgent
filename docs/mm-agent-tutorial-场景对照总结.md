# mm_agent_tutorial → WorkPilot 场景对照总结

> 教程目录：[基于多模态理解生成模型的智能体构建教程](https://zsc.github.io/mm_agent_tutorial/)  
> 更新：2026-07-30  
> 用途：把教程各章概念映射到 **WorkPilot 真实代码与用户路径**，供开发、文档、面试共用。

---

## 1. 怎么读这份对照

| 你想… | 读什么 |
|--------|--------|
| 快速知道「教程学了什么、项目用在哪」 | 本文 §2 总表 + §3 用户场景 |
| 改某块代码 | 对应章节的 `mm-agent-tutorial-ch*-落地.md` + `.cursor/codemaps/` |
| 准备面试 | `INTERVIEW_QA_SKILL.md` Q6/Q52/Q71–Q75 + `interview-perception-goal-reliability.md` |
| 演示前防夸大 | `INTERVIEW-DEFENSE.md` + 各章「未做/诚实边界」 |

**WorkPilot 定位**：职场文本 Agent 为主（简历/谈薪/离职/咨询），吸收教程的 **可靠性、RAG、工具、Loop、Handoff** 工程方法；不做 Browser/VLA/自动驾驶全栈。

---

## 2. 教程章节 → 项目落地总表

| 章 | 教程核心 | WorkPilot 实际场景 | 关键代码 / 页面 | 落地笔记 |
|----|----------|-------------------|----------------|----------|
| **Ch1** 多模态 Agent 概览 | 感知→推理→决策→执行→反馈；可靠性/成本 | 用户上传简历 PDF → 结构化进会话；ReAct 不忘目标；连续失败熔断 | `DocumentPerceptionService` · `GoalAnchor` · `ConsecutiveFailureGuard` | [ch1-落地](./mm-agent-tutorial-ch1-落地.md) |
| **Ch2** 多模态 I/O 与上下文 | 长文档 Map-Reduce；表格结构化；chunk 分层 | 超长 PDF 摘要后注入；知识库 PDF 表格 → Markdown chunk；会话上传 7 天 TTL | `LongDocumentSummarizer` · `PdfKnowledgeIngestionService` · `SessionUploadStore` | [ch1](./mm-agent-tutorial-ch1-落地.md) · [ch5](./mm-agent-tutorial-ch5-落地.md) |
| **Ch3** Tool Call | Schema 工程；并行；幂等；Submit-Poll；副作用分级 | 超级智能体查网/读文件/搜知识库；写文件/终端需 HITL；只读工具超时可重试 | `RagTool` · `ParallelToolCallingSupport` · `ToolIdempotencyStore` | [ch3-落地](./mm-agent-tutorial-ch3-落地.md) |
| **Ch4** Agent Loop | ReAct；P&E；Reflect；Wrap-up；Depth Limit | Manus 多步工具；Plan-and-Solve 失败 Replanner；步数耗尽部分结论 | `ToolCallAgent` · `LoopWrapUp` · `PlanAndSolveAgent` · `StepReflector` | [ch4-落地](./mm-agent-tutorial-ch4-落地.md) |
| **Ch5** 记忆与 RAG | Query Rewrite；Hybrid；时间衰减；防检索循环 | 职场顾问/简历 Agent 查内置+上传文档；经验记忆用消息关键词检索；知识库页上传 MD/PDF | `RetrievalPipeline` · `MemoryCoordinator` · `KnowledgeBase.vue` | [ch5-落地](./mm-agent-tutorial-ch5-落地.md) |
| **Ch6** Handoff | Handoff Packet；人类移交 | 多 Agent 路由 + Artifact 黑板；HITL 审批高危工具；`HumanHandoffService` | `OrchestratorAgent` · `agent-handoff-protocol.md` | [handoff 协议](./agent-handoff-protocol.md) |
| **Ch7** 消息协议 | 多模 content parts；tool result 边界 | SSE 流式事件；Trace 步骤；感知块与聊天传输解耦（bind API） | `TraceRecorder` · `PerceptionAppService` | ch1 bind 路径 |
| **Ch8** Multi-Agent | Manager-Worker；Blackboard | Orchestrator 唯一路由；子 Agent 不互调；ArtifactShelf 传递交付物 | `OrchestratorAgent` · `ArtifactShelf` | WIKI §4 |
| **Ch9** 仿真互动 | 闭环观测/动作 | **未做**（无仿真环境）；沙箱终端/本地进程近似「受控执行」 | `Sandbox` L19 | FEATURES L19 |
| **Ch10** Trace / 评测 | 可回放 trace；回归集 | 前端 Trace 时间线；EvalCenter routing-suite；反馈 thumbs | `TraceDetail.vue` · `EvalCenter` | WIKI §14 |
| **Ch11** DeepResearch PDF | 版面/表格/引用 | 知识库 PDF 启发式表格 + sidecar JSON；**无**页码可点击引用 | `document/pdf/*` | ch5 P3 |
| **Ch17** 文档 RPA | 字段抽取 + 人工复核 | 简历/Offer 结构化字段 + 交叉验证；知识库分类标签 | `ResumeOfferStructurer` · `PerceptionCrossValidator` | ch1 P1 |

---

## 3. 四条真实用户路径（串联理解）

### 路径 A：职场顾问 + RAG 问答

```
用户登录 → /chat/career → 「试用期被辞退有补偿吗？」
  → NLU 路由 GeneralCareerAgent
  → MemoryCoordinator 注入 L1–L4（L4 query = 当前消息关键词）
  → RetrievalPipeline：改写 → Multi-Query/HyDE → Rerank（含 indexedAt 时间衰减）
  → 流式 SSE 返回答案（可 Trace 回放）
```

**对应教程**：Ch5 RAG + Ch1 记忆分层 + Ch4 ReAct 单轮专家。

### 路径 B：上传简历（感知，非知识库）

```
用户 📎 上传 resume.pdf → preprocess-and-bind
  → DocumentPerceptionService：PDFBox 解析 + 超长 Map-Reduce 摘要
  → ImageCaptionService（filename 轻量 caption）
  → 写入 SessionSharedState.lastPerceptionBlock
  → 用户短句「帮我改简历」→ ResumeAgent + PipelineRagAdvisor
```

**对应教程**：Ch1 感知降维 + Ch2 长文档策略 + Ch5 Pipeline RAG。

**易混点**：会话附件走 **Perception**；持久化进向量库走 **知识库页** `/knowledge`。

### 路径 C：知识库管理（运营/个人文档）

```
用户 → /knowledge（需登录，双主题 sage/dark）
  → 选分类（通用/职场/简历…）→ 上传 .md 或 .pdf
  → DocumentAppService：dedup → embed → vectorStore
  → PDF：PdfTableHeuristicExtractor → Markdown 表 chunk（chunkType=table）
  → 列表筛选/搜索/删除；失败项「重新上传」
```

**对应教程**：Ch2 表格结构化 + Ch5 语料入库 + Ch17 文档自动化（MVP 级）。

### 路径 D：超级智能体 + 工具

```
用户 → /chat/super → 「搜一下 2025 劳动法试用期规定并总结」
  → Manus ReAct：searchWeb → scrapeWebPage / searchKnowledgeBase
  → Goal Anchor 每步重挂；连续失败 → ConsecutiveFailureGuard
  → 步数耗尽 → LoopWrapUp 部分结论
```

**对应教程**：Ch3 工具 + Ch4 Loop + Ch1 Goal/熔断。

---

## 4. 按教程主题的「学到了什么 → 项目怎么用」

### 4.1 可靠性（Ch1 + Ch4 + Ch3）

| 教程 Gotcha | 项目对策 | 面试怎么说 |
|-------------|----------|------------|
| 上下文遗忘 Goal | `GoalAnchor` 每轮/每步重插 | 「System Prompt 静态，Goal 动态 grounding」 |
| 连续失败烧 Token | `ConsecutiveFailureGuard` + 只读工具才自动重试 | 「重复动作 vs 连续失败，两个 detector 正交」 |
| 假完成「我已经发送了」 | `CompletionClaimGuard` | 「没有成功 Tool Output 不能声称写入」 |
| 副作用重复执行 | `ToolIdempotencyStore` + HITL | 「写文件/终端不盲重试」 |

### 4.2 感知与文档（Ch1 + Ch2 + Ch11）

| 教程概念 | 项目实现 | 边界 |
|----------|----------|------|
| 视觉摘要 | `ImageCaptionService`（hint/filename） | 非真 VLM 像素理解 |
| 长 PDF | `LongDocumentSummarizer` Map-Reduce | 阈值 `perception.map-reduce.char-threshold` |
| 表格结构化 | `PdfTableHeuristicExtractor` + sidecar JSON | 复杂版式/扫描件需 Tabula/OCR |
| Hybrid 检索 | `PerceptionHybridContextService` + 桩 | 无 CLIP 向量 |

### 4.3 RAG 与记忆（Ch5）

| 教程概念 | 项目实现 | 配置 |
|----------|----------|------|
| 统一检索管线 | `RetrievalPipeline` | `rag.pipeline.*` |
| 防空检索循环 | `RagRetrievalAttemptTracker` | `max-empty-retries: 2` |
| L4 经验 query | `ExperienceQueryBuilder` | 用消息关键词，非 conversationId |
| 时间衰减 | `RerankService` + `indexedAt` | `rag.rerank.time-decay-enabled` |
| 动态语料 | `POST /document/upload` · `/document/add` | 前端 `/knowledge` |

### 4.4 工具与 Loop（Ch3 + Ch4）

| 场景 | 选哪个工具 | Schema 要点 |
|------|-----------|-------------|
| 外网实时事实 | `searchWeb` | 与 RAG/scrape 互斥写清 |
| 已知 URL 正文 | `scrapeWebPage` / async | Submit-Poll |
| 内置/上传文档 | `searchKnowledgeBase` | 注册进 `ToolRegistration` |
| 大文件 | `readFileChunk` + `file_id` | 不传全文进 Context |

---

## 5. 前端与 API 速查（知识库）

| 能力 | 前端 | API |
|------|------|-----|
| 页面 | `KnowledgeBase.vue` · `/knowledge` | — |
| 文件上传 | 拖拽/选择 · 分类下拉 | `POST /document/upload` |
| 粘贴文本 | 文本 Tab | `POST /document/add` |
| 列表/删 | 筛选 chip + 搜索 | `GET /document/list` · `DELETE /document/{docId}` |
| 主题 | `data-theme=sage\|dark` · AppLayout 切换 | — |
| API 客户端 | `listDocuments` · `uploadDocument` · `deleteDocument` · `addTextDocument` | `api/index.js` |

---

## 6. 诚实边界（演示 / 面试必提）

| 声称 | 实际 |
|------|------|
| 「多模态看懂图片」 | filename/hint caption，非 VLM |
| 「PDF 完美抽表」 | 启发式 MVP；扫描件/合并单元格弱 |
| 「删文档即删向量」 | 当前软删除 metadata，向量未 purge |
| 「Hybrid 图文检索」 | 文本优先 + VisionRef 关键词桩 |
| 「Browser Agent」 | 未做；Web 用 search/scrape 工具 |

---

## 7. 本地验证清单

```bash
# Java 21 必须
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot"
mvn spring-boot:run "-Dmaven.test.skip=true"

cd yu-ai-agent-frontend && npm run dev
# 前端 http://localhost:3000  · 后端 http://localhost:8123/api
```

| # | 操作 | 预期 |
|---|------|------|
| 1 | 登录后进 `/knowledge` 上传 `.md` | 列表状态 INDEXED，分类标签入库 |
| 2 | 上传含表格 PDF | chunk metadata 含 `chunkType=table` |
| 3 | 职场顾问问文档相关问题 | Trace 可见检索；较新文档排名靠前 |
| 4 | 顾问上传简历 bind | SharedState 有感知块，非知识库列表 |
| 5 | 切换 sage/dark 主题 | 知识库页颜色随 `--gold` / `--layer*` 变化 |

---

## 8. 相关文档索引

- 落地笔记：[ch1](./mm-agent-tutorial-ch1-落地.md) · [ch3](./mm-agent-tutorial-ch3-落地.md) · [ch4](./mm-agent-tutorial-ch4-落地.md) · [ch5](./mm-agent-tutorial-ch5-落地.md)
- 面试：[INTERVIEW_QA_SKILL.md](./INTERVIEW_QA_SKILL.md) · [interview-perception-goal-reliability.md](./interview-perception-goal-reliability.md)
- 架构：[FEATURES.md](./FEATURES.md) L1/L27 · [WIKI.md](./WIKI.md) §3.2
