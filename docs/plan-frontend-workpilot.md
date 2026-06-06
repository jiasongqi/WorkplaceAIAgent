# WorkPilot 前端设计计划

> 产品定位：多 Agent 协作的职场智能工作台  
> 前台给用户用，后台给管理员看

---

## 设计规范

### 视觉 Token
```css
--bg: #f7f8fa;           /* 页面底色 */
--surface: #ffffff;       /* 卡片/面板 */
--surface-muted: #f1f3f5; /* 次级背景 */
--border: #e5e7eb;        /* 主边框 */
--text: #111827;          /* 主文字 */
--text-secondary: #374151;
--text-muted: #6b7280;    /* 辅助文字 */
--primary: #2563eb;       /* 主色蓝 */
--success: #16a34a;
--warning: #d97706;
--danger: #dc2626;
--radius: 8px;            /* 圆角，不用 16px/30px */
```

### 风格关键词
- 浅色、密集、专业
- 8px 圆角，边框为主，少阴影
- 不要霓虹蓝紫、不要大面积黑色、不要发光卡片
- 不要 emoji 当图标（用几何符号 ◈ ◇ ◆ ◻ ◼ ◎）
- 按钮/卡片统一 8px 圆角

---

## 页面结构

### 用户前台（7 个页面）

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` | 工作台 | 任务输入 + 常用 Agent + 最近任务 + 交付物 |
| `/chat/career` | 职场顾问 | 对话页面（现有 CareerAdvisor 清爽化） |
| `/chat/super` | 超级智能体 | 对话页面（现有 SuperAgent 清爽化） |
| `/knowledge` | 知识库 | 文档上传/列表 |
| `/artifacts` | 交付物 | 我的交付物列表 |
| `/favorites` | 收藏 | 收藏的消息 |
| `/usage` | 用量 | 个人用量统计 |

### 管理后台（4 个页面，`/admin` 下）

| 路径 | 页面 | 说明 |
|------|------|------|
| `/admin` | 管理总览 | 入口卡片 |
| `/admin/compare` | Agent 对比 | 双 Agent 回答对比 |
| `/trace/:traceId` | 轨迹详情 | 执行时间线（管理员调试用） |
| `/admin` | Trace 列表 | 待做 |

### 隐藏
- LoveMaster 路由保留，不暴露导航

---

## 实施 Phase

### Phase 1：全局基础（已完成）
- [x] style.css 设计 Token
- [x] AppLayout.vue（Topbar + Sidebar）
- [x] App.vue 改用 AppLayout
- [x] 路由改造
- [x] 隐藏 LoveMaster
- [x] 品牌名 WorkPilot

### Phase 2：工作台首页
- [ ] Home.vue 改版
  - 顶部：问候语 + 任务输入框
  - 常用 Agent 卡片（职场顾问 / 超级智能体 / 知识库问答）
  - 最近任务列表（来自 trace API）
  - 我的交付物（来自 artifact API）
  - 右侧：知识库状态 + 工具可用性

### Phase 3：对话页面清爽化
- [ ] CareerAdvisor.vue
  - 移除顶部多余按钮（轨迹/收藏/导出/导入/用量/对比 → 全在 sidebar）
  - 保留核心：sidebar 会话列表 + 聊天区 + 输入框
  - 样式对齐新 Token（圆角/颜色/间距）
- [ ] SuperAgent.vue
  - 同样清理 header，统一风格

### Phase 4：TraceDetail 改版
- [ ] 三栏布局：时间线 / 节点详情 / 运行指标
- [ ] 用户看到简化状态（准备中/检索中/生成中/完成）
- [ ] 管理员看到完整详情（输入输出/工具调用/Token/延迟）

### Phase 5：知识库 & 交付物页面统一
- [ ] KnowledgeBase.vue 样式对齐
- [ ] ArtifactAdmin.vue 样式对齐
- [ ] Favorites.vue 样式对齐
- [ ] UsageDashboard.vue 样式对齐

---

## 文件清单

| 文件 | 操作 | Phase |
|------|------|-------|
| src/style.css | 重写 | 1 ✅ |
| src/App.vue | 改用 AppLayout | 1 ✅ |
| src/components/AppLayout.vue | 新建 | 1 ✅ |
| src/router/index.js | 路由改造 | 1 ✅ |
| src/views/Home.vue | 重写为工作台 | 2 |
| src/views/CareerAdvisor.vue | 清爽化 | 3 |
| src/views/SuperAgent.vue | 清爽化 | 3 |
| src/views/TraceDetail.vue | 三栏改版 | 4 |
| src/views/KnowledgeBase.vue | 样式对齐 | 5 |
| src/views/ArtifactAdmin.vue | 样式对齐 | 5 |
| src/views/Favorites.vue | 样式对齐 | 5 |
| src/views/UsageDashboard.vue | 样式对齐 | 5 |
| src/views/AdminDashboard.vue | 管理入口 | 1 ✅ |

---

## 用户 vs 管理员分离

**用户看到的 sidebar：**
```
◈ 工作台
◇ 职场顾问
◆ 超级智能体
◻ 知识库
◼ 交付物
★ 收藏
◎ 用量
──────────
⚙ 管理后台
```

**管理员额外看到（/admin 下）：**
```
Agent 对比
Trace 调试
用量统计（全局）
系统设置
```

Trace 详情页 `/trace/:traceId` 不在 sidebar，只能从管理后台或最近任务列表进入。
