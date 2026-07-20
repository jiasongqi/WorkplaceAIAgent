# WorkPilot 项目导航 · Cursor 开发索引

> 本目录为 Cursor AI 与开发者提供**结构化代码地图**，用于快速定位功能、理解模块边界、开展增量开发。
>
> 项目：WorkPilot / 全场景职场生存智囊 · Java 21 + Spring Boot 3.4 + Spring AI + Vue 3

---

## 快速入口

| 我要… | 看这里 |
|-------|--------|
| 了解整体架构 | [codemaps/01-architecture.md](./codemaps/01-architecture.md) |
| 找后端包/类 | [codemaps/02-backend-packages.md](./codemaps/02-backend-packages.md) |
| 改 Agent 路由/子 Agent | [codemaps/03-backend-agents.md](./codemaps/03-backend-agents.md) |
| 加/改 API 接口 | [codemaps/04-backend-api.md](./codemaps/04-backend-api.md) |
| 改前端页面/路由 | [codemaps/05-frontend.md](./codemaps/05-frontend.md) |
| 按功能层定位代码 | [codemaps/06-features-by-layer.md](./codemaps/06-features-by-layer.md) |
| 改配置/YAML/资源 | [codemaps/07-config-and-resources.md](./codemaps/07-config-and-resources.md) |
| 完整索引 | [codemaps/00-INDEX.md](./codemaps/00-INDEX.md) |

---

## 目录结构

```
.cursor/
├── README.md                          ← 你在这里（总入口）
├── rules/
│   └── project-context.mdc            ← Cursor AI 项目上下文规则
└── codemaps/
    ├── 00-INDEX.md                    ← 全量索引 + 常见开发场景
    ├── 01-architecture.md             ← 分层架构 + 请求链路
    ├── 02-backend-packages.md         ← 46 个 Java 包说明
    ├── 03-backend-agents.md           ← Agent 体系 + 路由流程
    ├── 04-backend-api.md              ← REST API 端点表
    ├── 05-frontend.md                 ← Vue 路由/页面/API 客户端
    ├── 06-features-by-layer.md        ← L0-L33 功能层 → 代码映射
    └── 07-config-and-resources.md     ← application.yml / YAML 资源
```

---

## 常见开发场景

### 新增一个专业子 Agent
1. 创建 `agent/XxxAgent.java`（继承 `BaseAgent` 或 `ReActAgent`）
2. 在 `config/AgentConfig.java` 注册 Bean
3. 在 `OrchestratorAgent` 添加路由分支
4. 在 `nlu/` 增加意图枚举与 NLU prompt
5. 添加 `permissions/xxx-agent.yaml` 权限画像
6. 可选：`agents/xxx-agent.yaml` 描述符、`eval/xxx-suite.yaml` 评测

### 新增 REST 接口
1. `controller/XxxController.java` — HTTP 适配
2. `service/XxxAppService.java` — 业务编排（Controller 不直接调 Agent）
3. 前端 `yu-ai-agent-frontend/src/api/index.js` 添加方法

### 新增前端页面
1. `views/Xxx.vue` — 页面组件
2. `router/index.js` — 注册路由
3. `components/AppLayout.vue` — 导航栏添加入口（如需要）

### 新增工具 (Tool)
1. 创建 `tools/XxxTool.java` 实现 `@Tool` 方法
2. 在 `tools/ToolRegistration.java` 注册
3. 可选：在 `tools/registry/ToolRegistryService` 动态注册

### 新增 YAML 技能
1. 在 `src/main/resources/skills/` 添加 `.yaml`
2. `SkillRegistry` 自动热加载
3. Orchestrator 通过 `SkillExecutor` 匹配执行

---

## 外部文档（详细设计）

| 文档 | 路径 |
|------|------|
| 完整 Wiki | `docs/WIKI.md` |
| 功能分层 L0-L33 | `docs/FEATURES.md` |
| 架构设计 | `docs/ARCHITECTURE.md` |
| NLU 设计 | `docs/nlu-layer-design-v4.2.md` |
| 多 Agent 运行时 | `docs/multi-agent-runtime-architecture.md` |
| 待办任务 | `docs/TODO.md` |

---

## 启动命令

```bash
# 后端 (端口 8123)
mvn spring-boot:run

# 前端 (端口 3000)
cd yu-ai-agent-frontend && npm install && npm run dev
```
