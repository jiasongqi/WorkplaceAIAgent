# AgentOps 控制台改版计划

## Phase 1: 全局基础 + 布局

### 1.1 设计 Token（style.css 全局变量）
```css
--bg: #f7f8fa;
--surface: #ffffff;
--surface-muted: #f1f3f5;
--border: #e5e7eb;
--text: #111827;
--text-muted: #6b7280;
--primary: #2563eb;
--success: #16a34a;
--warning: #d97706;
--danger: #dc2626;
--radius: 8px;
```

### 1.2 AppLayout.vue（新建）
- Topbar: 产品名「Yu Agent」/ 环境标签 / 搜索 / 用户
- Sidebar: 9 个导航项（Overview / Agents / Runs / Traces / Knowledge / Artifacts / Usage / Compare / Settings）
- 主内容区 `<router-view />`
- 浅色、密集、专业风格，8px 圆角，边框为主，少阴影

### 1.3 隐藏 LoveMaster
- Home.vue 移除入口卡片
- AppLayout sidebar 不显示
- 路由保留不删

### 1.4 路由改造
- `/` → Overview Dashboard（AppLayout 内）
- `/agents` → Agent 列表（复用现有入口）
- `/runs` → 运行记录（复用 trace 列表）
- `/traces` → 轨迹列表
- `/knowledge` → KnowledgeBase
- `/artifacts` → ArtifactAdmin
- `/usage` → UsageDashboard
- `/compare` → CompareView
- `/settings` → 设置（占位）
- `/chat/:agentType` → CareerAdvisor / SuperAgent（Agent 详情页）
- `/trace/:traceId` → TraceDetail

---

## Phase 2: Overview Dashboard（Home.vue 改版）

### 布局
```
┌─ 顶部状态条 ──────────────────────────────────┐
│ 🟢 生产环境  │  后端: 已连接  │  模型: qwen3.5  │
└───────────────────────────────────────────────┘

┌─ KPI Cards ───────────────────────────────────┐
│ 今日运行 │ 成功率 │ 平均延迟 │ Token 消耗     │
│   128    │ 96.2%  │  4.8s   │    182k        │
└───────────────────────────────────────────────┘

┌─ 最近运行表格 ────────────────────────────────┐
│ 任务          Agent     状态    耗时   Token  │
│ 简历优化      Career    ✓      6.2s   4.1k   │
│ 市场调研      Super     ⟳      21s    9.8k   │
│ RAG 查询      RAG       ✗      2.1s   1.2k   │
└───────────────────────────────────────────────┘

┌─ Agent 状态 ──────────┐ ┌─ 知识库 & 工具 ────┐
│ 🟢 职场顾问           │ │ 📚 知识库: 3 文档  │
│ 🟢 超级智能体         │ │ 🔧 工具: 7 可用    │
│ 🟢 知识库助手         │ │ 📦 交付物: 12      │
└───────────────────────┘ └────────────────────┘
```

### 数据来源
- KPI: `GET /usage/stats`
- 最近运行: `GET /trace/user/{userId}?pageSize=10`
- Agent 状态: 静态列表（后端健康检查可选）
- 知识库: `GET /document/list`

---

## Phase 3: TraceDetail 改版

### 布局（三栏）
```
┌─ 左侧：时间线 ──┬─ 中间：节点详情 ──┬─ 右侧：指标 ─┐
│ ● 意图识别  10ms │                   │ 总耗时: 6.2s  │
│ ● 路由分发   5ms │ [选中节点详情]     │ Token: 4.1k   │
│ ● 画像注入   8ms │  输入 / 输出      │ 状态: SUCCESS │
│ ● 子Agent   2.3s │  工具调用         │ 步骤: 6       │
│ ● 质量审查  1.8s │  错误信息         │ 风险: LOW     │
│ ● 完成       —   │                   │               │
└──────────────────┴───────────────────┴───────────────┘
```

---

## 实施顺序

1. style.css 设计 Token
2. AppLayout.vue + 路由改造
3. Home.vue Overview Dashboard
4. TraceDetail.vue 三栏改版
5. CareerAdvisor.vue header 清理（移除多余按钮，统一到 sidebar）
6. 隐藏 LoveMaster

## 文件清单

| 文件 | 操作 |
|------|------|
| src/style.css | 重写设计 Token |
| src/App.vue | 改用 AppLayout |
| src/components/AppLayout.vue | **新建** |
| src/views/Home.vue | 重写为 Dashboard |
| src/views/TraceDetail.vue | 重写为三栏布局 |
| src/router/index.js | 路由改造 |
| src/views/CareerAdvisor.vue | header 清理 |
