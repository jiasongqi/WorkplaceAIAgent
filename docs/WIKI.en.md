# WorkPilot Project Wiki (English)

> Updated: 2026-07-27 (v1.7 — personal companion · digital employees · suggested actions · feedback closed loop)  
> Product: WorkPilot (all-scenario AI career coach)  
> Stack: Java 21 + Spring Boot 3.4 + Spring AI 1.0 + Vue 3 + DashScope + optional PostgreSQL  
> Chinese: [WIKI.md](./WIKI.md)

---

## Contents

- [1. Product positioning](#1-product-positioning)
- [2. Architecture overview](#2-architecture-overview)
- [3. Core modules](#3-core-modules)
- [4. Agent system](#4-agent-system)
- [5. Frontend pages](#5-frontend-pages)
- [6. API cheatsheet](#6-api-cheatsheet)
- [7. Storage](#7-storage)
- [8. Screenshots](#8-screenshots)
- [9. Related docs](#9-related-docs)

---

## 1. Product positioning

WorkPilot covers the workplace lifecycle from job seeking to resignation:

| Scenario | Capability | Owner |
|----------|------------|--------|
| Job prep | Resume, interview, STAR | ResumeAgent |
| Salary negotiation | Market research, raise strategy | NegotiationAgent |
| Resignation | Letters, handover, labor rights | EscapeAgent |
| Consultation booking | Slot filling + calendar | ConsultationAgent |
| General career | Relationships, stress, planning | GeneralCareerAgent |
| Personal companion | Always-on persona per user | UserCompanion → Orchestrator |
| Digital employees | Template specialists, activate to delegate | DigitalEmployee + sub-agents |
| Complex tasks | Search, PDF, code | YuManus (Super Agent) |

**Companion vs digital employee:** companion = default personality & preferences; digital employees = switchable specialists (“one role, one job”).

---

## 2. Architecture overview

```
Frontend (Vue 3)
  Home · CareerAdvisor (companion / employees) · SuperAgent · Knowledge · Usage …
        │ SSE / REST (JWT)
API → AppService → OrchestratorAgent
        ├─ KeywordRouter / NLU / Skills
        ├─ ContextInjection (profile · companion · active employee · memory)
        ├─ SuggestedActions (SSE)
        └─ Sub-agents + Feedback writeback (Reflexion / Facts)
Infrastructure: Memory · Trace · HITL · Store (file|jdbc) · LLM
```

| Layer | Tech |
|-------|------|
| Backend | Java 21, Spring Boot 3.4, Spring AI 1.0 |
| Models | DashScope (Qwen / DeepSeek), optional Ollama |
| DB | PostgreSQL 16 + Flyway + JPA when `STORAGE_TYPE=jdbc` |
| Frontend | Vue 3, Vite, Axios, marked |
| Streaming | SSE (token in URL query) |

---

## 3. Core modules

### 3.1 Feedback closed loop

- Ratings: `UP` / `DOWN` (1–5 stars = future TODO)
- `POST /feedback`, `GET /feedback/stats` (JWT userId)
- **DOWN** → ReflexionService; **UP** → Fact preferences
- Stats aggregated by agent / intent (Usage dashboard)

### 3.2 Suggested actions

- Cold-start chips on empty Career Advisor chat
- After a reply, Orchestrator emits SSE event `suggested-actions`
- Chip click sends the text as the next user message

### 3.3 Personal companion

| Item | Detail |
|------|--------|
| Scope | One companion per user; always injected (not “summoned”) |
| Fields | displayName, tone, focus, personaPrompt, skills |
| API | `GET/PUT /companion/me` (claim on first get) |
| Inject | `ContextInjectionService` → Orchestrator |
| UI | Sidebar **My AI Team**, top pills, welcome cards, settings drawer |
| Table | `t_user_companion` (Flyway `V2__…`) |

Changes apply on the **next turn** (config evolution, not fine-tuning).

### 3.4 Digital employees

| Item | Detail |
|------|--------|
| Create | From `agents/*.yaml` templates |
| API | templates / mine / create / update / activate / rollback |
| Versions | `t_digital_employee_version`; update bumps version; rollback restores content |
| Chat | Activate (“set current”) or trial, then chat in Career Advisor |
| UI | Template grid, employee cards, inline persona editor, style presets |

### 3.5 Other (summary)

Multi-agent routing, 4-layer memory, RAG, skills YAML, HITL, Trace, dual storage, QualityGuard, EvalCenter, Perception / Goal Anchor, tool Schema+parallel+idempotency, Agent Loop Wrap-up/Replanner — see Chinese [WIKI.md](./WIKI.md) §§3–9 / §17 for full detail.

---

## 4. Agent system

Orchestrator flow (simplified):

1. Prompt-injection check  
2. KeywordRouter (zero-LLM) or NLU  
3. Skill match (optional short-circuit)  
4. Context injection (profile + companion + active employee + memory)  
5. Dispatch to specialist agent(s)  
6. SSE `suggested-actions`  
7. Quality review + memory extraction  

Evolution is **writeback to memory / persona config**, not model fine-tuning.

---

## 5. Frontend pages

| Page | Route | Notes |
|------|-------|--------|
| Login | `/login` | Register / password; guest toggle depends on `guest-enabled` |
| Home | `/` | Scenario entry |
| Career Advisor | `/chat/career` | Main chat + companion + digital employees |
| Super Agent | `/chat/super` | YuManus + progress |
| Knowledge | `/knowledge` | Docs |
| Usage | `/usage` | Includes feedback stats |

---

## 6. API cheatsheet

| Area | Method | Path | Auth |
|------|--------|------|------|
| Auth | POST | `/session/register` · `/login` · `/refresh` | - |
| Chat | GET | `/ai/orchestrator/chat` | JWT (token query for SSE) |
| Feedback | POST/GET | `/feedback` · `/feedback/stats` | JWT |
| Companion | GET/PUT | `/companion/me` | JWT |
| Digital employee | GET/POST/PUT | `/digital-employee/**` | JWT |
| HITL / Trace / Usage | … | `/hitl/*` · `/trace/*` · `/usage/stats` | JWT |

---

## 7. Storage

| Mode | Meaning |
|------|---------|
| `file` (default) | JSON/Kryo under `./tmp/**` |
| `jdbc` | PostgreSQL + Flyway `V1` + `V2` (companion / digital employee) |

Key V2 tables: `t_user_companion`, `t_digital_employee`, `t_digital_employee_version`.

CORS defaults include `localhost:3000` and `localhost:3001` (`app.cors.allowed-origins`).

---

## 8. Screenshots

| Screen | Asset |
|--------|--------|
| Login | [`assets/screenshot-login.png`](./assets/screenshot-login.png) |
| Home | [`assets/screenshot-home.png`](./assets/screenshot-home.png) |
| Career Advisor | [`assets/screenshot-career.png`](./assets/screenshot-career.png) |
| Companion settings | [`assets/screenshot-companion.png`](./assets/screenshot-companion.png) |
| Digital employees | [`assets/screenshot-digital-employee.png`](./assets/screenshot-digital-employee.png) |
| Super Agent | [`assets/screenshot-super.png`](./assets/screenshot-super.png) |
| Knowledge | [`assets/screenshot-knowledge.png`](./assets/screenshot-knowledge.png) |
| Theme · Original dark | [`assets/screenshot-theme-dark.png`](./assets/screenshot-theme-dark.png) |
| Theme · Capsule (default) | [`assets/screenshot-theme-capsule.png`](./assets/screenshot-theme-capsule.png) |
| Theme · Sage green | [`assets/screenshot-theme-sage.png`](./assets/screenshot-theme-sage.png) |

![Career Advisor](./assets/screenshot-career.png)

![Companion](./assets/screenshot-companion.png)

![Digital employees](./assets/screenshot-digital-employee.png)

#### Theme trio (prototype `theme-sage.html`)

| Original dark | Capsule (default) | Sage |
|---------------|-------------------|------|
| ![dark](./assets/screenshot-theme-dark.png) | ![capsule](./assets/screenshot-theme-capsule.png) | ![sage](./assets/screenshot-theme-sage.png) |

Recapture product pages:

```bash
BASE=http://localhost:3000 node yu-ai-agent-frontend/scripts/capture-docs-screenshots.mjs
```

Recapture themes:

```bash
node yu-ai-agent-frontend/scripts/capture-theme-prototypes.mjs
```
---

## 9. Related docs

| Doc | Purpose |
|-----|---------|
| [FEATURES.md](./FEATURES.md) | L0–L34 feature map |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Architecture notes |
| [nlu-layer-design-v4.2.md](./nlu-layer-design-v4.2.md) | NLU pipeline |
| [WIKI.md](./WIKI.md) | Full Chinese wiki |
| [../README.en.md](../README.en.md) | English README |
