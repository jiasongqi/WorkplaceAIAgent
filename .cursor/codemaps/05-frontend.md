# 前端 · Vue 3 Frontend

> 根目录：`yu-ai-agent-frontend/`  
> 技术栈：Vue 3 + Vite + Vue Router + Axios

---

## 目录结构

```
yu-ai-agent-frontend/
├── src/
│   ├── main.js                 入口
│   ├── App.vue                 根组件 → AppLayout
│   ├── api/index.js            API 客户端（Axios + SSE）
│   ├── router/index.js         路由定义
│   ├── views/                  页面组件（11 个）
│   ├── components/             公共组件（6 个）
│   ├── assets/                 静态资源
│   └── style.css               全局样式
├── vite.config.js              Vite 配置（dev port 3000）
├── nginx.conf                  生产 nginx 代理
└── package.json
```

---

## 路由与页面对照

**文件：** `src/router/index.js`

| 路由 | 组件 | 功能 | 后端 API |
|------|------|------|----------|
| `/` | `views/Home.vue` | 工作台首页、快捷入口 | `/session/list` |
| `/chat/career` | `views/CareerAdvisor.vue` | **主产品聊天** | `/ai/orchestrator/chat` (SSE) |
| `/chat/super` | `views/SuperAgent.vue` | 超级智能体 Manus | `/ai/manus/chat` (SSE) |
| `/knowledge` | `views/KnowledgeBase.vue` | 知识库管理 | `/document/*` |
| `/artifacts` | `views/ArtifactAdmin.vue` | 交付物管理（admin） | `/artifact/*` |
| `/favorites` | `views/Favorites.vue` | 收藏消息 | `/favorite/*` |
| `/usage` | `views/UsageDashboard.vue` | 用量统计 | `/usage/stats` |
| `/compare` | `views/CompareView.vue` | Agent A/B 对比 | `/ai/orchestrator/chat` ×2 |
| `/trace/:traceId` | `views/TraceDetail.vue` | 执行轨迹详情 | `/trace/{traceId}` |
| `/admin` | `views/AdminDashboard.vue` | 管理后台 hub | 导航入口 |
| `/love-master` | `views/LoveMaster.vue` | 遗留沟通助手（隐藏） | `/ai/ai_chat/chat/sse` |

---

## 页面功能详情

### CareerAdvisor.vue — 主聊天页（最复杂）
- 左侧：会话列表（活跃/归档/回收站）、搜索
- 中间：SSE 流式聊天、Markdown 渲染
- 右侧：Trace 时间线、用户画像
- 功能：收藏、文件上传、会话 CRUD、归档
- SSE 事件处理：`routing`, `agent-turn`, `trace`, `quality-review`, `clarification`

### SuperAgent.vue — 超级智能体
- Manus Agent 流式对话
- 右侧执行进度面板

### KnowledgeBase.vue
- 上传/列表/删除 .md 文档
- 部分 API 用 raw `fetch`（非 api/index.js）

### CompareView.vue
- 双栏并排 Orchestrator SSE
- 直接 EventSource，不经过 api 封装

### TraceDetail.vue
- 三栏轨迹检查器
- RUNNING 状态时轮询刷新

---

## 公共组件

**目录：** `src/components/`

| 组件 | 文件 | 用途 | 使用者 |
|------|------|------|--------|
| AppLayout | `AppLayout.vue` | 全局布局、导航栏、移动端抽屉 | App.vue |
| ChatRoom | `ChatRoom.vue` | 通用聊天 UI（消息列表+输入框） | LoveMaster |
| TraceTimelineView | `TraceTimelineView.vue` | 实时轨迹步骤时间线 | CareerAdvisor |
| AiAvatarFallback | `AiAvatarFallback.vue` | 圆形 emoji 头像 | ChatRoom |
| AppFooter | `AppFooter.vue` | 页脚 | LoveMaster |
| HelloWorld | `HelloWorld.vue` | Vite 脚手架（未使用） | — |

---

## API 客户端

**文件：** `src/api/index.js`

### 基址

```javascript
const API_BASE_URL = import.meta.env.PROD
  ? '/api'
  : 'http://localhost:8123/api'
```

### HTTP（Axios）
- 超时：60s
- 请求拦截：自动附加 `Authorization: Bearer ${localStorage.token}`
- 响应拦截：401 → 自动游客重登 → 刷新 token → 重试队列

### SSE（EventSource）

| 函数 | 端点 | Token |
|------|------|-------|
| `chatWithAiChat(msg, chatId)` | `/ai/ai_chat/chat/sse` | 无 |
| `chatWithOrchestrator(msg, chatId)` | `/ai/orchestrator/chat` | URL `token` 参数 |
| `chatWithManus(msg)` | `/ai/manus/chat` | 无 |
| `connectSSE(url, params)` | 通用 helper | — |

### API 函数分组

| 分组 | 函数 |
|------|------|
| Auth/Session | `login`, `createSession`, `listSessions`, `deleteSession`, `getChatMessages`, `renameSession`, `archiveSession`, `unarchiveSession`, `listArchivedSessions`, `restoreSession`, `listTrashSessions`, `searchSessions` |
| AI Chat | `chatWithAiChat`, `chatWithOrchestrator`, `chatWithManus`, `connectSSE` |
| Profile | `getMyProfile`, `clearMyProfile` |
| Document | `uploadDocument` |
| Artifact | `listArtifacts`, `getArtifactDetail` |
| Trace | `getTrace`, `getTracesByChat`, `getTracesByUser` |
| Favorite | `addFavorite`, `removeFavorite`, `listFavorites` |
| Export | `exportAll`, `importData` |
| Usage | `getUsageStats` |

### 已定义但未接入 UI 的 API
- `getTracesByChat`, `getTracesByUser`
- `listTrashSessions`
- `exportAll`, `importData`

---

## 鉴权模型

- **游客登录：** `POST /session/login?username=游客`（可选 `userId` 保持会话归属）
- **Admin 登录：** `POST /session/login?username=admin`（ArtifactAdmin 页）
- **Storage：** `localStorage` → `token`, `userId`, `username`
- **无路由守卫** — 鉴权通过 API 401 拦截 + 各页面 `ensureLogin()`

---

## 新增前端功能步骤

1. 创建 `views/Xxx.vue`
2. 在 `router/index.js` 添加路由
3. 在 `components/AppLayout.vue` 导航栏添加入口
4. 在 `api/index.js` 添加 API 方法
5. 如需 SSE，参考 `CareerAdvisor.vue` 的 EventSource 处理

---

## 生产部署

- **nginx.conf：** `/api` 代理到后端，SSE 友好（`proxy_buffering off`, `proxy_read_timeout 600s`）
- **Docker：** 前端静态文件 + nginx
