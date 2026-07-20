# 生产化三板斧：存储双模 · 账号配额 · SSE 续传

> 创建：2026-07-14  
> 优先级（按用户确认）：**P0 账号权限+配额 → P1 SSE 续传 → P2 storage 双模**

---

## 目标对照

| 能力 | 演示部署 | 正式生产 |
|------|----------|----------|
| 存储 | `app.storage.type=file`（./tmp，零依赖） | `app.storage.type=jdbc`（PostgreSQL） |
| 账号 | 可开 guest（低配额） | 强制注册登录 + refresh + 角色 |
| SSE | 建议仍落库半条（file 也可写 JSON） | messageId 续传 + 前端重连 |

---

## P0 · 账号 / Refresh / 角色 / 配额 / 前端守卫（先做）

### 为什么优先
无门槛游客登录 + 无日配额 = Demo 就能把 DashScope token 烧光。

### 角色与默认日配额（可配置）

| Role | 日请求上限 | 日 Token 上限（估算） | 说明 |
|------|-----------|---------------------|------|
| `GUEST` | **默认关闭**；若开启则 3 次 chat | 15_000 | 防烧 Token；`GUEST_ENABLED=false` |
| `USER` | 50 | 200_000 | 正式注册用户 |
| `ADMIN` | 很高 | 很高 | 管理端 |

### API

| Method | Path | 说明 |
|--------|------|------|
| POST | `/session/register` | username + password → USER |
| POST | `/session/login` | 返回 `accessToken` + `refreshToken` + role |
| POST | `/session/refresh` | refresh → 新 access（旋转 refresh） |
| POST | `/session/logout` | 吊销 refresh |
| GET | `/session/me` | 当前用户 + 配额剩余 |

### Token 策略
- **Access JWT**：短时效（默认 30min），payload: `userId, username, role, typ=access`
- **Refresh JWT**：长时效（默认 14d）+ 服务端落库哈希，支持吊销；payload: `typ=refresh`
- 废弃：匿名 `userId` 无密码畅聊（演示模式 `app.auth.guest-enabled=true` 时保留，但强制 GUEST 配额）

### 配额拦截点
`OrchestratorAppService.chatStream` 入口：`UserQuotaService.checkAndConsume(userId)`  
超限 → `BusinessException` 429，前端提示升级登录。

### 前端
- 登录/注册页；`localStorage`: accessToken / refreshToken / role
- Axios：401 先调 `/session/refresh`，失败再跳登录（**禁止**无限游客重登）
- 路由守卫：`meta.requiresAuth` / `meta.roles: ['ADMIN']`
- 聊天页展示今日配额条

### Schema（V2）
- `t_wp_user` 增补：`username` UNIQUE, `password_hash`, `status`
- 新表：`t_refresh_token`（user_id, token_hash, expires_at, revoked）
- 新表：`t_user_daily_quota`（user_id, day, chat_count, token_used）  
  *或* 继续用 `t_token_usage` 聚合按日统计

---

## P1 · SSE 重连 / messageId 续传 / 半条落库

### 后端
1. 流开始：分配 `assistantMessageId`，SSE 事件 `message-start` 推给前端
2. 用户消息先落库 `status=COMPLETE`
3. 助手消息落库 `status=STREAMING`，`partial_content` 定期 flush（如每 500ms / 每 N token）
4. 正常结束 → `content=full`, `status=COMPLETE`；异常/断开 → `status=PARTIAL`
5. 续传：`GET /ai/orchestrator/chat/resume?chatId=&messageId=&token=`  
   - 若 COMPLETE：返回已完成全文  
   - 若 STREAMING/PARTIAL：从 `partial_content` 续推或仅补齐 UI
6. `PersistentMessageRepository`：按 `app.storage.type` 走 file 或 jdbc（与 P2 衔接）

### 前端（CareerAdvisor）
- 保存当前 `assistantMessageId`；`onerror` 退避重连 resume
- 历史消息带 `messageId`；PARTIAL 展示「回答中断，点击继续」

---

## P2 · `app.storage.type=file|jdbc`

### 设计
```
app.storage.type: file | jdbc   # default file（演示友好）

统一接口（已有 Repository 外观保持）:
  PersistentMessageRepository / TraceRepository / FavoriteRepository / AppointmentRepository
    → FileXxxStore | JpaXxxStore（按 type 装配 @ConditionalOnProperty）
```

已切 JPA 的模块（Artifact / Session / Fact / Profile…）在 `file` 模式下：
- **方案 A（推荐）**：演示也起嵌入式 H2 或本地 PG docker（仍是 jdbc，只换库）
- **方案 B**：为未切完的模块做 File 回退；已 JPA 模块要求 jdbc

**推荐落地**：`file` = 核心会话/消息/轨迹用 JSON；`jdbc` = 全量 PG。演示一键 `STORAGE_TYPE=file` 无需装库；正式 `STORAGE_TYPE=jdbc` + docker-compose。

### 配置示例
```yaml
# 演示
app.storage.type: file

# 生产
app.storage.type: jdbc
spring.datasource.url: jdbc:postgresql://...
```

---

## 实施状态（2026-07-14）

- [x] P0 账号 + refresh + 角色 + 日配额；游客默认关闭
- [x] P1 `message-start` + `partial_content` 落库 + `GET /ai/orchestrator/chat/resume` + CareerAdvisor 断线续传
- [x] P2 `app.storage.type=file|jdbc`：`MessageStore` / `TraceStore` 双实现（默认 file）

```
切换示例：
  STORAGE_TYPE=file   # 演示：./tmp/sessions/messages、./tmp/traces
  STORAGE_TYPE=jdbc   # 生产：t_message / t_trace（需 PG schema 已就绪）
```

---

## 非目标（本阶段不做）
- 完整 OAuth（GitHub/飞书）— 预留 `AuthProvider` 接口，P0 之后可插
- 多租户组织级账单
- 全量模块 file 回退（已 JPA 的 Artifact 等保持 jdbc 或演示用 docker PG）
