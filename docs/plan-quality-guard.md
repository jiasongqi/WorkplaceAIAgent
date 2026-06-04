# Agent Quality Guard — 实施计划

> Agent 治理层：红蓝对抗 + 质量守卫  
> 优先级：P1（高于语音/对比/统计）

---

## 一、核心理念

```
其他 Agent 平台在卷：更多模型、更多工具、更多工作流
用户真正关心的：结果可信度

Quality Guard 解决的核心问题：
  AI 回答质量不稳定 → 用户不敢长期依赖
```

---

## 二、架构设计

### 整体架构

```
用户消息
  ↓
OrchestratorAgent
  ├─ 路由到业务 Agent
  │    ├─ CareerAgent
  │    ├─ ResumeAgent
  │    ├─ NegotiationAgent
  │    ├─ EscapeAgent
  │    └─ GeneralCareerAgent
  │
  ├─ 业务 Agent 生成答案（蓝队）
  │
  ├─ [Mode 2/3] QualityGuardAgent 审查（红队）
  │    ├─ 评分
  │    ├─ 挑刺
  │    └─ 风险标注
  │
  ├─ [Mode 3] 业务 Agent 修正 → 再次审查
  │
  └─ 最终输出
       ├─ 回答内容
       ├─ QualityReview（评分 + 风险提示）
       └─ 执行轨迹（含审查步骤）
```

### 三种运行模式

| 模式 | 流程 | 延迟 | 场景 |
|------|------|------|------|
| NORMAL | Agent → 输出 | 最快 | 日常闲聊、简单问题 |
| REVIEW | Agent → Guard → 输出 | +3-5s | 重要决策、专业建议 |
| RED_TEAM | Agent → Guard → Agent修正 → Guard → 输出 | +8-15s | 高风险场景、合规要求 |

### 模式选择策略

**自动判断**（不依赖用户手动选）:

```java
public enum QualityMode {
    NORMAL,     // 简单问题，无需审查
    REVIEW,     // 中等风险，审查一次
    RED_TEAM    // 高风险，红蓝对抗
}
```

OrchestratorAgent 路由时同时判断模式：

```java
// 意图识别阶段，同时判断质量模式
QualityMode mode = assessQualityMode(userMessage, intent);

private QualityMode assessQualityMode(String message, AgentIntent intent) {
    // RED_TEAM: 涉及法律/财务/隐私/合规
    if (containsHighRiskKeywords(message)) return QualityMode.RED_TEAM;
    // REVIEW: 职业决策类（跳槽/薪资/离职）
    if (intent == AgentIntent.RESUME || 
        intent == AgentIntent.NEGOTIATION || 
        intent == AgentIntent.ESCAPE) return QualityMode.REVIEW;
    // NORMAL: 日常闲聊
    return QualityMode.NORMAL;
}
```

**也支持用户手动覆盖**: SSE 参数 `?qualityMode=RED_TEAM`

---

## 三、QualityGuardAgent 设计

### 核心 Prompt

```java
public class QualityGuardAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
        你是一个专业的 AI 回答质量审核员。你的职责是审查其他 AI Agent 的输出质量。
        
        审查维度：
        1. 准确性 (accuracy) — 事实是否正确，数据是否准确
        2. 完整性 (completeness) — 是否遗漏关键信息
        3. 逻辑性 (logic) — 推理是否合理，结论是否成立
        4. 幻觉风险 (hallucination) — 是否包含无依据的编造
        5. 风险等级 (risk) — 是否涉及敏感领域（法律/财务/隐私/合规）
        
        输出格式（严格 JSON）：
        {
          "accuracyScore": 0-100,
          "completenessScore": 0-100,
          "logicScore": 0-100,
          "hallucinationScore": 0-100,  // 越高越安全
          "riskScore": 0-100,           // 越高风险越大
          "overallScore": 0-100,
          "issues": ["问题1", "问题2"],
          "suggestions": ["建议1", "建议2"],
          "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
          "summary": "一句话总结"
        }
        
        评分标准：
        - 90+ 优秀：回答专业、准确、完整
        - 70-89 良好：基本准确，有小瑕疵
        - 50-69 一般：存在明显问题
        - <50 差：严重错误或高风险
        
        风险等级：
        - LOW: 日常建议，无风险
        - MEDIUM: 职业建议，需要用户自行判断
        - HIGH: 涉及财务/法律，建议咨询专业人士
        - CRITICAL: 可能造成严重后果，建议阻断
        """;
}
```

### 红队模式 Prompt 补充

```java
private static final String RED_TEAM_PROMPT = """
    你现在扮演红队角色。你的目标是尽可能找出回答中的问题。
    
    攻击策略：
    1. 事实核查：每个数据是否有依据
    2. 逻辑漏洞：推理链条是否有断裂
    3. 隐含假设：是否做了未声明的假设
    4. 边界情况：极端情况下是否成立
    5. 合规风险：是否违反法规或伦理
    6. 隐私风险：是否泄露或解析个人信息
    
    你需要像一个严格的审查员一样，尽可能挑刺。
    宁可误报，不可漏报。
    """;
```

---

## 四、数据模型

### QualityReview（质量审查结果）

```java
@Data
public class QualityReview {
    private String reviewId;              // UUID
    private String chatId;
    private String userMessageId;         // 用户消息 ID
    private String agentMessageId;        // 被审查的 Agent 回答 ID
    private QualityMode mode;             // NORMAL/REVIEW/RED_TEAM
    
    // 评分
    private Integer accuracyScore;        // 准确性 0-100
    private Integer completenessScore;    // 完整性 0-100
    private Integer logicScore;           // 逻辑性 0-100
    private Integer hallucinationScore;   // 幻觉安全度 0-100（越高越安全）
    private Integer riskScore;            // 风险度 0-100（越高风险越大）
    private Integer overallScore;         // 综合分 0-100
    
    // 详情
    private String riskLevel;             // LOW/MEDIUM/HIGH/CRITICAL
    private List<String> issues;          // 发现的问题
    private List<String> suggestions;     // 改进建议
    private String summary;               // 一句话总结
    
    // 红队模式专用
    private String revisedAnswer;         // 修正后的回答（仅 RED_TEAM）
    private Integer roundCount;           // 对抗轮数（仅 RED_TEAM）
    
    private LocalDateTime createdAt;
}
```

### QualityMode 枚举

```java
public enum QualityMode {
    NORMAL("普通模式", "直接回答，无审查"),
    REVIEW("审查模式", "回答后审查一次"),
    RED_TEAM("红蓝对抗", "回答→审查→修正→再审查");
    
    private final String displayName;
    private final String description;
}
```

### RiskLevel 枚举

```java
public enum RiskLevel {
    LOW("低风险", "日常建议"),
    MEDIUM("中风险", "需要用户自行判断"),
    HIGH("高风险", "建议咨询专业人士"),
    CRITICAL("极高风险", "建议阻断回答");
    
    private final String displayName;
    private final String description;
}
```

---

## 五、流程详解

### Mode 1: NORMAL（普通模式）

```
用户: "今天天气怎么样"
  ↓
OrchestratorAgent → 意图识别 → NORMAL
  ↓
GeneralCareerAgent → 回答
  ↓
直接返回（无审查）
  ↓
输出: { answer: "...", qualityReview: null }
```

### Mode 2: REVIEW（审查模式）

```
用户: "帮我优化简历"
  ↓
OrchestratorAgent → 意图识别 → REVIEW
  ↓
ResumeAgent → 生成简历优化建议（蓝队）
  ↓
QualityGuardAgent → 审查（红队）
  ↓
输出: { answer: "...", qualityReview: { overallScore: 85, ... } }
```

**前端展示**:

```
┌─────────────────────────────────────┐
│ 📄 简历优化建议                       │
│                                     │
│ [Agent 回答内容...]                   │
│                                     │
├─────────────────────────────────────┤
│ ⚖️ 质量审查: 85 分                   │
│                                     │
│ 准确性 ████████░░ 85                │
│ 完整性 ███████░░░ 70                │
│ 逻辑性 █████████░ 90                │
│ 幻觉度 █████████░ 95                │
│                                     │
│ ⚠️ 建议:                             │
│ · 建议补充具体项目数据                 │
│ · 技能列表可更具体                     │
└─────────────────────────────────────┘
```

### Mode 3: RED_TEAM（红蓝对抗）

```
用户: "我应该现在跳槽吗"
  ↓
OrchestratorAgent → 意图识别 → RED_TEAM
  ↓
Round 1:
  CareerAgent → 生成建议（蓝队）
  QualityGuardAgent(RedTeam) → 审查（红队）
  ↓
  如果 riskLevel == CRITICAL → 阻断，返回风险提示
  如果 overallScore < 70 → 进入 Round 2
  如果 overallScore >= 70 → 直接返回
  ↓
Round 2:
  CareerAgent + RedTeam反馈 → 修正建议（蓝队整改）
  QualityGuardAgent(RedTeam) → 再次审查（红队验收）
  ↓
输出: {
  answer: "修正后的建议...",
  qualityReview: { overallScore: 88, roundCount: 2, ... },
  originalAnswer: "原始建议...",
  redTeamFeedback: ["问题1", "问题2"]
}
```

**前端展示**:

```
┌─────────────────────────────────────┐
│ 💼 职业规划建议                       │
│                                     │
│ [最终修正版建议...]                    │
│                                     │
├─────────────────────────────────────┤
│ 🔴🔵 红蓝对抗 (2 轮)                 │
│                                     │
│ 最终评分: 88 分                       │
│                                     │
│ 第 1 轮: 65 分 → 红队反馈 3 个问题     │
│ 第 2 轮: 88 分 → 红队验收通过          │
│                                     │
│ 📋 红队发现:                          │
│ · 未考虑行业周期影响                   │
│ · 薪资数据来源不明确                   │
│ · 缺少风险对冲建议                     │
│                                     │
│ [展开查看原始回答]                     │
└─────────────────────────────────────┘
```

---

## 六、与执行轨迹集成

Quality Guard 的每一步都通过 TraceRecorder 记录：

```java
// 新增 TraceStepType
QUALITY_REVIEW("质量审查"),
RED_TEAM_REVIEW("红队审查"),
RED_TEAM_REVISION("蓝队整改");
```

**轨迹新增步骤**:

```
Normal 模式:
  INTENT_DETECTION → ROUTING → SUB_AGENT_EXECUTION → 结束

Review 模式:
  INTENT_DETECTION → ROUTING → SUB_AGENT_EXECUTION 
    → QUALITY_REVIEW → 结束

Red Team 模式:
  INTENT_DETECTION → ROUTING → SUB_AGENT_EXECUTION 
    → RED_TEAM_REVIEW 
    → RED_TEAM_REVISION 
    → RED_TEAM_REVIEW → 结束
```

前端 TraceTimelineView 自动展示审查步骤。

---

## 七、阻断机制

当 riskLevel == CRITICAL 时：

```java
if (review.getRiskLevel() == RiskLevel.CRITICAL) {
    // 不返回原始回答
    // 返回风险提示
    return "⚠️ 该问题涉及高风险领域，AI 建议可能造成严重后果。\n\n" +
           "风险原因：" + review.getSummary() + "\n\n" +
           "建议：请咨询相关领域的专业人士。";
}
```

**前端特殊展示**:

```
┌─────────────────────────────────────┐
│ 🚫 回答已被质量守卫阻断              │
│                                     │
│ 风险等级: CRITICAL                   │
│ 原因: 涉及个人财务规划，可能存在      │
│       误导性投资建议                  │
│                                     │
│ 建议: 请咨询持证理财顾问              │
│                                     │
│ [查看原始回答（仅管理员）]             │
└─────────────────────────────────────┘
```

---

## 八、SSE 事件扩展

```java
// 新增 SSE 事件类型
eventSource.addEventListener('quality-review', (e) => {
    const data = JSON.parse(e.data)
    // data: {
    //   mode: "REVIEW" | "RED_TEAM",
    //   overallScore: 85,
    //   riskLevel: "MEDIUM",
    //   issues: [...],
    //   round: 1,
    //   totalRounds: 2
    // }
})

eventSource.addEventListener('red-team-round', (e) => {
    const data = JSON.parse(e.data)
    // data: { round: 2, status: "reviewing" | "revising" | "done" }
})
```

---

## 九、配置

```yaml
# application.yml
quality-guard:
  enabled: ${QUALITY_GUARD_ENABLED:true}
  default-mode: ${QUALITY_GUARD_MODE:auto}   # auto | normal | review | red_team
  auto-mode:
    # 自动模式判断的关键词
    high-risk-keywords: "投资,理财,贷款,法律,诉讼,隐私,身份证,密码"
    review-intents: "RESUME,NEGOTIATION,ESCAPE"
  red-team:
    max-rounds: ${RED_TEAM_MAX_ROUNDS:3}     # 最大对抗轮数
    pass-threshold: ${RED_TEAM_PASS_THRESHOLD:70}  # 通过阈值
  blocking:
    enabled: ${QUALITY_BLOCKING_ENABLED:true}
    risk-threshold: "CRITICAL"               # 阻断的风险等级
```

---

## 十、文件变更清单

### 新增文件

| 文件 | 说明 |
|------|------|
| QualityGuardAgent.java | 质量守卫 Agent（审查 + 红队） |
| QualityReview.java | 审查结果模型 |
| QualityMode.java | 运行模式枚举 |
| RiskLevel.java | 风险等级枚举 |
| QualityReviewRepository.java | 审查结果持久化 |
| QualityGuardConfig.java | 配置类 |
| TraceStepType.java | 新增 3 个步骤类型 |

### 改动文件

| 文件 | 改动 |
|------|------|
| OrchestratorAgent.java | 路由后增加质量审查流程 |
| AgentConfig.java | 注册 QualityGuardAgent Bean |
| AiController.java | SSE 事件增加 quality-review |
| TraceStreamPublisher.java | 推送审查步骤事件 |
| application.yml | 新增 quality-guard 配置段 |
| CareerAdvisor.vue | 展示质量评分卡片 |
| TraceTimelineView.vue | 展示审查步骤 |
| api/index.js | 新增 getQualityReview |

---

## 十一、实施顺序

```
Phase 1: 基础框架（3 天）
├── QualityGuardAgent + Prompt 设计
├── QualityReview 数据模型
├── QualityReviewRepository 持久化
└── TraceStepType 新增枚举值

Phase 2: Review 模式（2 天）
├── OrchestratorAgent 集成 Review 流程
├── SSE quality-review 事件推送
├── 前端质量评分卡片
└── 执行轨迹集成

Phase 3: Red Team 模式（2 天）
├── 红队 Prompt 设计
├── 多轮对抗循环
├── 修正回答生成
├── 前端红蓝对抗展示
└── 阻断机制

Phase 4: 自动模式判断（1 天）
├── 关键词风险检测
├── 意图→模式映射
├── 用户手动覆盖
└── 配置化
```

---

## 十二、与现有功能的集成点

| 现有功能 | 集成方式 |
|----------|----------|
| OrchestratorAgent | 路由后插入审查流程 |
| TraceRecorder | 记录审查步骤 |
| TraceStreamPublisher | 推送审查 SSE 事件 |
| TraceTimelineView | 展示审查步骤 |
| CareerAdvisor.vue | 展示质量评分卡片 |
| SSE 协议 | 新增 quality-review 事件 |
| application.yml | 新增 quality-guard 配置 |

---

## 十三、未来演进（Agent Governance Layer）

```
Phase 1（当前）: QualityGuardAgent
  └─ 质量审查 + 红蓝对抗

Phase 2: ComplianceAgent
  └─ 合规检查（法规/行业规范/公司政策）

Phase 3: SecurityAgent
  └─ 安全检查（隐私泄露/注入攻击/敏感信息）

Phase 4: CostGuardAgent
  └─ 成本控制（Token 消耗/调用频率/预算告警）

Phase 5: Agent Governance Layer 抽象
  ├─ QualityGuardAgent
  ├─ ComplianceAgent
  ├─ SecurityAgent
  ├─ CostGuardAgent
  └─ GovernancePipeline（编排多个守卫）
```

---

## 十四、优先级整合（全部功能）

```
Phase 0: 基础设施
├── Infra-1 PersistentChatMessage + MessageId
├── Infra-2 DocumentMetadataManager
└── 确认 VectorStore 实现

Phase 1: P0 核心
├── P0-1 对话历史回看
├── P0-2 会话重命名
├── P0-3 删除会话
└── P0-4 知识库管理

Phase 2: P1 体验 + 治理
├── P1-1 对话搜索（contains + 权重评分）
├── P1-2 消息收藏（含 contentSnapshot）
├── P1-3 会话归档（三态）
├── P1-4 数据导出/导入
└── P1-5 Quality Guard（Review 模式）← 新增

Phase 3: P1.5 治理增强
├── P1-5b Quality Guard（Red Team 模式）
├── P1-5c 自动模式判断
└── P1-5d 阻断机制

Phase 4: P2 增强
├── P2-1 用量统计
├── P2-2 Agent 对比
└── P2-3 语音输入
```
