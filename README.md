<div align="right">
  <b>🇨🇳 中文</b> | <a href="./README.en.md">🇺🇸 English</a>
</div>

<div align="center">
  <h1>WorkPilot</h1>
  <h3>🚀 全场景职场 AI 智囊</h3>
  <p><em>覆盖求职 → 谈判 → 在职成长 → 预约咨询 → 离职规划的全生命周期职场助手</em></p>

  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" alt="Java" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-green?logo=springboot" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Spring%20AI-1.0-blue" alt="Spring AI" />
  <img src="https://img.shields.io/badge/Vue.js-3-brightgreen?logo=vuedotjs" alt="Vue.js" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License" />
</div>

---

## 🎯 项目介绍

如果你已经会调 LLM API，却总觉得「真正能上线的 Agent 产品」仍隔着一层：意图怎么路由？记忆怎么分层？工具调用如何审计？高危操作如何人工确认？——**WorkPilot** 就是用一套可运行的全栈工程，把这些问题串成闭环。

WorkPilot 是一个**全场景职场 AI 智囊平台**。前端是 Vue 3 工作台，后端是 Java 21 + Spring Boot + Spring AI。用户消息进入 `OrchestratorAgent` 后，经 Keyword 快路径 / NLU 意图理解，路由到求职、谈薪、离职、预约、通用职场等专业子 Agent；同时具备四层记忆、工具调用、HITL 审批、执行轨迹 Trace、双存储（本地文件 / PostgreSQL）等工程能力。

它适合：

- 想做一个**可演示、可联调**的 Multi-Agent 作品集项目
- 想系统理解 **Orchestrator + 子 Agent + Memory + Tool + HITL** 的落地方式
- 需要中文职场场景（简历、涨薪、离职、预约咨询）做产品化练习

---

## 📚 快速开始

### 前置条件

- JDK **21+**（Windows 若默认是 17，请设置 `JAVA_HOME` 指向 JDK 21）
- Node.js **18+**
- [DashScope API Key](https://dashscope.aliyun.com/)（请关闭「仅免费额度」或确保账户有余额）

### 1. 克隆项目

```bash
git clone https://github.com/jiasongqi/WorkplaceAIAgent.git
cd WorkplaceAIAgent
git checkout java-v3-db   # 或 feat/consultation-chat-ux 等目标分支
```

### 2. 配置环境变量

```powershell
$env:DASHSCOPE_API_KEY = "your-dashscope-key"
$env:JWT_SECRET = "change-me-to-a-long-random-string"
# 可选：$env:STORAGE_TYPE = "file"   # file=本地 ./tmp；jdbc=PostgreSQL
```

> API Key / 数据库密码请用环境变量注入，**不要**写进仓库。

### 3. 启动后端

```bash
mvn spring-boot:run -DskipTests
# → http://localhost:8123/api
# 健康检查：curl http://localhost:8123/api/health
```

### 4. 启动前端

```bash
cd yu-ai-agent-frontend
npm install
npm run dev
# → http://localhost:3000
```

### 5. 第一次使用

1. 打开 http://localhost:3000/login  
2. **注册**账号，或本地游客：用户名 `游客` / 密码 `workpilot-local`（也可点「一键游客登录」）  
3. 进入首页 → **职场顾问**，发送例如：`帮我优化一段 Java 后端简历亮点`

### 可选：Docker 起 PostgreSQL

```bash
docker compose up -d postgres
$env:STORAGE_TYPE = "jdbc"
$env:DB_URL = "jdbc:postgresql://localhost:5432/workpilot"
$env:DB_USERNAME = "workpilot"
$env:DB_PASSWORD = "workpilot123"
mvn spring-boot:run -DskipTests
```

一键编排（Postgres + Redis + App）：

```bash
docker compose up -d
```

---

## ✨ 你将收获什么？

- 🧭 **Multi-Agent 路由** — Keyword 快路径 + NLU，5 个专业子 Agent（求职 / 谈薪 / 离职 / 预约 / 通用）
- 🧠 **四层记忆系统** — 滑动窗口 · 事实 · 摘要 · 向量经验，理解长期对话如何控 Token
- 🔧 **工具与 MCP** — 搜索、抓取、文件、终端、PDF，以及外部 MCP 能力扩展
- 🙋 **HITL 人工审批** — 终端、日历等高危操作先确认再执行
- 📊 **Trace 与评测** — 执行轨迹可视化，方便联调、答辩与回归
- 🗄️ **双存储工程实践** — `file` 本地演示 / `jdbc` PostgreSQL + Flyway
- 🖥️ **可运行产品界面** — 登录、工作台、职场顾问、超级智能体、知识库

---

## 📖 内容导航

| 章节 | 关键内容 | 状态 |
|------|----------|------|
| **一、上手与产品体验** | | |
| [快速开始](#-快速开始) | 环境、启动、首次对话 | ✅ |
| [产品截图](#-产品截图) | 登录 / 工作台 / 顾问 / Manus / 知识库 | ✅ |
| **二、架构与核心能力** | | |
| [系统架构](#-系统架构) | 分层架构与 Agent 路由图 | ✅ |
| [功能地图](#-功能地图) | 核心能力一览 | ✅ |
| [docs/FEATURES.md](docs/FEATURES.md) | L0–L33 完整功能分层 | ✅ |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 架构设计说明 | ✅ |
| **三、深入专题** | | |
| [docs/WIKI.md](docs/WIKI.md) | 项目完整 Wiki | ✅ |
| [docs/nlu-layer-design-v4.2.md](docs/nlu-layer-design-v4.2.md) | NLU 意图理解管道设计 | ✅ |
| [docs/multi-agent-runtime-architecture.md](docs/multi-agent-runtime-architecture.md) | 多 Agent 运行时架构 | ✅ |
| [docs/plan-auth-sse-storage.md](docs/plan-auth-sse-storage.md) | 鉴权 · SSE · 存储方案 | ✅ |
| **四、面试与答辩** | | |
| [docs/INTERVIEW-DEFENSE.md](docs/INTERVIEW-DEFENSE.md) | 面试答辩话术 | ✅ |
| [docs/INTERVIEW_QA_SKILL.md](docs/INTERVIEW_QA_SKILL.md) | 面试问答技能整理 | ✅ |
| [docs/PROJECT_HIGHLIGHTS.md](docs/PROJECT_HIGHLIGHTS.md) | 项目亮点提炼 | ✅ |
| **五、学习笔记** | | |
| [docs/hello-agents-study.md](docs/hello-agents-study.md) | Hello-Agents 学习对照 | ✅ |
| [docs/HELLO_AGENTS_SUMMARY.md](docs/HELLO_AGENTS_SUMMARY.md) | Hello-Agents 要点总结 | ✅ |

---

## 🖼 产品截图

<p align="center">
  <img src="docs/assets/screenshot-login.png" width="720" alt="登录页" />
</p>
<p align="center"><sub>深色登录页 · 登录 / 注册 / 一键游客</sub></p>

<p align="center">
  <img src="docs/assets/screenshot-home.png" width="720" alt="工作台首页" />
</p>
<p align="center"><sub>工作台：细线 SVG 图标 · 快捷场景入口</sub></p>

<p align="center">
  <img src="docs/assets/screenshot-career.png" width="720" alt="职场顾问" />
</p>
<p align="center"><sub>职场顾问：SSE 流式对话 + 多 Agent 路由</sub></p>

<p align="center">
  <img src="docs/assets/screenshot-super.png" width="48%" alt="超级智能体" />
  <img src="docs/assets/screenshot-knowledge.png" width="48%" alt="知识库" />
</p>
<p align="center"><sub>左：超级智能体 · 右：知识库</sub></p>

---

## 🏗 系统架构

<p align="center">
  <img src="docs/assets/architecture-overview.png" width="900" alt="系统架构图" />
</p>
<p align="center"><sub>Frontend Vue3 → API / AppService → OrchestratorAgent → 子 Agent + Memory / Trace / HITL / Store / LLM</sub></p>

### Agent 路由

<p align="center">
  <img src="docs/assets/architecture-routing.png" width="900" alt="Agent 路由图" />
</p>
<p align="center"><sub>用户消息 → KeywordRouter（快路径）→ NLU Pipeline → ReAct / Plan-and-Solve / Reflection → 五个专业子 Agent</sub></p>

### 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 21 · Spring Boot 3.4 · Spring AI 1.0 |
| 模型 | DashScope（默认 `qwen3.7-max`）· 可选 Ollama |
| 前端 | Vue 3 · Vite · Axios · marked |
| 存储 | `file`（本地 `./tmp`）或 `jdbc`（PostgreSQL + Flyway） |
| 通信 | SSE（token 走 URL 参数）· REST + JWT refresh |

---

## 🗺 功能地图

| 你想做什么 | 去哪用 | 背后是谁 |
|-----------|--------|---------|
| 聊职场困惑、求职、薪资、离职 | **职场顾问** `/chat/career` | Orchestrator → 5 个子 Agent |
| 让 AI 自己规划并调用工具 | **超级智能体** `/chat/super` | YuManus（ReAct） |
| 检索 / 上传职场知识 | **知识库** `/knowledge` | RAG + 向量检索 |
| 看一次对话怎么被路由 / 执行 | Trace / 用量面板 | TraceRecorder · Usage |

| 能力 | 说明 |
|------|------|
| Multi-Agent 路由 | Keyword 快路径 + NLU；Resume / Negotiation / Escape / Consultation / General |
| 四层记忆 | 滑动窗口 · 事实 · 摘要 · 向量经验 |
| 工具调用 | 搜索 / 抓取 / 文件 / 终端 / PDF + MCP |
| HITL 审批 | 终端、日历等高危操作需确认 |
| 预约咨询 | 槽位收集 → 确认 → 创建（可接飞书 / 钉钉日历） |
| Trace | 执行轨迹可视化，便于联调与答辩 |
| 双存储 | `file` 演示 / `jdbc` 生产向 PostgreSQL |
| 质量与监控 | QualityGuard · Actuator · Prometheus |

完整分层（L0–L33）见 [docs/FEATURES.md](docs/FEATURES.md)。

---

## 💡 建议怎么学

本项目更偏**工程落地**，而不是纯教程章节。推荐路径：

1. **先跑通** — 按「快速开始」起前后端，用游客账号在职场顾问里聊几轮  
2. **看路由** — 对照 Trace / 日志，观察 KeywordRouter 与 NLU 如何选中子 Agent  
3. **读核心代码** — `OrchestratorAgent` → 子 Agent → `MemoryCoordinator` → HITL  
4. **对照文档** — `FEATURES.md`（能力地图）+ `INTERVIEW-DEFENSE.md`（答辩话术）  
5. **扩展一点** — 新增一个 Skill YAML，或接一个 MCP / 日历能力

适合有一定 Java / 前端基础、了解 LLM API 的同学；不要求算法训练背景。

### 目录结构

```
agent_product/
├── src/main/java/com/yupi/yuaiagent/
│   ├── agent/          # Orchestrator + 子 Agent + 范式
│   ├── nlu/ memory/    # 意图理解 · 四层记忆
│   ├── hitl/ auth/     # 人工审批 · JWT / 配额
│   ├── tools/ rag/     # 工具 · 知识库
│   ├── controller/ service/
├── src/main/resources/
│   ├── application.yml · skills/ · permissions/ · db/migration/
├── yu-ai-agent-frontend/
├── docker-compose.yml
└── docs/               # WIKI · FEATURES · assets 截图
```

---

## ❓ 常见问题

**聊天没有回复？**  
看后端日志是否出现 `AllocationQuota.FreeTierOnly`：DashScope 免费额度耗尽。关闭「仅免费额度」或充值后重试。

**编译报「不支持发行版本 21」？**  
当前 `JAVA_HOME` 不是 JDK 21，请切换后重启后端。

**SSE 鉴权失败？**  
EventSource 不能带 Header，前端已把 token 放在 URL 参数；请确认已登录且 token 未过期。

---

## 📜 开源协议

本项目采用 [MIT License](LICENSE)。

---

<div align="center">
  <p>如果这个项目对你有帮助，欢迎 Star ⭐</p>
  <p><em>Built by jsq · Java 21 + Spring AI + Vue 3 + PostgreSQL</em></p>
</div>
