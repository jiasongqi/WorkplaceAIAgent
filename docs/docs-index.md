# 文档索引

> 最后更新：2026-08-18

## 快速入口

| 文档 | 说明 | 何时阅读 |
|------|------|----------|
| [WIKI.md](./WIKI.md) | 项目完整 Wiki（中文：架构 + 功能 + API + 数据） | 新人入门、功能查阅 |
| [WIKI.en.md](./WIKI.en.md) | Project Wiki (English) | Onboarding in English |
| [FEATURES.md](./FEATURES.md) | L0–L34 功能分层（含 Perception） | 能力地图、排期对照 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 架构设计说明 | 架构评审 |
| [**mm-agent-tutorial-场景对照总结.md**](./mm-agent-tutorial-场景对照总结.md) | **教程 Ch1–Ch10 → WorkPilot 用户路径 / 代码 / 面试** | **学教程、写文档、备面试** |
| [agent-handoff-protocol.md](./agent-handoff-protocol.md) | Handoff Packet / NACK / TTL / Human 异步 / Manifest | 多 Agent 协作协议 |
| [interview-multi-agent-session-state.md](./interview-multi-agent-session-state.md) | 同会话切换 Agent · 面试稿 | 面试准备、Shared State |
| [mm-agent-tutorial-ch1-落地.md](./mm-agent-tutorial-ch1-落地.md) | 多模态教程 Ch1 → Goal Anchor / Perception / HITL 熔断 | 可靠性与感知层扩展 |
| [mm-agent-tutorial-ch3-落地.md](./mm-agent-tutorial-ch3-落地.md) | Ch3 → Tool Schema / 并行 / 幂等 / Submit-Poll | 工具工程 |
| [mm-agent-tutorial-ch4-落地.md](./mm-agent-tutorial-ch4-落地.md) | Ch4 → Loop Wrap-up / Replanner / Claim Guard | Agent Loop |
| [mm-agent-tutorial-ch5-落地.md](./mm-agent-tutorial-ch5-落地.md) | Ch5 → RAG Pipeline / 知识库 PDF / KnowledgeBase 页 | 记忆与知识库 |
| [interview-perception-goal-reliability.md](./interview-perception-goal-reliability.md) | 感知/Goal/熔断 · 术语 · STAR 踩坑话术 | 面试叙事、解释联调问题 |
| [INTERVIEW_QA_SKILL.md](./INTERVIEW_QA_SKILL.md) | 50+ 面试题手册（含 Ch5 RAG / 知识库） | 系统背题 |
| [INTERVIEW-DEFENSE.md](./INTERVIEW-DEFENSE.md) | 诚实边界防崩溃 | 演示前必看 |
| [workpilot-plugin-platform-refactor-plan.md](./workpilot-plugin-platform-refactor-plan.md) | 平台插件化迁移（`platform.*` 开关） | 灰度 Manifest/Runner/Permission |
| [TODO.md](./TODO.md) | 未完成任务汇总 | 领任务、查进度 |

> **桌面萌宠**：`CompanionPet`（全局悬浮）· `CatPet`/`PilotPet` · `PetRoom` 随 sage/dark 主题切换（`--pet-room-*` CSS 变量）。设置在职场顾问「我的伙伴」抽屉。

## 阅读路径

**新人入门**：WIKI.md §1 → §3（含伙伴 / 数字员工 / 反馈闭环）→ §4.1 → §10 截图

**English onboarding**：WIKI.en.md §1 → §3 → §8 screenshots → README.en.md

**前端开发**：WIKI.md §10 → CareerAdvisor 伙伴/员工/萌宠 UI → `components/companion/*` · `useTheme.js` · KnowledgeBase.vue → API §11

**后端开发**：WIKI.md §3.10–3.13 → §4 → §11–§12 → `rag/RetrievalPipeline` · `document/pdf/*`

**架构评审**：WIKI.md §2 → §4 → Trace / HITL / 存储

**教程 → 项目对照**：mm-agent-tutorial-场景对照总结.md → 对应 ch*-落地.md

**可靠性 / 感知层**：mm-agent-tutorial-ch1-落地.md → interview-perception-goal-reliability.md → INTERVIEW_QA_SKILL §六–§七

**RAG / 知识库**：mm-agent-tutorial-ch5-落地.md → FEATURES L1 → `/knowledge` 页联调

**面试准备**：INTERVIEW-DEFENSE.md → mm-agent-tutorial-场景对照总结.md → INTERVIEW_QA_SKILL.md

## 截图资源

位于 [`assets/`](./assets/)：业务页截图 + 主题 **sage / dark**（`screenshot-theme-sage.png` · `screenshot-theme-dark.png`）。胶囊主题为原型（`prototypes/`，未接入正式 UI）。

重新生成：

```bash
BASE=http://localhost:3000 node yu-ai-agent-frontend/scripts/capture-docs-screenshots.mjs
node yu-ai-agent-frontend/scripts/capture-theme-prototypes.mjs
```
