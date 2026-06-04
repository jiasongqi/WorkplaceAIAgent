# 分层重构计划

> 目标：将已有代码对齐 java-layered-code-standard

## 违反清单

| # | 违反 | 当前代码 | 正确做法 |
|---|------|----------|----------|
| 1 | Controller 含业务逻辑 | SessionController 里做归属校验 | 移到 AppService |
| 2 | Controller 调多个 Repository | 直接调 sessionManager + favoriteRepository + chatMemoryAdapter | 只调一个 AppService |
| 3 | Controller 做鉴权 | authService.authenticate() 在 Controller | 移到 AppService 或用 @RequestAttribute |
| 4 | DTO 内嵌在 Controller | RenameRequest 定义在 Controller 里 | 独立 DTO 文件 |
| 5 | Controller catch 异常 | 部分 Controller 有 try-catch | 让 @RestControllerAdvice 处理 |

## 重构范围

### 需要新建的 AppService

| AppService | 职责 |
|------------|------|
| SessionAppService | 会话 CRUD + 重命名 + 归档 + 软删除 + 搜索 + 消息历史 |
| FavoriteAppService | 收藏 CRUD + orphaned 标记 |
| ExportAppService | 导出/导入 |
| DocumentAppService | 文档 CRUD + 去重 |

### 需要新建的 DTO

| DTO | 类型 | 说明 |
|-----|------|------|
| RenameRequest | Request | 重命名请求 |
| AddFavoriteRequest | Request | 添加收藏请求 |
| ImportResult | Response | 导入结果 |
| SessionSearchResponse | Response | 搜索结果 |
| FavoriteResponse | Response | 收藏详情 |

### 需要改动的 Controller

| Controller | 改动 |
|------------|------|
| SessionController | 瘦身：只做参数绑定 + 调 SessionAppService |
| FavoriteController | 瘦身：只做参数绑定 + 调 FavoriteAppService |
| ExportController | 瘦身：只做参数绑定 + 调 ExportAppService |
| DocumentController | 瘦身：只做参数绑定 + 调 DocumentAppService |

## 鉴权方案

统一用 `@RequestAttribute(required = false) String userId`：
- 由一个 Filter/Interceptor 从 Authorization header 或 token 参数解析 userId
- Controller 不再手动调 authService.authenticate()
- AppService 拿到 userId 做业务判断

**但当前项目没有 Filter 基础设施**，所以短期方案：
- Controller 保留 `authService.authenticate()` 调用
- 只传 userId 给 AppService
- AppService 做所有业务判断

## 执行顺序

1. 新建 4 个 AppService
2. 新建 DTO 文件
3. 重构 4 个 Controller
4. 验证编译通过
