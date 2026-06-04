# P3 前端时间线可视化 — 实施计划 v3

> 任务 12.1 + 13

## 前置修复：后端鉴权统一

### 1) 新建 AuthService — `auth/AuthService.java`

```java
@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtUtil jwtUtil;

    /**
     * Resolves JWT from URL param or Authorization header, validates, returns userId.
     * Throws BusinessException(NOT_LOGIN) on failure — callers don't need null-check.
     */
    public String authenticate(String tokenParam, String authHeader) {
        String token = resolveToken(tokenParam, authHeader);
        if (StrUtil.isBlank(token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        String userId = jwtUtil.validateToken(token);
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        return userId;
    }

    private String resolveToken(String tokenParam, String authHeader) {
        if (tokenParam != null && !tokenParam.isBlank()) return tokenParam;
        if (authHeader != null && authHeader.startsWith("Bearer ")) return authHeader.substring(7);
        return null;
    }
}
```

### 2) 改造 TraceController

- 注入 AuthService
- 三个接口加 `@RequestHeader(value = "Authorization", required = false) String authHeader`
- 调用 `authService.authenticate(token, authHeader)` 直接拿 userId，不再 null-check
- GlobalExceptionHandler 统一处理 BusinessException

### 3) 改造 AiController

- 注入 AuthService，删除私有 `resolveToken()` 和 `authenticate()` 方法
- orchestrator 接口改用 `authService.authenticate()`

### 4) 补充分页支持

```java
@GetMapping("/chat/{chatId}")
public Result<List<ExecutionTrace>> getTracesByChat(
        @PathVariable String chatId,
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(value = "token", required = false) String token,
        @RequestHeader(value = "Authorization", required = false) String authHeader) {
    String userId = authService.authenticate(token, authHeader);
    if (!sessionManager.isOwner(userId, chatId)) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    List<ExecutionTrace> traces = traceRepository.findByChatId(chatId, pageNum, pageSize);
    return Result.success(traces);
}
```

TraceRepository 新增带分页的 `findByChatId(chatId, pageNum, pageSize)` 和 `findByUserId(userId, pageNum, pageSize)`。

### 5) TraceControllerTest 补充

- Authorization header 鉴权测试
- 分页参数测试
- 401/403 场景覆盖

---

## Step 1：API 层 — `src/api/index.js`

```js
export const getTrace = (traceId) => request.get(`/trace/${traceId}`)
export const getTracesByChat = (chatId, pageNum = 1, pageSize = 20) =>
  request.get(`/trace/chat/${chatId}`, { params: { pageNum, pageSize } })
export const getTracesByUser = (userId, pageNum = 1, pageSize = 20) =>
  request.get(`/trace/user/${userId}`, { params: { pageNum, pageSize } })
```

---

## Step 2：组件分层

```
views/
 └─ TraceDetail.vue          ← 页面：路由入口 + 自动轮询
components/
 └─ TraceTimelineView.vue    ← 展示：纯渲染，自己判断是否实时
```

### TraceTimelineView.vue

**Props：** 只接收 `trace`，不接收 `realtime`

```js
const props = defineProps({ trace: Object })
const isRealtime = computed(() => props.trace?.status === 'RUNNING')
```

渲染逻辑不变。

### TraceDetail.vue

- 从路由获取 traceId
- 调用 `getTrace(traceId)` 加载
- 如果 `trace.status === 'RUNNING'`，启动 2s 轮询直到终态
- 传递给 `<TraceTimelineView :trace="trace" />`

```js
let pollTimer = null
const startPolling = () => {
  pollTimer = setInterval(async () => {
    const res = await getTrace(route.params.traceId)
    trace.value = res.data.data
    if (trace.value.status !== 'RUNNING') {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }, 2000)
}
onBeforeUnmount(() => { if (pollTimer) clearInterval(pollTimer) })
```

---

## Step 3：CareerAdvisor.vue 实时 trace

```js
import { shallowReactive, computed } from 'vue'

const traceMap = shallowReactive(new Map())
const traceSteps = computed(() =>
  Array.from(traceMap.values()).sort((a, b) => a.sequence - b.sequence)
)
```

每条 AI 消息气泡底部加「查看执行轨迹」链接，点击跳转 `/trace/:traceId`。

---

## Step 4：路由

```js
{
  path: '/trace/:traceId',
  name: 'TraceDetail',
  component: () => import('../views/TraceDetail.vue'),
  meta: { title: '执行轨迹 - 职场生存智囊' }
}
```

---

## Step 5：轨迹入口

不在 header 加按钮，改为：
- 每条 AI 消息底部显示「🔍 查看执行轨迹」链接
- 点击跳转 TraceDetail 页面

---

## 文件变更清单

| 文件 | 操作 |
|------|------|
| `src/main/java/.../auth/AuthService.java` | **新建** |
| `src/main/java/.../controller/TraceController.java` | 改用 AuthService + 分页 |
| `src/main/java/.../controller/AiController.java` | 改用 AuthService |
| `src/main/java/.../trace/TraceRepository.java` | 新增分页查询方法 |
| `src/test/.../trace/TraceControllerTest.java` | 补充鉴权 + 分页测试 |
| `yu-ai-agent-frontend/src/api/index.js` | 新增 trace API |
| `yu-ai-agent-frontend/src/components/TraceTimelineView.vue` | **新建** |
| `yu-ai-agent-frontend/src/views/TraceDetail.vue` | **新建** |
| `yu-ai-agent-frontend/src/router/index.js` | 新增路由 |
| `yu-ai-agent-frontend/src/views/CareerAdvisor.vue` | 实时 trace + 消息底部链接 |
