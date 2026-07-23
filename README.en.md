<p align="center">
  <a href="./README.md">🇨🇳 中文</a>
  &nbsp;|&nbsp;
  <b>🇺🇸 English</b>
</p>

# WorkPilot · All-Scenario AI Career Coach

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-blue)](https://docs.spring.io/spring-ai/reference/)
[![Vue.js](https://img.shields.io/badge/Vue.js-3-brightgreen?logo=vuedotjs)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> An AI career coach covering the full lifecycle: job seeking → negotiation → on-the-job growth → consultation booking → resignation planning.  
> Multi-agent routing, 4-layer memory, HITL approval, and optional PostgreSQL persistence.

---

## Product in 60 seconds

| Goal | Where | Who handles it |
|------|--------|----------------|
| Career chat: resume, salary, resignation | **Career Advisor** `/chat/career` | Orchestrator → 5 sub-agents |
| Autonomous tool-using agent | **Super Agent** `/chat/super` | YuManus (ReAct) |
| Search built-in career docs | **Knowledge Base** `/knowledge` | RAG + vector store |
| Inspect routing / execution | Trace / Usage panels | TraceRecorder · Usage |

**Sub-agents:** Resume · Negotiation · Escape · Consultation · General Career

---

## Screenshots

<p align="center">
  <img src="docs/assets/screenshot-login.png" width="720" alt="Login" />
</p>
<p align="center"><sub>Login / Register / Guest for local demo</sub></p>

<p align="center">
  <img src="docs/assets/screenshot-home.png" width="720" alt="Home workbench" />
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

## Architecture

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

## Quick start

### 1. Prerequisites

- JDK **21+** (on Windows, set `JAVA_HOME` to JDK 21 if the default is 17)
- Node.js **18+**
- [DashScope API Key](https://dashscope.aliyun.com/) (disable “free tier only” or ensure billing quota)

### 2. Environment (PowerShell)

```powershell
$env:DASHSCOPE_API_KEY = "your-dashscope-key"
$env:JWT_SECRET = "change-me-to-a-long-random-string"
# Optional
# $env:STORAGE_TYPE = "file"
# $env:SEARCH_API_KEY = "..."
```

> Inject secrets via env vars — **never** commit API keys or DB passwords.

### 3. Backend

```bash
git clone https://github.com/jiasongqi/WorkplaceAIAgent.git
cd WorkplaceAIAgent
git checkout java-v3-db                  # or feat/consultation-chat-ux / your target branch

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

### 5. First login

1. Open http://localhost:3000/login  
2. **Register**, or for local demo use username `游客` / password `workpilot-local` (or “Guest login”)  
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

## Feature highlights

| Capability | What it does |
|------------|--------------|
| Multi-agent routing | Keyword fast-path + NLU; 5 specialist agents |
| 4-layer memory | Sliding window · facts · summary · vector experience |
| Tool calling | Search / scrape / files / terminal / PDF + MCP |
| HITL | Approval for high-risk terminal & calendar actions |
| Consultation booking | Slot filling → confirm → create (Feishu/DingTalk ready) |
| Trace | Execution timeline for debugging & demos |
| Dual storage | `file` for demo / `jdbc` for PostgreSQL |
| Quality & ops | QualityGuard · Actuator · Prometheus |

Full layer map (L0–L33): [docs/FEATURES.md](docs/FEATURES.md)

---

## Project layout

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

## Docs

| Doc | Content |
|-----|---------|
| [README.md](README.md) | Chinese README |
| [docs/WIKI.md](docs/WIKI.md) | Full wiki |
| [docs/FEATURES.md](docs/FEATURES.md) | Feature layers L0–L33 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architecture notes |
| [docs/INTERVIEW-DEFENSE.md](docs/INTERVIEW-DEFENSE.md) | Interview talking points |
| [docs/nlu-layer-design-v4.2.md](docs/nlu-layer-design-v4.2.md) | NLU design |

---

## FAQ

**No chat reply?**  
Check logs for `AllocationQuota.FreeTierOnly` — DashScope free quota exhausted.

**Build fails with “release version 21 not supported”?**  
Point `JAVA_HOME` to JDK 21 and restart.

**SSE auth fails?**  
EventSource cannot send headers; the frontend passes `token` as a query param. Make sure you are logged in.

---

## License

MIT · See [LICENSE](LICENSE)

> Built by jsq · Java 21 + Spring AI + Vue 3 + PostgreSQL
