# 配置与资源文件

> 根目录：`src/main/resources/`

---

## Spring 配置

| 文件 | 用途 | 关键配置项 |
|------|------|-----------|
| `application.yml` | **主配置** | DB, DashScope AI, MCP, PgVector, JWT, 会话/轨迹/交付物存储, 沙箱, Agent 守卫, 记忆层, Actuator |
| `application-prod.yml` | 生产覆盖 | 环境变量 DB/JWT, Docker 沙箱, 更严格 MCP, `/data/*` 路径 |
| `application-local.yml.example` | 本地模板 | 复制为 `application-local.yml`（gitignore） |

### application.yml 主要配置段

```yaml
server.servlet.context-path: /api     # API 全局前缀
spring.datasource.*                   # PostgreSQL
spring.ai.dashscope.*                 # DashScope API Key + 模型
spring.ai.mcp.*                       # MCP Client（默认注释）
spring.ai.vectorstore.pgvector.*      # PgVector
jwt.*                                 # JWT secret + expiry
calendar.*                            # 飞书/钉钉日历
session.storage.*                     # 会话存储路径
artifact.storage.*                    # 交付物存储
trace.storage.*                       # 轨迹存储
sandbox.*                             # 沙箱策略 (none/process/docker)
agent.guards.*                        # 循环检测/注入防护
memory.coordinator.*                  # 四层记忆开关/预算
quality.*                             # 质量守护
reflexion.*                           # Reflexion 失败记忆
rag.rerank.*                          # RAG 重排序
paradigm.*                            # 范式选择
management.endpoints.*                # Actuator 端点
```

---

## Java 配置 Bean

| 文件 | 职责 |
|------|------|
| `config/AgentConfig.java` | **所有 Agent Bean 装配**（Orchestrator, 子 Agent, YuManus, 数据员工） |
| `config/CorsConfig.java` | 跨域配置 |
| `config/CalendarConfig.java` | 日历服务配置 |
| `config/CompressionConfig.java` | 记忆压缩策略 |
| `config/ExecutorConfig.java` | 异步线程池 |
| `config/FollowUpTemplateConfig.java` | 预约追问模板加载 |

---

## YAML 资源

### Agent 描述符 — `agents/`

| 文件 | Agent |
|------|-------|
| `agents/general-agent.yaml` | GeneralCareerAgent |
| `agents/resume-agent.yaml` | ResumeAgent |
| `agents/negotiation-agent.yaml` | NegotiationAgent |

### 权限画像 — `permissions/`

| 文件 | 说明 |
|------|------|
| `permissions/consultation-agent.yaml` | 预约咨询权限 |
| `permissions/data-agent.yaml` | 数据员工权限 |
| `permissions/escape-agent.yaml` | 离职 Agent 权限 |
| `permissions/general-agent.yaml` | 通用顾问权限 |
| `permissions/negotiation-agent.yaml` | 谈判 Agent 权限 |
| `permissions/resume-agent.yaml` | 求职 Agent 权限 |
| `permissions/admin.yaml` | 管理员权限 |

### 技能定义 — `skills/`

| 文件 | 技能 |
|------|------|
| `skills/salary-research.yaml` | 薪资调研 |
| `skills/resignation-letter.yaml` | 辞职信生成 |
| `skills/interview-prep.yaml` | 面试准备 |

新增技能：在此目录添加 `.yaml`，`SkillRegistry` 自动加载。

### 评测套件 — `eval/`

| 文件 | 说明 |
|------|------|
| `eval/resume-suite.yaml` | ResumeAgent 回归测试 |

### 模板 — `templates/`

| 文件 | 说明 |
|------|------|
| `templates/follow-up-templates.yml` | ConsultationAgent 追问模板 |

---

## MCP 配置

| 文件 | 说明 |
|------|------|
| `mcp-servers.json` | MCP stdio 服务定义（amap-maps, image-search） |

在 `application.yml` 中启用 `spring.ai.mcp` 配置段。

---

## 数据库

| 文件 | 说明 |
|------|------|
| `db/migration/V1__init_schema.sql` | Flyway 初始 schema（users, conversations, messages, artifacts, traces, feedback 等） |

---

## RAG 知识库文档 — `document/`

内置 Markdown 职场文档，启动时加载到向量库：

| 主题 | 说明 |
|------|------|
| 求职篇 | 简历、面试、offer |
| 在职篇 | 人际关系、绩效 |
| 晋升篇 | 晋升路径 |
| 离职篇 | 离职规划、劳动法规 |

动态入库：`POST /document/upload` 或 `POST /document/add`

---

## 前端配置

| 文件 | 说明 |
|------|------|
| `yu-ai-agent-frontend/vite.config.js` | Vite dev server (port 3000) |
| `yu-ai-agent-frontend/nginx.conf` | 生产 nginx（/api 代理 + SSE） |
| `yu-ai-agent-frontend/package.json` | 依赖与脚本 |

---

## 环境变量（生产）

| 变量 | 用途 |
|------|------|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL 连接 |
| `JWT_SECRET` | JWT 签名密钥 |
| `DASHSCOPE_API_KEY` | DashScope API Key |
| `SANDBOX_MODE` | 沙箱模式 (none/process/docker) |

---

## 修改配置时的注意事项

- Agent 相关改动优先看 `config/AgentConfig.java`，而非仅改 YAML
- 新增 Agent 权限必须添加 `permissions/xxx-agent.yaml`
- 新增技能只需在 `skills/` 添加 YAML，无需改代码
- 记忆层开关在 `application.yml` → `memory.coordinator.enabled`
- 沙箱模式影响工具执行安全性，生产建议 `docker`
