<div align="right">
  <a href="./README.md">🇨🇳 中文</a> | <b>🇺🇸 English</b>
</div>

<div align="center">
  <h1>WorkPilot</h1>
  <h3>🚀 All-Scenario AI Career Coach</h3>
  <p><em>From job seeking → negotiation → growth → consultation → resignation — a full-lifecycle workplace AI assistant</em></p>

  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" alt="Java" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-green?logo=springboot" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Spring%20AI-1.0-blue" alt="Spring AI" />
  <img src="https://img.shields.io/badge/Vue.js-3-brightgreen?logo=vuedotjs" alt="Vue.js" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License" />
</div>

---

## 🎯 Project Introduction

Calling an LLM API is easy. Shipping a real Agent product is harder: how do you route intent, layer memory, audit tool calls, and require human approval for risky actions? **WorkPilot** is a runnable full-stack answer to those questions.

WorkPilot is an **all-scenario AI career coach**. The frontend is a Vue 3 workbench; the backend is Java 21 + Spring Boot + Spring AI. User messages enter `OrchestratorAgent`, go through a keyword fast path / NLU pipeline, and land on specialist agents for resume, salary negotiation, resignation, consultation booking, and general career advice — with 4-layer memory, tool calling, HITL approval, execution traces, and dual storage (`file` / PostgreSQL).

It is a good fit if you want to:

- Build a **demoable Multi-Agent portfolio project**
- Learn how **Orchestrator + sub-agents + Memory + Tools + HITL** land in production-style code
- Practice Chinese workplace scenarios (resume, raise, quit, booking)

---

## 📚 Quick Start

### Prerequisites

- JDK **21+** (on Windows, set `JAVA_HOME` to JDK 21 if the default is 17)
- Node.js **18+**
- [DashScope API Key](https://dashscope.aliyun.com/) (disable “free tier only” or ensure billing quota)

### 1. Clone

```bash
git clone https://github.com/jiasongqi/WorkplaceAIAgent.git
cd WorkplaceAIAgent
git checkout java-v3-db   # or feat/consultation-chat-ux / your target branch
```

### 2. Environment

```powershell
$env:DASHSCOPE_API_KEY = "your-dashscope-key"
$env:JWT_SECRET = "change-me-to-a-long-random-string"
# Optional: $env:STORAGE_TYPE = "file"   # file=./tmp ; jdbc=PostgreSQL
```

> Inject secrets via env vars — **never** commit API keys or DB passwords.

### 3. Backend

```bash
mvn spring-boot:run -DskipTests
# → http://localhost:8123/api
# Health: curl http://localhost:8123/api/health
```

### 4. Frontend

```bash
cd yu-ai-agent-frontend
npm install
npm run dev
# → http://localhost:3000
```

### 5. First run

1. Open http://localhost:3000/login  
2. **Register**, or local guest: username `游客` / password `workpilot-local` (or “Guest login”)  
3. Open **Career Advisor** and try: `Help me rewrite a Java backend resume highlight`

### Optional: PostgreSQL via Docker

```bash
docker compose up -d postgres
$env:STORAGE_TYPE = "jdbc"
$env:DB_URL = "jdbc:postgresql://localhost:5432/workpilot"
$env:DB_USERNAME = "workpilot"
$env:DB_PASSWORD = "workpilot123"
mvn spring-boot:run -DskipTests
```

Full stack:

```bash
docker compose up -d
```

---

## ✨ What You Will Get

- 🧭 **Multi-agent routing** — keyword fast path + NLU; 5 specialists (resume / negotiation / escape / consultation / general)
- 🧠 **4-layer memory** — sliding window · facts · summary · vector experience
- 🔧 **Tools & MCP** — search, scrape, files, terminal, PDF, plus MCP extensions
- 🙋 **HITL approval** — confirm before high-risk terminal / calendar actions
- 📊 **Trace & eval** — execution timelines for debugging, demos, and regression
- 🗄️ **Dual storage** — `file` for local demo / `jdbc` PostgreSQL + Flyway
- 🖥️ **Runnable product UI** — login, home, career advisor, super agent, knowledge base

---

## 📖 Content Navigation

| Section | Focus | Status |
|---------|-------|--------|
| **I. Get started & product** | | |
| [Quick Start](#-quick-start) | Env, launch, first chat | ✅ |
| [Screenshots](#-screenshots) | Login / Home / Advisor / Manus / Knowledge | ✅ |
| **II. Architecture & capabilities** | | |
| [Architecture](#-architecture) | Layered system + routing diagrams | ✅ |
| [Feature Map](#-feature-map) | Capability overview | ✅ |
| [docs/FEATURES.md](docs/FEATURES.md) | Full L0–L33 feature layers | ✅ |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architecture notes | ✅ |
| **III. Deep dives** | | |
| [docs/WIKI.md](docs/WIKI.md) | Project wiki | ✅ |
| [docs/nlu-layer-design-v4.2.md](docs/nlu-layer-design-v4.2.md) | NLU pipeline design | ✅ |
| [docs/multi-agent-runtime-architecture.md](docs/multi-agent-runtime-architecture.md) | Multi-agent runtime | ✅ |
| [docs/plan-auth-sse-storage.md](docs/plan-auth-sse-storage.md) | Auth · SSE · storage plan | ✅ |
| **IV. Interview** | | |
| [docs/INTERVIEW-DEFENSE.md](docs/INTERVIEW-DEFENSE.md) | Interview defense notes | ✅ |
| [docs/INTERVIEW_QA_SKILL.md](docs/INTERVIEW_QA_SKILL.md) | Interview Q&A skill notes | ✅ |
| [docs/PROJECT_HIGHLIGHTS.md](docs/PROJECT_HIGHLIGHTS.md) | Project highlights | ✅ |
| **V. Study notes** | | |
| [docs/hello-agents-study.md](docs/hello-agents-study.md) | Hello-Agents study mapping | ✅ |
| [docs/HELLO_AGENTS_SUMMARY.md](docs/HELLO_AGENTS_SUMMARY.md) | Hello-Agents summary | ✅ |

---

## 🖼 Screenshots

<p align="center">
  <img src="docs/assets/screenshot-login.png" width="720" alt="Login" />
</p>
<p align="center"><sub>Login / Register / Guest for local demo</sub></p>

<p align="center">
  <img src="docs/assets/screenshot-home.png" width="720" alt="Home" />
</p>
<p align="center"><sub>Home: quick scenario entry</sub></p>

<p align="center">
  <img src="docs/assets/screenshot-career.png" width="720" alt="Career Advisor" />
</p>
<p align="center"><sub>Career Advisor: SSE streaming + multi-agent routing</sub></p>

<p align="center">
  <img src="docs/assets/screenshot-super.png" width="48%" alt="Super Agent" />
  <img src="docs/assets/screenshot-knowledge.png" width="48%" alt="Knowledge Base" />
</p>
<p align="center"><sub>Left: Super Agent · Right: Knowledge Base</sub></p>

---

## 🏗 Architecture

<p align="center">
  <img src="docs/assets/architecture-overview.png" width="900" alt="System architecture" />
</p>
<p align="center"><sub>Frontend Vue3 → API / AppService → OrchestratorAgent → sub-agents + Memory / Trace / HITL / Store / LLM</sub></p>

### Agent routing

<p align="center">
  <img src="docs/assets/architecture-routing.png" width="900" alt="Agent routing" />
</p>
<p align="center"><sub>User message → KeywordRouter (fast path) → NLU Pipeline → ReAct / Plan-and-Solve / Reflection → five specialist agents</sub></p>

### Tech stack

| Layer | Tech |
|-------|------|
| Backend | Java 21 · Spring Boot 3.4 · Spring AI 1.0 |
| Models | DashScope (default `qwen3.7-max`) · optional Ollama |
| Frontend | Vue 3 · Vite · Axios · marked |
| Storage | `file` (`./tmp`) or `jdbc` (PostgreSQL + Flyway) |
| Transport | SSE (token in URL) · REST + JWT refresh |

---

## 🗺 Feature Map

| Goal | Where | Who handles it |
|------|--------|----------------|
| Career chat: resume, salary, resignation | **Career Advisor** `/chat/career` | Orchestrator → 5 sub-agents |
| Autonomous tool-using agent | **Super Agent** `/chat/super` | YuManus (ReAct) |
| Search / upload career docs | **Knowledge Base** `/knowledge` | RAG + vector store |
| Inspect routing / execution | Trace / Usage panels | TraceRecorder · Usage |

| Capability | What it does |
|------------|--------------|
| Multi-agent routing | Keyword fast path + NLU; Resume / Negotiation / Escape / Consultation / General |
| 4-layer memory | Sliding window · facts · summary · vector experience |
| Tool calling | Search / scrape / files / terminal / PDF + MCP |
| HITL | Approval for high-risk terminal & calendar actions |
| Consultation booking | Slot filling → confirm → create (Feishu/DingTalk ready) |
| Trace | Execution timeline for debugging & demos |
| Dual storage | `file` demo / `jdbc` PostgreSQL |
| Quality & ops | QualityGuard · Actuator · Prometheus |

Full layer map: [docs/FEATURES.md](docs/FEATURES.md).

---

## 💡 How to Learn

This repo is **engineering-first**, not a chapter-only tutorial. Suggested path:

1. **Run it** — follow Quick Start, chat a few rounds in Career Advisor  
2. **Watch routing** — use Trace / logs to see KeywordRouter vs NLU pick a sub-agent  
3. **Read core code** — `OrchestratorAgent` → sub-agents → `MemoryCoordinator` → HITL  
4. **Use the docs** — `FEATURES.md` + `INTERVIEW-DEFENSE.md`  
5. **Extend one piece** — add a Skill YAML, or wire an MCP / calendar capability

Best for people with some Java / frontend background and basic LLM API knowledge. No model-training background required.

### Layout

```
agent_product/
├── src/main/java/com/yupi/yuaiagent/
│   ├── agent/ · nlu/ · memory/ · hitl/ · auth/
│   ├── tools/ · rag/ · controller/ · service/
├── src/main/resources/
├── yu-ai-agent-frontend/
├── docker-compose.yml
└── docs/
```

---

## ❓ FAQ

**No chat reply?**  
Check logs for `AllocationQuota.FreeTierOnly` — DashScope free quota exhausted.

**Build fails with “release version 21 not supported”?**  
Point `JAVA_HOME` to JDK 21 and restart.

**SSE auth fails?**  
EventSource cannot send headers; the frontend passes `token` as a query param. Make sure you are logged in.

---

## 📜 License

[MIT License](LICENSE).

---

<div align="center">
  <p>If this project helps you, a Star ⭐ is appreciated</p>
  <p><em>Built by jsq · Java 21 + Spring AI + Vue 3 + PostgreSQL</em></p>
</div>
