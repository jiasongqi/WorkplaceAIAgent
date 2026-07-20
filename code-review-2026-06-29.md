## WorkPilot (yu-ai-agent) 生产级代码审查报告

**项目**: 全场景职场生存智囊 Agent | **技术栈**: Java 21 + Spring Boot 3.4.4 + Spring AI 1.0.0 + Vue 3
**审查范围**: 327 个 Java 源文件、11 个前端视图、完整 API 层 | **审查视角**: 真实用户 + 生产环境

---

## 一、总体评价

项目在 Agent 架构设计上有明显亮点：ReAct 模式实现完整、四层记忆系统真正串联、NLU 管线只需一次 LLM 调用、质量审查已接入主流程。这些不是 demo 级别的空壳，而是有真实工程深度的实现。

但从「用户真实使用」和「生产环境跑起来」两个角度看，存在一批必须修复才能上线的问题。以下按严重程度分 P0（必须修）、P1（强烈建议修）、P2（影响体验和质量）三档。

---

## 二、P0 — 必须修复（安全漏洞 + 数据风险）

### 2.1 登录接口无任何身份验证

`SessionController.login()` 接受任意 `username` 和 `userId` 参数，直接返回合法 JWT。任何人调用 `/api/session/login?userId=admin&username=admin` 就能拿到管理员 Token。这不是认证系统——JWT 只证明令牌由本服务器签发，不证明用户是声称的那个人。

**影响**: 整个系统等于没有认证。任意用户可冒充任何人，包括 admin。
**修复方向**: 接入真实认证（OAuth2、密码校验、或至少接入飞书/钉钉 SSO）。短期内可加一个密码字段做基础校验。

### 2.2 CORS 全放开 + 允许携带凭证

`CorsConfig` 配置了 `allowedOriginPatterns("*")` + `allowCredentials(true)`，任何网站都能向你的 API 发带 JWT 的跨域请求。配合 2.1 的登录漏洞，攻击链完整。

**影响**: CSRF + 跨域数据窃取。
**修复方向**: 生产环境限定前端域名列表。

### 2.3 application.yml 硬编码了真实云数据库地址

`jdbc:postgresql://pc-d9j153n6x60iqotrq.rwlb.ap-southeast-5.rds.aliyuncs.com:5432/arkham`，用户名密码是 `test/test`。即使这是开发库，把真实 RDS 端点提交到代码仓库意味着任何能访问仓库的人都能尝试连接。

**影响**: 数据库暴露风险。
**修复方向**: 从 yml 中移除真实地址，全部走 `${DB_HOST}` 环境变量，`.gitignore` 排除含敏感信息的配置文件。

### 2.4 前端 Markdown 渲染存在 XSS 漏洞

`CareerAdvisor.vue` 用 `v-html="renderMarkdown(msg.content)"` 直接渲染 `marked.parse()` 的输出，没有做 HTML 消毒。如果后端 Agent 返回的 Markdown 中包含 `<script>` 或 `onerror` 属性，会直接在前端执行。

**影响**: 存储型/反射型 XSS。
**修复方向**: 引入 `DOMPurify`，渲染前做消毒：`DOMPurify.sanitize(marked.parse(content))`。

### 2.5 知识库文档接口完全无鉴权

`DocumentController` 的上传、列表、删除接口没有任何认证。任何人都能上传文档到知识库、查看已有文档、或删除别人的文档。

**影响**: 数据污染、信息泄露、恶意删除。
**修复方向**: 加上 `AuthService.authenticate()` 校验。

### 2.6 WebScrapingTool 存在 SSRF 风险

该工具接受任意 URL 并用 Jsoup 抓取内容，没有对内网地址做过滤。攻击者可让 Agent 调用 `http://169.254.169.254/latest/meta-data/`（云实例元数据）或内网其他服务。

**影响**: 内网探测、云凭证窃取。
**修复方向**: 加 URL 白名单或黑名单（禁止 `169.254.x.x`、`10.x.x.x`、`127.x.x.x` 等内网段）。

---

## 三、P1 — 强烈建议修复（生产稳定性问题）

### 3.1 飞书/钉钉日历服务无 HTTP 超时

`FeishuCalendarService` 和 `DingTalkCalendarService` 直接 `new RestTemplate()`，没有配置连接超时和读取超时。如果第三方 API 响应慢或挂起，调用线程会无限阻塞，最终耗尽线程池。

**修复方向**: 用 `RestTemplateBuilder` 设置 `connectTimeout(5s)` + `readTimeout(15s)`。

### 3.2 PersistentMessageRepository 并发写入可能损坏文件

`save()` 先修改内存索引（`computeIfAbsent`，原子操作），然后在锁外写文件。两个线程同时保存同一个会话时，文件可能被写坏，导致消息丢失。

**修复方向**: 把 `saveToFile()` 也纳入 `synchronized` 块，或用 `ReentrantLock`。

### 3. MemoryCoordinator.layerCache 无上限增长

`ConcurrentHashMap<String, String>` 做 last-known-good 缓存，key 是 `userId:layerName`。没有 TTL，没有 eviction。用户量增长后，这个 Map 会一直膨胀直到 OOM。

**修复方向**: 换成 Caffeine/Guava Cache，设 maxSize + expireAfterWrite。

### 3.3 SSE Emitter 超时后异步任务不取消

`OrchestratorAgent` 中 SseEmitter 超时 300 秒，但 `CompletableFuture.runAsync()` 的任务不会被取消。LLM 如果卡住，HTTP 连接断了但后端线程还在跑，浪费资源且可能在 emitter 已关闭后尝试写入。

**修复方向**: 在 `emitter.onTimeout()` 回调中调用 `future.cancel(true)`，Agent 主循环检查 `Thread.interrupted()`。

### 3.4 所有外部 API 调用无重试机制

WebSearchTool、WebScrapingTool、日历服务——全部是单次调用，一次失败就直接抛异常。在真实网络环境下，瞬时故障（DNS 抖动、连接超时）会导致不必要的用户侧失败。

**修复方向**: 引入 Spring Retry 或简单的指数退避重试（最多 2-3 次）。

### 3.5 NLU 对话状态仅存在内存中

`InMemoryConversationStateStore` 存储 NLU 管线的对话状态。服务重启后所有用户的上下文状态丢失，多轮对话的实体跟踪、意图连续性全部中断。代码注释也提到了需要 Redis。

**修复方向**: 接入 Redis 或复用已有的 JPA 持久层。

### 3.6 无 @Transactional 保护

自定义的 Repository 类（`AppointmentRepository`、`PersistentMessageRepository` 等）没有事务注解。JPA 的默认事务管理只覆盖 Spring Data Repository，不覆盖这些手写类。并发场景下数据一致性无法保证。

**修复方向**: 关键写操作加 `@Transactional`。

### 3.7 L4 经验存储用 SimpleVectorStore 而非 PGVector

`SimpleVectorStore` 是全量加载到内存的 JSON 文件。用户量增长后，向量数据会超出内存容量，且不支持并发查询优化。项目已经配了 PGVector 依赖（docker-compose 中有 `pgvector:pg16`），但没有在 L4 层使用。

**修复方向**: 将 L4 切换到已有的 PgVector 配置。

---

## 四、P2 — 影响用户体验和工程质量

### 4.1 用户体验层面

**错误恢复不友好**: 消息发送失败时只显示「连接出现问题，请重试」，没有重试按钮。SSE 断连后直接关闭 EventSource（禁用了浏览器内置的自动重连）。用户只能手动重新发送。

**文件上传失败反馈弱**: 上传失败以 AI 消息形式内嵌显示（`⚠️ 文件上传失败`），容易被忽略。应该有独立的 Toast 通知。

**多页面主题不一致**: CareerAdvisor 和 Home 页面是深色主题，但 KnowledgeBase、ArtifactAdmin、Favorites、UsageDashboard、CompareView 使用 `background: #f0f2f5` 浅灰背景。在页面间导航时视觉跳跃明显。

**移动端无导航菜单**: 768px 以下隐藏了顶部导航链接和部分按钮，但没有提供汉堡菜单或抽屉导航。移动端用户几乎无法到达知识库、收藏、用量统计等页面。

**知识库页面显示「重试」按钮但点击无效**: 文档上传失败后显示重试按钮，但实现只是弹提示说「需要重新上传」。

**ArtifactAdmin 权限判断 Bug**: `isAdmin.value = data.username === 'admin' || true` 永远为 true，任何人都能看到管理界面。

### 4.2 API 设计层面

**无 Bean Validation**: 整个项目没有使用 `@Valid`、`@NotNull`、`@NotBlank` 等注解。请求参数校验全靠业务层 if-else，Controller 层不做校验。用户传入空字符串或超长内容时，报错信息不够友好。

**Swagger 文档零标注**: 虽然配了 Knife4j + SpringDoc，但所有 Controller 和 DTO 都没有 `@Tag`、`@Operation`、`@Schema` 注解。自动生成的文档只有类名和方法名，对前端和第三方对接者几乎没用。

**GET 请求做写操作**: `/ai/ai_chat/chat/sync`、`/ai/orchestrator/chat` 等会产生实际对话（写入记忆、消耗 token）的接口用 GET 方法。不符合 RESTful 语义，且 GET 参数有长度限制（用户发长消息可能被截断）。

**FeedbackController 的 userId 是假的**: 从 Authorization header 截取前 20 个字符作为 userId，而不是解码 JWT。反馈数据无法关联到真实用户。

### 4.3 代码质量层面

**日志中记录了完整的用户消息和 AI 提示词**: `MyLoggerAdvisor` 在 INFO 级别记录完整 prompt 和 response。职场咨询场景下，用户消息可能包含个人薪资、公司信息、离职原因等敏感内容。生产环境应降级到 DEBUG 或做脱敏。

**提示注入检测只覆盖英文**: `PromptInjectionDetector` 的正则模式全是英文（"ignore previous instructions"、"you are now"）。中文注入（"忽略之前的指令"、"你现在是..."）会直接绕过。考虑到用户群是中文用户，这是主要攻击面。

**V3 工作流引擎节点执行是空壳**: `WorkflowRuntime` 支持 6 种节点类型（Agent/Tool/Condition/Parallel/Loop/Approval），但 Agent 节点只打日志「scheduled」不真正执行，Tool 节点同理，Parallel 是顺序模拟。代码能跑但没有实际功能。

**ConsultationAgent 手写 JSON 解析**: 用正则 `extractJsonValue` 提取 JSON 字段，而不是用 Jackson。对格式变动的容错性差。

**前端 API 层 process.env.NODE_ENV 在 Vite 中不生效**: Vite 使用 `import.meta.env.MODE`，不是 `process.env.NODE_ENV`。这会导致生产构建使用错误的 API 地址。

### 4.4 测试覆盖缺口

项目有 40 个测试文件，记忆系统（9 个）和 Trace 系统（9 个）测试较好。但以下关键模块零测试：

- `AuthService` / `JwtUtil` — 认证核心
- `AccessDecisionService` — 访问控制
- `PromptInjectionDetector` — 安全防护
- `AgentCircuitBreaker` — 熔断器
- `SessionManager` — 会话生命周期
- `GlobalExceptionHandler` — 异常处理
- 除 `TraceController` 外的所有 Controller
- `LocalProcessSandbox` / `DockerSandbox` — 沙箱执行
- `FeishuCalendarService` / `DingTalkCalendarService` — 日历集成
- `DataExportService` / `DataImportService` — 数据导入导出

**建议优先级**: AuthService > PromptInjectionDetector > SessionManager > AgentCircuitBreaker > 各 Controller。

---

## 五、架构亮点（做得好的地方）

公平起见，以下设计值得肯定：

**ReAct Agent 实现扎实**: think/act 分离、工具调用 30 秒超时 + 2 次自动重试、基于 Embedding 的循环检测（余弦相似度 > 0.88 触发引导）——这些不是教科书代码，是真正考虑了生产环境异常的实现。

**四层记忆系统真正串联**: 并行查询 + 超时降级到 last-known-good 缓存、异步提取管线不阻塞主流程、Token 预算按层分配（L1=60%, L2=15%, L3=10%, L4=15%）+ 中英文混合文本的行/句边界截断。

**NLU 管线一次 LLM 调用搞定**: 意图识别 + 实体抽取 + 指标 + 时间范围 + 域 + 动作，全部在一次调用中完成。KeywordRouter 做快速路径，80% 的单意图查询不需要 LLM 调用。

**线程池隔离合理**: 5 个专用线程池（Agent/Tool/Profile/MemoryExtraction/MemoryQuery），防止工具阻塞饿死 Agent 线程。CallerRunsPolicy 在大多数场景下合理。

**前端主聊天视图功能丰富**: SSE 流式渲染、会话管理（创建/归档/搜索/恢复）、质量审查可视化、Trace 时间线、语音输入、文件上传、删除撤回 Toast——CareerAdvisor.vue 的 1240 行代码密度很高。

---

## 六、修复优先级路线图

| 阶段 | 内容 | 预计工作量 |
|------|------|-----------|
| **第一周** | 修复 P0 安全问题（登录认证、CORS、DB 凭证、XSS、文档鉴权、SSRF） | 3-5 天 |
| **第二周** | 修复 P1 稳定性问题（超时配置、并发安全、缓存上限、任务取消、重试机制） | 3-4 天 |
| **第三周** | 补充关键测试（Auth、Security、Controller）+ 前端主题统一 + API 规范化 | 4-5 天 |
| **持续** | 中文提示注入检测、V3 工作流实际执行、NLU 状态持久化、Swagger 注解 | 按需 |

---

*审查时间: 2026-06-29 | 审查范围: 327 Java files + Vue frontend | 项目版本: 0.0.1-SNAPSHOT*
