# 面试防崩溃话术（WorkPilot）

> 原则：**宁可说「脚手架」，不要说「已上线」却答不出调用链。**

## 30 秒项目定位

全场景职场 AI 智囊：Orchestrator 按意图路由到简历/谈薪/离职/预约/通用顾问；多意图时并行专家辩论再综合；带 Trace、质量审查、HITL、路由评测门禁。

## 高频追问 → 诚实答案

| 面试官问 | 你怎么答 |
|---------|---------|
| 这是真 Multi-Agent 吗？ | **编排式协作**：单意图一个专家真 SSE；多意图并行辩论 + 综合 + 失败/低质换 GENERAL，黑板写 DEBATE/HANDOFF。不是 AutoGen 式常驻辩论群。 |
| SSE 是真流式吗？ | **单意图是** token 级 `chatStream`；多意图先并行算完再推送（会发 `agent-progress`）。 |
| Workflow / Paradigm / DataEmployee？ | **脚手架**：类与 Bean 在，主聊天路径未跑 WorkflowMatcher.execute / ParadigmService / produce()。FEATURES 标了 `[脚手架]`。 |
| 怎么证明变好了？ | `POST /api/eval/gate/routing-suite`（KeywordRouter，零 LLM，ADMIN）；内容 live 另开，费 token。 |
| 工具安全？ | SSRF：`UrlSafetyGuard`；终端/日历：HITL 审批；沙箱执行。 |
| RAG 有来源吗？ | Resume 提示词强制引用文档名；空检索明确说未命中再通用回答。 |
| 成本怎么算？ | SSE `usage` 给 approxChars/approxTokens 估算；UsageTracker 记请求；模型精确 token 依赖厂商回传，未全量接。 |
| DATA_QUERY？ | **未接业务库**：映射 GENERAL + 诚实说明，不编数字。 |
| 记忆？ | 四层 MemoryCoordinator + Reflexion 按意图注入失败教训。 |

## 演示路径（建议背）

1. 登录（local：`application-local.yml`）→ 问「优化简历」→ 看真流式 + Trace  
2. 问「改简历并谈薪」→ `PARALLEL_DEBATE` + Artifact  
3. 故意触发终端命令 → HITL approve  
4. Admin 跑 `/eval/gate/routing-suite`

## 绝对别说

- 「我们有 33 层都生产就绪了」  
- 「多 Agent 一直在辩论投票」  
- 「Token 计量和 OpenAI 一样精确」  
- 「数据查询已经接了公司数仓」
