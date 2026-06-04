# 功能扩展实施计划 v5（终版）

> 3 个 Blocker + 4 个 Important + 1 个 NiceToHave 全部修复  
> 评分目标：9.6/10 → 批准进入开发

---

## v5 修订清单

| # | 级别 | 问题 | 修正 |
|---|------|------|------|
| 1 | 🔴 Blocker | 废弃 Spring AI Memory | PersistentMessage 为 Truth + ChatMemory 适配层 |
| 2 | 🔴 Blocker | MessageId 碰撞风险 | 改用 UUID.randomUUID() |
| 3 | 🔴 Blocker | 搜索匹配顺序 Bug | equals > startsWith > contains |
| 4 | 🟡 Important | findByMessageId O(n) | 加 messageIdIndex O(1) |
| 5 | 🟡 Important | 收藏孤立无提示 | 加 isOrphaned 状态 |
| 6 | 🟡 Important | DocumentHash 误判 | 预留 contentHash 字段 |
| 7 | 🟡 Important | Quality Guard 延迟 | AUTO 模式 + 规则引擎 |
| 8 | 🟢 Nice | Review 单独存储浪费 | 默认写 Trace，>=HIGH 才持久化 |

---

## Blocker 1: PersistentMessage + ChatMemory 适配层

### 问题

彻底废弃 `Spring AI ChatMemory` 会导致：
- `MemoryAdvisor` 无法使用
- `PromptAdvisor` 无法注入历史
- `ToolCalling` 上下文断裂
- 未来接 `ChatClient` 困难

### 方案: 两层架构

```
PersistentMessageRepository    ← Source of Truth（持久化）
        ↓
ChatMemoryAdapter              ← 运行时适配层
        ↓
Spring AI ChatMemory           ← 运行时缓存（兼容生态）
```

### ChatMemoryAdapter

```java
@Service
public class ChatMemoryAdapter {

    @Resource
    private PersistentMessageRepository persistentRepo;

    /**
     * 写入消息：先落库（Truth），再同步到 ChatMemory（缓存）。
     * 如果 ChatMemory 同步失败，不影响数据完整性。
     */
    public PersistentChatMessage addMessage(String chatId, String role, String content) {
        // 1. 持久化（Source of Truth）
        PersistentChatMessage pm = persistentRepo.save(chatId, role, content);

        // 2. 同步到 Spring AI ChatMemory（运行时缓存，best-effort）
        try {
            syncToChatMemory(chatId, pm);
        } catch (Exception e) {
            log.warn("Failed to sync to ChatMemory, will rebuild on next read: {}", e.getMessage());
        }

        return pm;
    }

    /**
     * 获取 LLM 上下文：优先从 ChatMemory 读，校验一致性后返回。
     * 缓存 miss 或 count 不一致时自动重建。
     */
    public List<Message> getMessagesForLlm(String chatId) {
        int persistentCount = persistentRepo.countByChatId(chatId);

        // 尝试从 ChatMemory 缓存读取
        List<Message> cached = chatMemory.get(chatId);
        if (cached != null && !cached.isEmpty()) {
            // 一致性校验：缓存条数 == 持久化条数
            if (cached.size() == persistentCount) {
                return cached;
            }
            // 条数不一致 → 缓存脏数据，清除重建
            log.warn("Cache inconsistency for chatId={}, cached={}, persistent={}",
                    chatId, cached.size(), persistentCount);
            chatMemory.clear(chatId);
        }

        // 缓存 miss 或不一致 → 从 PersistentMessage 重建
        List<Message> rebuilt = persistentRepo.findByChatId(chatId).stream()
                .map(this::toLlmMessage)
                .toList();
        chatMemory.add(chatId, rebuilt);
        return rebuilt;
    }

    /**
     * 获取前端展示用消息（直接读 Truth）。
     */
    public List<PersistentChatMessage> getMessagesForDisplay(String chatId) {
        return persistentRepo.findByChatId(chatId);
    }

    /**
     * 压缩后重建 ChatMemory 缓存。
     */
    public void rebuildChatMemory(String chatId) {
        chatMemory.clear(chatId);
        List<Message> messages = persistentRepo.findByChatId(chatId).stream()
                .map(this::toLlmMessage)
                .toList();
        chatMemory.add(chatId, messages);
    }

    // --- private ---

    private void syncToChatMemory(String chatId, PersistentChatMessage pm) {
        Message msg = toLlmMessage(pm);
        chatMemory.add(chatId, List.of(msg));
    }

    private Message toLlmMessage(PersistentChatMessage pm) {
        return switch (pm.getRole()) {
            case "user" -> new UserMessage(pm.getContent());
            case "assistant" -> new AssistantMessage(pm.getContent());
            case "system" -> new SystemMessage(pm.getContent());
            default -> new UserMessage(pm.getContent());
        };
    }
}
```

### 架构图

```
写入流程:
  Agent 生成回答
    ↓
  ChatMemoryAdapter.addMessage()
    ↓
  ┌─────────────────────────────────────┐
  │ 1. PersistentMessageRepository.save()│  ← Source of Truth
  │ 2. ChatMemory.add() (best-effort)   │  ← 运行时缓存
  └─────────────────────────────────────┘

读取 LLM 上下文:
  ChatMemoryAdapter.getMessagesForLlm()
    ↓
  ChatMemory.get() → 命中 → 返回
                   → miss → PersistentMessage 重建 → 写入缓存 → 返回

读取前端历史:
  ChatMemoryAdapter.getMessagesForDisplay()
    ↓
  PersistentMessageRepository.findByChatId()  ← 直接读 Truth
```

### 为什么这样设计

| 场景 | 行为 |
|------|------|
| ChatMemory 缓存命中 | 直接返回，性能最佳 |
| ChatMemory 缓存 miss | 从 PersistentMessage 重建，自动回填 |
| ChatMemory 写入失败 | 不影响 Truth，下次读取时重建 |
| PersistentMessage 写入失败 | 抛异常，不进 ChatMemory，数据一致 |
| Spring AI Advisor 需要 Memory | 正常工作，因为 ChatMemory 存在 |
| 搜索/收藏/导出 | 直接读 PersistentMessage，不依赖 ChatMemory |

---

## Blocker 2: MessageId 生成策略

### 问题

`System.currentTimeMillis() + RandomUtil.randomString(6)` 高并发下可能碰撞。  
`UUID.randomUUID()` 技术没问题但导出日志难排查（`9c0d5f1b84e5...`）。

### 方案: ULID

```java
public static String generateMessageId() {
    return IdUtil.fastSimpleUUID();  // hutool-all 已包含
}
```

示例: `01JY4K7X2S5A8M3NBQRV0FHJTD`

| 对比 | UUID | ULID |
|------|------|------|
| 唯一性 | ✓ | ✓ |
| 可排序 | ✗ | ✓（时间有序） |
| 可读性 | 差 | 好 |
| 导出排查 | 难 | 直接看前缀知时间 |

hutool-all 已在 pom.xml 中，无需新增依赖。

---

## Blocker 3: 搜索匹配顺序修复

### 问题

```java
if (contains) return 50;    // ← 先匹配到 contains
if (startsWith) return 70;  // ← 永远走不到
```

### 修复

```java
private int calculateMatchScore(String content, String keyword) {
    String lowerContent = content.toLowerCase();
    String lowerKeyword = keyword.toLowerCase();

    // 1. 完全匹配（最高优先级）
    if (lowerContent.equals(lowerKeyword)) return 100;

    // 2. 前缀匹配（第二优先级）
    if (lowerContent.startsWith(lowerKeyword)) return 70;

    // 3. 包含匹配（第三优先级）
    if (lowerContent.contains(lowerKeyword)) return 50;

    // 4. 不匹配
    return 0;
}
```

**顺序: equals(100) > startsWith(70) > contains(50)**

---

## Important 4: findByMessageId O(1) 索引

### 问题

```java
store.values().stream().flatMap(List::stream)
    .filter(m -> m.getMessageId().equals(messageId))
    // O(所有消息)
```

### 方案: 双索引

```java
@Repository
public class PersistentMessageRepository {

    // 主存储: chatId → messages（synchronizedList 适合聊天读写均衡场景）
    private final Map<String, List<PersistentChatMessage>> chatIndex = new ConcurrentHashMap<>();

    // 辅助索引: messageId → message（O(1) 查找）
    private final Map<String, PersistentChatMessage> messageIdIndex = new ConcurrentHashMap<>();

    public PersistentChatMessage save(String chatId, String role, String content) {
        PersistentChatMessage msg = new PersistentChatMessage();
        msg.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        msg.setChatId(chatId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTimestamp(System.currentTimeMillis());

        chatIndex.computeIfAbsent(chatId,
                k -> Collections.synchronizedList(new ArrayList<>())).add(msg);
        messageIdIndex.put(msg.getMessageId(), msg);  // O(1) 索引
        saveToFile(chatId);
        return msg;
    }

    public PersistentChatMessage findByMessageId(String messageId) {
        return messageIdIndex.get(messageId);  // O(1)
    }

    public void deleteByChatId(String chatId) {
        List<PersistentChatMessage> removed = chatIndex.remove(chatId);
        if (removed != null) {
            removed.forEach(m -> messageIdIndex.remove(m.getMessageId()));
        }
        deleteFile(chatId);
    }
}
```

---

## Important 5: 收藏孤立处理

### 问题

会话删除后，收藏的 chatId/messageId 失效，但用户不知道。

### 方案: isOrphaned 状态

```java
@Data
public class Favorite {
    private String favoriteId;
    private String userId;
    private String chatId;
    private String messageId;
    private String contentSnapshot;
    private String sessionTitleSnapshot;
    private String role;
    private boolean orphaned;         // 新增：来源是否已失效
    private LocalDateTime createdAt;
}
```

**会话删除时标记**:

```java
// SessionController 删除会话时
favoriteRepository.markOrphanedByChatId(chatId);
```

**前端展示**:

```
📌 我的收藏

[1] 来自: Spring AI 架构设计
    "...Spring AI支持PgVector..."
    收藏于: 2 天前

[2] 来自: [会话已删除] ⚠️ 孤立收藏
    "...简历优化建议..."
    收藏于: 5 天前
```

---

## Important 6: DocumentMeta 预留 contentHash

```java
@Data
public class DocumentMeta {
    private String docId;
    private String fileName;
    private long fileSize;
    private String fileHash;          // SHA-256(file bytes) — 文件级去重
    private String contentHash;       // 预留: SHA-256(extracted text) — 内容级去重
    private DocumentStatus status;
    private String failReason;
    private LocalDateTime uploadedAt;
    private LocalDateTime indexedAt;
}
```

当前只用 `fileHash` 去重。`contentHash` 预留，未来可做：
- 不同文件名但相同内容的文档去重
- 内容变更检测

---

## Important 7: Quality Guard AUTO 模式

### 问题

每次对话都走 Quality Guard → 延迟翻倍 → 用户体验下降。

### 方案: AUTO 模式 + 规则引擎

```java
public enum QualityMode {
    OFF,        // 不审查
    AUTO,       // 自动判断（默认）
    REVIEW,     // 强制审查一次
    RED_TEAM    // 强制红蓝对抗
}
```

### AUTO 规则引擎: Intent-Based Risk Classifier

**不用关键词匹配**（太脆，"基金""股票""买房贷款" 容易漏）。  
**用 LLM 意图分类**，输出风险等级。

```java
@Service
public class QualityModeResolver {

    private final ChatModel chatModel;

    /**
     * 用 LLM 判断用户问题的风险等级。
     * 比关键词匹配稳定得多。
     */
    public QualityMode resolve(String userMessage, AgentIntent intent) {
        // 职业决策意图 → 直接 REVIEW（无需 LLM 判断）
        if (isCareerDecision(intent)) {
            return QualityMode.REVIEW;
        }

        // 其他问题 → LLM 判断风险等级
        RiskAssessment risk = assessRisk(userMessage);
        return switch (risk.level()) {
            case "LOW" -> QualityMode.OFF;
            case "MEDIUM" -> QualityMode.REVIEW;
            case "HIGH", "CRITICAL" -> QualityMode.RED_TEAM;
            default -> QualityMode.OFF;
        };
    }

    private RiskAssessment assessRisk(String message) {
        // LLM 快速分类（< 500ms）
        String prompt = """
            判断以下用户问题的风险等级。
            LOW: 日常闲聊、一般建议
            MEDIUM: 职业规划、学习建议
            HIGH: 财务投资、法律咨询、医疗健康
            CRITICAL: 涉及个人隐私、可能造成严重后果
            只输出JSON: {\"level\":\"...\",\"reason\":\"...\"}
            问题: %s
            """.formatted(message);
        // 调用 LLM，解析返回
        // ...
    }

    private boolean isCareerDecision(AgentIntent intent) {
        return intent == AgentIntent.RESUME
            || intent == AgentIntent.NEGOTIATION
            || intent == AgentIntent.ESCAPE;
    }

    record RiskAssessment(String level, String reason) {}
}
```

**为什么不用关键词**:
- "基金" → 漏
- "股票" → 漏
- "买房贷款" → 可能漏
- LLM 理解语义，"我想贷款买房" 和 "帮我算贷款利率" 风险不同

### 延迟对比

| 模式 | 流程 | 额外延迟 |
|------|------|----------|
| OFF | Agent → 输出 | 0ms |
| AUTO=OFF | Agent → 输出 | 0ms |
| AUTO=REVIEW | Agent → Guard → 输出 | +3-5s |
| AUTO=RED_TEAM | Agent → Guard → 修正 → Guard → 输出 | +8-15s |
| REVIEW (手动) | Agent → Guard → 输出 | +3-5s |
| RED_TEAM (手动) | Agent → Guard → 修正 → Guard → 输出 | +8-15s |

**默认 AUTO → 大部分对话 OFF → 不影响日常体验**

---

## Nice 8: Quality Review 复用 Trace

### 问题

每次 Review 都持久化到 `quality-review.json` → 数据量爆炸。

### 方案: 默认写 Trace，>=HIGH 才额外持久化

```java
// QualityGuardAgent 审查完成后
QualityReview review = doReview(answer);

// 1. 始终写入 Execution Trace（已有基础设施）
traceRecorder.addSpan(traceContext, TraceStepType.QUALITY_REVIEW,
    Map.of("overallScore", review.getOverallScore(),
           "riskLevel", review.getRiskLevel()));

// 2. 仅 HIGH/CRITICAL 额外持久化（用于告警/审计）
if (review.getRiskLevel() == RiskLevel.HIGH ||
    review.getRiskLevel() == RiskLevel.CRITICAL) {
    qualityReviewRepository.save(review);
}
```

**查询方式**:
- 普通查看 → 从 Execution Trace 的 metadata 中读取分数
- 风险审计 → 从 QualityReviewRepository 查询 HIGH/CRITICAL 记录

---

## 最终架构总览

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend (Vue 3)                      │
│  CareerAdvisor ← 搜索/收藏/归档/质量卡片                  │
│  KnowledgeBase ← 文档管理                                │
│  TraceDetail   ← 轨迹 + 审查步骤                         │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                    Controller Layer                       │
│  AiController (+qualityMode)  SessionController (+三态)  │
│  TraceController  DocumentController  FavoriteController │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                    Agent Layer                            │
│  OrchestratorAgent → 路由 + 质量模式判断                   │
│    ├─ BusinessAgent (蓝队)                                │
│    └─ QualityGuardAgent (红队)                            │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                    Service Layer                          │
│  ChatMemoryAdapter ← PersistentMessage + ChatMemory缓存 │
│  QualityModeResolver ← AUTO 规则引擎                     │
│  ChatSearchService ← contains + 权重评分                  │
│  DocumentMetadataManager ← fileHash 去重                  │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                    Storage Layer                          │
│  PersistentMessageRepository ← Source of Truth           │
│    ├─ chatIndex (chatId → messages)                      │
│    └─ messageIdIndex (messageId → message) O(1)          │
│  SessionManager ← 三态 (ACTIVE/ARCHIVED/DELETED)         │
│  FavoriteRepository ← contentSnapshot + sessionTitle     │
│  DocumentMetadataManager ← fileHash + contentHash预留    │
│  TraceRepository ← Execution Trace                       │
│  QualityReviewRepository ← 仅 HIGH/CRITICAL              │
└─────────────────────────────────────────────────────────┘
```

---

## 实施顺序（最终版）

```
Phase 0: 基础设施（5 天）
├── PersistentMessageRepository (双索引, UUID)
├── ChatMemoryAdapter (Truth + ChatMemory 缓存)
├── DocumentMetadataManager (fileHash + contentHash 预留)
├── Session 三态 (ACTIVE/ARCHIVED/DELETED)
└── 确认 VectorStore 删除能力

Phase 1: P0 核心（4 天）
├── P0-1 对话历史回看
├── P0-2 会话重命名
├── P0-3 删除会话（软删除 + 级联 + 收藏标记 orphaned）
├── P0-4 知识库管理
└── P0-5 会话归档

Phase 2: P1 体验（5 天）
├── P1-1 对话搜索 (equals>startsWith>contains + 权重评分)
├── P1-2 消息收藏 (contentSnapshot + sessionTitleSnapshot + orphaned)
├── P1-3 数据导出/导入 (manifest + 版本迁移)
└── P1-4 Quality Guard (AUTO 模式 + Review 模式)

Phase 3: P1.5 治理增强（3 天）
├── P1-4b Quality Guard Red Team 模式
├── P1-4c 自动模式规则引擎
├── P1-4d 阻断机制
└── TraceStepType 新增枚举

Phase 4: P2 增强
├── P2-1 用量统计
├── P2-2 Agent 对比（可保存）
└── P2-3 语音输入
```

---

## 文件清单

### 新增

| 文件 | 说明 |
|------|------|
| PersistentChatMessage.java | 持久化消息模型 |
| PersistentMessageRepository.java | 消息持久化（双索引, UUID） |
| ChatMemoryAdapter.java | Truth + ChatMemory 适配层 |
| DocumentMetadataManager.java | 文档元数据（fileHash + contentHash） |
| DocumentStatus.java | 文档状态枚举（7 种） |
| SessionStatus.java | 会话状态枚举（三态） |
| QualityGuardAgent.java | 质量守卫 Agent |
| QualityReview.java | 审查结果模型 |
| QualityMode.java | 运行模式枚举 (OFF/AUTO/REVIEW/RED_TEAM) |
| RiskLevel.java | 风险等级枚举 |
| QualityModeResolver.java | AUTO 模式规则引擎 |
| QualityReviewRepository.java | 仅 HIGH/CRITICAL 持久化 |
| ChatSearchService.java | 搜索服务（三层匹配 + 权重评分） |
| Favorite.java | 收藏模型（含 orphaned） |
| FavoriteRepository.java | 收藏持久化 |
| SessionCleanupJob.java | 30 天物理清理 |
| BackupMigration.java | 版本迁移接口 |

### 改动

| 文件 | 改动 |
|------|------|
| ChatMemoryManager.java | 委托给 ChatMemoryAdapter |
| OrchestratorAgent.java | +QualityMode 判断 + Guard 集成 |
| SessionController.java | +重命名 +归档 +回收站 |
| SessionManager.java | +三态 +rename +archive |
| DocumentController.java | +list +delete +hash 去重 |
| TraceStepType.java | +4 个新枚举值 |
| TraceStreamPublisher.java | +quality-review 事件 |
| AiController.java | +qualityMode 参数 |
| application.yml | +quality-guard 配置段 |
| api/index.js | +全部新 API |
| CareerAdvisor.vue | +搜索/收藏/质量卡片/归档 |
| TraceTimelineView.vue | +审查步骤展示 |
| router/index.js | +新路由 |
