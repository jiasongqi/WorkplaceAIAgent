# 多模态 Agent 教程落地笔记（mm_agent_tutorial Ch5 → WorkPilot）

> 来源：[第 5 章 记忆与知识：RAG、多模态检索与状态管理](https://zsc.github.io/mm_agent_tutorial/chapter5.html)  
> 落地日期：2026-07-30  
> 原则：记忆分层已有 L27；本次完成 **P0–P2** RAG 统一、L4 query 修复、感知混合检索、时间衰减、Map-Reduce、上传 TTL。

---

## 1. 对照结论

| 教程概念 | WorkPilot 原有 | 落地状态 |
|---------|---------------|---------|
| L1/L2/L3 三级记忆 | L27 四层 + SessionSharedState | ✅ 保留 |
| L2→L3 经验检索 query | L4 用 `conversationId` 当 query | ✅ **P1-a `ExperienceQueryBuilder`** |
| Query Rewriting + Rerank | 分散、Rerank 未接线 | ✅ **P0 `RetrievalPipeline`** |
| 多路 Hybrid 检索 | `TextFirstHybridRetrieval` 仅单测 | ✅ **P1-b 感知混合注入** |
| 文档时间衰减 | 无 | ✅ **P1-c `indexedAt` + Rerank decay** |
| ResumeAgent RAG Advisor | 纯 VectorStore Advisor | ✅ **P1-d `PipelineRagAdvisorFactory`** |
| 长 PDF Map-Reduce | 全文直塞 | ✅ **P2 `LongDocumentSummarizer`** |
| 图片 Caption | OCR 未启用 | ✅ **P2 `ImageCaptionService`（轻量 LLM 推测）** |
| 上传 TTL | 无 | ✅ **P2 `SessionUploadStore` + 定时清理** |
| 过度检索循环 | 无 | ✅ **P0 `RagRetrievalAttemptTracker`** |

---

## 2. 改动清单

### P0 — 统一 RAG Pipeline（已完成）

| 组件 | 路径 |
|------|------|
| `RetrievalPipeline` | `rag/RetrievalPipeline.java` |
| `RagTool` + Tool 注册 | `rag/RagTool.java` · `tools/ToolRegistration.java` |
| `RagRetrievalAttemptTracker` | `rag/RagRetrievalAttemptTracker.java` |

### P1-a — L4 经验检索 query

| 组件 | 行为 |
|------|------|
| `ExperienceQueryBuilder` | 当前用户消息 → `KeyInfoExtractor`；不足则读 L3 摘要 |
| `MemoryCoordinator` | `assembleContext(..., currentUserMessage)` |
| `OrchestratorAgent` / `GeneralCareerAgent` | 传入本轮 `message` |

### P1-b — Perception Hybrid 注入

| 组件 | 行为 |
|------|------|
| `PerceptionHybridContextService` | 从 SharedState 感知块拆 TextHit/VisionRef → `TextFirstHybridRetrieval` |
| `ContextInjectionService` | 在 artifact 注入后追加 hybrid 块 |

### P1-c — 时间衰减

| 组件 | 行为 |
|------|------|
| `RerankService` | `score *= blend(timeDecayFactor(indexedAt))` |
| `DocumentAppService` / `AiChatDocumentLoader` | chunk metadata 写入 `indexedAt` |
| 配置 | `rag.rerank.time-decay-enabled` |

### P1-d — ResumeAgent Pipeline RAG

| 组件 | 行为 |
|------|------|
| `PipelineDocumentRetriever` | 实现 Spring AI `DocumentRetriever` |
| `PipelineRagAdvisorFactory` | 创建 `RetrievalAugmentationAdvisor` |
| `ResumeAgent` | 改用 Pipeline Advisor + QueryRewriter |

### P2 — 感知增强与生命周期

| 组件 | 行为 |
|------|------|
| `LongDocumentSummarizer` | 超 12k 字符 → Map-Reduce 摘要 |
| `ImageCaptionService` | 图片 filename/hint → LLM 推测 caption |
| `SessionUploadStore` | bind 时落盘；7 天 TTL；`@Scheduled` 清理 |
| `DocumentPerceptionService` | 接入 summarizer + caption |
| `PerceptionAppService` | 保存 uploadRef 到 SharedState facts |

---

### P3 — 知识库 PDF 表格结构化（MVP）

| 组件 | 行为 |
|------|------|
| `PdfTableHeuristicExtractor` | PDFBox 3 按页检测多列对齐行 → 表格块 |
| `TableToMarkdownConverter` | 表格 → GitHub Markdown（向量库可读） |
| `TableJsonSidecarStore` | JSON 侧车 `./tmp/knowledge/tables/` |
| `PdfKnowledgeIngestionService` | 正文 chunk + 整表 chunk（`chunkType=table`） |
| `DocumentAppService` | 支持 `.pdf` 上传 |
| `KnowledgeBase.vue` | 双主题 · 文件/文本上传 · 分类 · 筛选搜索 · API 封装 · 重试重传 |

### P3-b — 知识库前端补全（2026-07-30）

| 能力 | 说明 |
|------|------|
| 路由 | `/knowledge` + `requiresAuth: true` |
| 主题 | sage（青荷绿）/ dark 全套 CSS 变量 |
| 上传 | 拖拽 + 多文件队列；`uploadDocument(file, status)` |
| 文本入库 | `addTextDocument` 粘贴 Markdown |
| 列表 | 状态 chip 筛选、文件名搜索、indexedAt/重试次数展示 |
| 删除 | 确认弹窗 + `deleteDocument` 错误提示 |
| 重试 | `FAILED_*` → 引导选择原文件重新上传（dedup） |

---

## 3. 未做（诚实边界）

| 项 | 说明 |
|----|------|
| 真 VLM 像素级 OCR | 当前 caption 为 filename/hint 轻量推测；可换 qwen-vl |
| CLIP/SigLIP 向量 | VisionRef 仍为 caption 关键词匹配 |
| PDF 表格（增强） | Tabula / 云 DocMind；扫描件 OCR；复杂合并单元格 |
| 删文档 purge 向量 | 软删除 metadata，向量库 chunk 暂未清理 |
| PII 人脸打码 | 仅有 `VisualPromptSanitizer` 注入防护 |

---

## 4. 验证

```bash
mvn "-Dtest=PdfTableHeuristicExtractorTest,TableToMarkdownConverterTest" test
```

```bash
mvn "-Dtest=RetrievalPipelineTest,RagRetrievalAttemptTrackerTest,ExperienceQueryBuilderTest,RerankServiceTimeDecayTest,TextFirstHybridRetrievalTest" test
```

手工：

1. 上传简历 PDF → 问「总结全文」→ 应走 Map-Reduce 而非超长原文  
2. 上传图片 + 问相关问题 → Context 含 `【混合检索 Hybrid Retrieval】`  
3. Day2 问「昨天那个零件扭矩」→ L4 经验 query 含消息关键词  
4. 知识库检索 → 较新文档排名靠前（time decay）
5. 知识库上传含表格的 PDF → 向量 chunk 含 `chunkType=table` 与 Markdown 表

---

## 5. 配置速查

| 配置项 | 默认 |
|--------|------|
| `rag.pipeline.max-empty-retries` | 2 |
| `rag.rerank.time-decay-enabled` | true |
| `perception.map-reduce.char-threshold` | 12000 |
| `perception.upload.ttl-days` | 7 |
| `perception.hybrid.top-text` / `top-vision` | 3 / 2 |

---

## 相关代码

- `memory/ExperienceQueryBuilder.java` · `MemoryCoordinator.java`
- `rag/RetrievalPipeline.java` · `PipelineRagAdvisorFactory.java`
- `perception/PerceptionHybridContextService.java` · `LongDocumentSummarizer.java` · `SessionUploadStore.java`
- `agent/ContextInjectionService.java` · `ResumeAgent.java`
- `document/pdf/*` · `views/KnowledgeBase.vue` · `api/index.js`（document APIs）

**场景对照总览**：见 [mm-agent-tutorial-场景对照总结.md](./mm-agent-tutorial-场景对照总结.md)
