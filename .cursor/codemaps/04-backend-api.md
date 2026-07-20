# REST API 端点 · Backend API

> 全局前缀：`/api`（`application.yml` → `server.servlet.context-path`）  
> 鉴权：JWT Bearer Header（SSE 接口通过 URL `token` 参数）

---

## Session — 会话与鉴权

**Controller：** `controller/SessionController.java`

| Method | Path | 说明 |
|--------|------|------|
| POST | `/session/login` | 登录（guest/admin），返回 JWT |
| POST | `/session/create` | 创建聊天会话 |
| GET | `/session/list` | 活跃会话列表 |
| GET | `/session/archived` | 已归档会话 |
| GET | `/session/trash` | 回收站会话 |
| PUT | `/session/{chatId}/title` | 重命名 |
| PUT | `/session/{chatId}/archive` | 归档 |
| PUT | `/session/{chatId}/unarchive` | 取消归档 |
| DELETE | `/session/{chatId}` | 软删除 |
| PUT | `/session/{chatId}/restore` | 从回收站恢复 |
| GET | `/session/search` | 关键词搜索 |
| GET | `/session/{chatId}/messages` | 获取会话消息 |

**AppService：** `service/SessionAppService.java`

---

## AI Chat — 对话

**Controller：** `controller/AiController.java`

| Method | Path | 说明 | 前端使用 |
|--------|------|------|----------|
| POST | `/ai/ai_chat/chat/sync` | 同步对话 | — |
| GET | `/ai/ai_chat/chat/sse` | SSE 流式（Flux） | LoveMaster |
| GET | `/ai/ai_chat/chat/server_sent_event` | SSE（ServerSentEvent） | — |
| GET | `/ai/ai_chat/chat/sse_emitter` | SSE（SseEmitter） | — |
| GET | `/ai/orchestrator/chat` | **Orchestrator SSE（主产品）** | CareerAdvisor, CompareView |
| GET | `/ai/manus/chat` | Manus 超级智能体 SSE | SuperAgent |
| POST | `/ai/ai_chat/rag/sync` | RAG 增强对话 | — |
| POST | `/ai/ai_chat/tools/sync` | 工具调用对话 | — |
| POST | `/ai/ai_chat/mcp/sync` | MCP 对话 | — |
| POST | `/ai/ai_chat/report/sync` | 报告生成 | — |

**AppService：** `service/OrchestratorAppService.java`（Orchestrator）

### Orchestrator SSE 事件类型

| 事件 | 说明 |
|------|------|
| `routing` | 路由决策（意图、目标 Agent） |
| `agent-turn` | Agent 轮次切换 |
| `trace` | 执行轨迹 span |
| `quality-review` | 质量审查结果 |
| `quality-blocked` | 质量拦截 |
| `clarification` | 意图澄清追问 |
| (default message) | Token 级流式文本，`[DONE]` 结束 |

### SSE 请求参数

```
GET /api/ai/orchestrator/chat?message={text}&chatId={id}&token={jwt}
```

---

## Document — 知识库

**Controller：** `controller/DocumentController.java`  
**AppService：** `service/DocumentAppService.java`

| Method | Path | 说明 |
|--------|------|------|
| POST | `/document/upload` | 上传 .md 到向量库 |
| POST | `/document/add` | 添加文本文档 |
| GET | `/document/list` | 文档列表 |
| DELETE | `/document/{docId}` | 删除文档 |

---

## Trace — 执行轨迹

**Controller：** `controller/TraceController.java`

| Method | Path | 说明 |
|--------|------|------|
| GET | `/trace/{traceId}` | 按 ID 查询 |
| GET | `/trace/chat/{chatId}` | 按会话查询（分页） |
| GET | `/trace/user/{userId}` | 按用户查询（分页） |

---

## Artifact — 交付物（Admin）

**Controller：** `controller/ArtifactController.java`  
**需 admin 权限**

| Method | Path | 说明 |
|--------|------|------|
| GET | `/artifact/list` | 交付物列表（可筛选） |
| GET | `/artifact/{artifactId}` | 交付物详情 |

---

## Favorite — 收藏

**Controller：** `controller/FavoriteController.java`  
**AppService：** `service/FavoriteAppService.java`

| Method | Path | 说明 |
|--------|------|------|
| POST | `/favorite` | 添加收藏 |
| DELETE | `/favorite/{favoriteId}` | 删除收藏 |
| GET | `/favorite/list` | 收藏列表 |

---

## Profile — 用户画像

**Controller：** `controller/ProfileController.java`

| Method | Path | 说明 |
|--------|------|------|
| GET | `/profile/me` | 获取当前用户画像 |
| DELETE | `/profile/me` | 清除画像 |

---

## Export — 导入导出

**Controller：** `controller/ExportController.java`  
**AppService：** `service/ExportAppService.java`

| Method | Path | 说明 |
|--------|------|------|
| GET | `/export/all` | 导出全量数据（ZIP，token 在 URL） |
| POST | `/export/import` | 导入数据（ZIP） |

---

## Usage — 用量统计

**Controller：** `controller/UsageController.java`

| Method | Path | 说明 |
|--------|------|------|
| GET | `/usage/stats` | 用量统计 |

---

## Feedback — 反馈

**Controller：** `controller/FeedbackController.java`

| Method | Path | 说明 |
|--------|------|------|
| POST | `/feedback` | 提交反馈 |
| GET | `/feedback/stats` | 反馈统计 |

---

## Health — 健康检查

**Controller：** `controller/HealthController.java`

| Method | Path | 说明 |
|--------|------|------|
| GET | `/health` | 健康检查 |

---

## Actuator — 监控（Spring Boot）

| Path | 说明 |
|------|------|
| `/actuator/health` | 健康检查 |
| `/actuator/agent-metrics` | Agent 自定义指标 |
| `/actuator/agent-diagnostics` | Agent 诊断 |
| `/actuator/prometheus` | Prometheus 指标 |

**实现：** `metrics/AgentMetricsEndpoint.java`, `metrics/AgentDiagnosticsEndpoint.java`

---

## 通用响应格式

**包装类：** `common/Response.java`  
**错误码：** `common/ResultCode.java`  
**异常处理：** `exception/GlobalExceptionHandler.java`

```json
{
  "code": 0,
  "data": { ... },
  "message": "ok"
}
```

401 时前端自动以「游客」身份重新登录并重试（见 `api/index.js` 拦截器）。
