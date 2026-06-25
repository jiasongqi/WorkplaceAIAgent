# Multi-Agent Runtime 架构设计（修订版 V2）

> WorkPilot Agent Runtime — 从群聊 Demo 到企业级多 Agent 工作流

---

## 设计决策记录

| 编号 | 决策 | 结论 |
|------|------|------|
| Q1 | Message Model | sourceType(枚举) + sourceId(字符串) + sourceName(字符串) |
| Q2 | AgentOutput 消费 | FormatterRegistry 模式，不用 instanceof |
| Q3 | ConversationContext | Immutable + contextWith() 链式追加 |
| Q4 | WorkflowTemplate 定义 | V2 硬编码，V3 配置化 |
| Q5 | WorkflowMatcher 匹配 | 规则优先 → LLM 兜底 → GENERIC fallback |
| Q6 | Agent 失败策略 | FailurePolicy 枚举，每 Workflow 可配 |
| Q7 | Token 预算 | V2 P0，必须做 |
| Q8 | Token 计数 | JTokkit 预算检查 + API Usage 实际统计 |
| Q9 | conversationSummary | 复用 ChatMemoryManager 压缩能力 |
| Q10 | ArtifactShelf 关联 | 独立管理，AgentOutput 用 artifactIds 弱引用 |

---

## 一、核心模型

### 1. MessageSource（替代 agentType/agentName/agentAvatar）

```java
public enum MessageSource {
    USER,
    AGENT,
    SYSTEM,
    TOOL,
    SYNTHESIZER
}
```

```java
// PersistentChatMessage 新增字段
public class PersistentChatMessage {
    // ... 现有字段不变 ...

    /** 消息来源类型 */
    private MessageSource sourceType;

    /** 来源标识（动态）：AGENT→"RESUME", TOOL→"linkedin_search" */
    private String sourceId;

    /** 来源展示名（前端用）：AGENT→"简历专家", TOOL→"LinkedIn 搜索" */
    private String sourceName;
}
```

示例：
```json
[
  {"role":"user", "sourceType":"USER", "sourceId":"user_001", "sourceName":"用户",
   "content":"我要跳槽，简历和薪资怎么准备"},

  {"role":"assistant", "sourceType":"AGENT", "sourceId":"RESUME", "sourceName":"简历专家",
   "content":"你的简历需要突出项目成果..."},

  {"role":"assistant", "sourceType":"AGENT", "sourceId":"NEGOTIATION", "sourceName":"薪资专家",
   "content":"以你的经验建议谈到25k..."},

  {"role":"assistant", "sourceType":"SYNTHESIZER", "sourceId":"aggregator", "sourceName":"综合顾问",
   "content":"跳槽准备建议：1.简历优化...2.薪资谈判..."}
]
```

### 2. AgentOutput（类型化接口 + FormatterRegistry）

```java
// === 接口 ===
public interface AgentOutput {
    /** 给 ResultAggregator 的文本摘要 */
    String summary();

    /** 关联的交付物 ID（弱引用 ArtifactShelf） */
    List<String> artifactIds();
}

// === 类型化实现 ===
public record ResumeAnalysisOutput(
    List<String> strengths,
    List<String> weaknesses,
    List<String> suggestions,
    List<String> artifactIds
) implements AgentOutput {
    @Override
    public String summary() {
        return "简历分析：优势" + strengths + "，劣势" + weaknesses + "，建议" + suggestions;
    }
    @Override
    public List<String> artifactIds() { return artifactIds; }
}

public record SalaryAnalysisOutput(
    Integer currentSalary,
    Integer marketRange,
    Integer suggestedTarget,
    List<String> negotiationTips,
    List<String> artifactIds
) implements AgentOutput {
    @Override
    public String summary() {
        return "薪资分析：当前" + currentSalary + "，市场" + marketRange
             + "，建议目标" + suggestedTarget + "，谈判要点" + negotiationTips;
    }
    @Override
    public List<String> artifactIds() { return artifactIds; }
}

public record InterviewAnalysisOutput(
    List<String> preparationSteps,
    List<String> commonQuestions,
    List<String> artifactIds
) implements AgentOutput {
    @Override
    public String summary() {
        return "面试准备：步骤" + preparationSteps + "，常见问题" + commonQuestions;
    }
    @Override
    public List<String> artifactIds() { return artifactIds; }
}

// === 通用文本输出（兜底）===
public record TextOutput(
    String text,
    List<String> artifactIds
) implements AgentOutput {
    @Override
    public String summary() { return text; }
    @Override
    public List<String> artifactIds() { return artifactIds; }
}
```

```java
// === FormatterRegistry ===
@Component
public class FormatterRegistry {

    private final Map<Class<? extends AgentOutput>, AgentOutputFormatter<?>> formatters;

    public FormatterRegistry() {
        this.formatters = Map.of(
            ResumeAnalysisOutput.class, new ResumeOutputFormatter(),
            SalaryAnalysisOutput.class, new SalaryOutputFormatter(),
            InterviewAnalysisOutput.class, new InterviewOutputFormatter(),
            TextOutput.class, new TextOutputFormatter()
        );
    }

    @SuppressWarnings("unchecked")
    public <T extends AgentOutput> String format(T output) {
        AgentOutputFormatter<T> formatter =
            (AgentOutputFormatter<T>) formatters.get(output.getClass());
        if (formatter != null) {
            return formatter.format(output);
        }
        // fallback: 用 summary()
        return output.summary();
    }
}

public interface AgentOutputFormatter<T extends AgentOutput> {
    String format(T output);
}

// 示例 Formatter
public class ResumeOutputFormatter implements AgentOutputFormatter<ResumeAnalysisOutput> {
    @Override
    public String format(ResumeAnalysisOutput output) {
        return """
            【简历分析结果】
            优势：%s
            劣势：%s
            建议：%s
            """.formatted(
                String.join("、", output.strengths()),
                String.join("、", output.weaknesses()),
                String.join("；", output.suggestions())
            );
    }
}
```

### 3. ExecutionResult（统一执行结果包装）

```java
public record ExecutionResult(
    String taskId,
    MessageSource agentType,
    String agentId,
    TaskStatus status,
    AgentOutput output,
    TokenUsage tokenUsage,
    long durationMs,
    int retryCount,                 // 重试次数（排查慢请求用）
    Throwable error
) {
    public boolean isSuccess() { return status == TaskStatus.SUCCESS; }
    public boolean isFailed()  { return status == TaskStatus.FAILED; }
    public boolean isSkipped() { return status == TaskStatus.SKIPPED
                                  || status == TaskStatus.SKIPPED_BY_BUDGET
                                  || status == TaskStatus.SKIPPED_BY_POLICY; }

    public static ExecutionResult success(String taskId, String agentId,
            AgentOutput output, TokenUsage usage, long duration, int retryCount) {
        return new ExecutionResult(taskId, MessageSource.AGENT, agentId,
            TaskStatus.SUCCESS, output, usage, duration, retryCount, null);
    }

    public static ExecutionResult failed(String taskId, String agentId,
            Throwable error, long duration) {
        return new ExecutionResult(taskId, MessageSource.AGENT, agentId,
            TaskStatus.FAILED, null, null, duration, 0, error);
    }

    public static ExecutionResult skipped(String taskId, String agentId, TaskStatus reason) {
        return new ExecutionResult(taskId, MessageSource.AGENT, agentId,
            reason, null, null, 0, 0, null);
    }
}
```

```java
public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    RETRYING,
    SKIPPED,
    SKIPPED_BY_BUDGET,
    SKIPPED_BY_POLICY
}
```

### 4. TokenBudget + TokenUsage

```java
// === 预算定义（每个 Workflow 配置）===
public record TokenBudget(
    long maxPromptTokens,
    long maxCompletionTokens,
    long maxTotalTokens
) {
    public boolean canExecute(long estimatedTokens) {
        return estimatedTokens <= remaining();
    }
    // remaining() 由 TokenUsageTracker 计算
}

// === 实际使用记录 ===
public record TokenUsage(
    long estimatedPromptTokens,   // JTokkit 预估
    long actualPromptTokens,      // API response
    long actualCompletionTokens,  // API response
    long totalTokens()
) {
    public long totalTokens() {
        return actualPromptTokens + actualCompletionTokens;
    }

    /** 预估误差率（用于后续优化预估模型） */
    public double estimationError() {
        if (estimatedPromptTokens == 0) return 0;
        return Math.abs(actualPromptTokens - estimatedPromptTokens)
             / (double) estimatedPromptTokens;
    }
}
```

```java
@Component
public class TokenUsageTracker {

    /** 工作流级别的累计用量 */
    private final Map<String, TokenUsage> workflowUsage = new ConcurrentHashMap<>();

    /** 执行前：JTokkit 预估 prompt tokens */
    public long estimatePromptTokens(String prompt) {
        // JTokkit: Encodings.newEncoder(Cl100kBase.INSTANCE).countTokens(prompt)
        return JtokkitEncoder.encode(prompt).size();
    }

    /** 执行后：记录 API 返回的实际用量 */
    public void recordUsage(String workflowId, TokenUsage usage) {
        workflowUsage.merge(workflowId, usage, (a, b) -> new TokenUsage(
            a.estimatedPromptTokens() + b.estimatedPromptTokens(),
            a.actualPromptTokens() + b.actualPromptTokens(),
            a.actualCompletionTokens() + b.actualCompletionTokens(),
            0
        ));
    }

    /** 检查预算 */
    public boolean canExecute(String workflowId, TokenBudget budget, long estimatedTokens) {
        TokenUsage used = workflowUsage.getOrDefault(workflowId, TokenUsage.ZERO);
        long remaining = budget.maxTotalTokens() - used.totalTokens();
        return estimatedTokens <= remaining;
    }
}
```

### 5. ConversationContext（纯 Immutable）+ RuntimeContext（可变执行状态）

```java
/**
 * 对话上下文 — 只包含"关于用户和对话"的静态信息。
 * 所有 Agent 共享同一个实例，不可变。
 * 不包含任何执行过程中的状态。
 */
public record ConversationContext(
    String userProfile,
    String conversationSummary,
    List<Message> recentMessages
) {
    // 纯数据，无方法，无 accumulatedOutputs
}
```

```java
/**
 * 运行时上下文 — 可变，追踪工作流执行过程中的状态。
 * 与 ConversationContext 分离，避免"上下文被污染"。
 *
 * 职责：
 * - 累积各 Agent 的执行结果
 * - 维护步骤间传递的变量
 * - 不传给 Agent（Agent 只看 ConversationContext）
 */
public class RuntimeContext {

    /** 各步骤的执行结果（按执行顺序） */
    private final List<ExecutionResult> results = new ArrayList<>();

    /** 步骤间传递的变量（供 DAG V3 使用） */
    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    /** 当前正在执行的步骤索引 */
    private int currentStepIndex = 0;

    public void addResult(ExecutionResult result) {
        results.add(result);
    }

    public List<ExecutionResult> getResults() {
        return List.copyOf(results);
    }

    public List<ExecutionResult> getSuccessfulResults() {
        return results.stream()
            .filter(ExecutionResult::isSuccess)
            .toList();
    }

    public boolean hasFailures() {
        return results.stream().anyMatch(ExecutionResult::isFailed);
    }

    /** 前一个 Agent 的输出（供下一个 Agent 参考，通过 AgentRunner 传入） */
    public String previousAgentSummary() {
        return results.stream()
            .filter(ExecutionResult::isSuccess)
            .reduce((a, b) -> b)  // 取最后一个成功的
            .map(r -> r.output().summary())
            .orElse("");
    }

    // === 变量存取（V3 DAG 用）===
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) variables.get(key);
    }
}
```

```java
// === AgentRunner 调用签名 ===
// Agent 只接收 ConversationContext（静态上下文），不接触 RuntimeContext
public interface AgentRunner {
    AgentOutput run(ConversationContext context, String userMessage);
    TokenUsage getLastTokenUsage();
}

// === TaskExecutor 调用方式 ===
// runner.run(conversationContext, userMessage)  ← Agent 看到的
// runtimeContext.addResult(result)              ← Executor 追踪的
// 两者完全分离
```

```java
@Component
public class ConversationContextBuilder {

    private final ChatMemoryManager chatMemoryManager;
    private final UserProfileService userProfileService;
    private final PersistentMessageRepository messageRepository;

    /**
     * 构建基础上下文（只调用一次，所有 Agent 共享）。
     * conversationSummary 复用 ChatMemoryManager 的压缩能力。
     */
    public ConversationContext build(String chatId, String userId) {
        // 1. 用户画像
        String profile = StringUtils.hasText(userId)
            ? userProfileService.buildPromptInjection(userId) : "";

        // 2. 对话摘要（复用已有的 MemoryCompressor）
        String summary = chatMemoryManager.getCompressedSummary(chatId);

        // 3. 最近消息（最多 20 条，避免 Token 爆炸）
        List<Message> recent = messageRepository.findByChatId(chatId).stream()
            .skip(Math.max(0, messageRepository.countByChatId(chatId) - 20))
            .map(this::toLlmMessage)
            .toList();

        return new ConversationContext(profile, summary, recent, List.of());
    }
}
```

### 5.5 PromptContext（避免 N+1 Token 预估）

```java
/**
 * 预构建的共享 Prompt 上下文。
 * 在工作流开始时构建一次，所有 Agent 共享基础部分，避免重复拼接和 Token 计算。
 */
public record PromptContext(
    String sharedSystemPrompt,    // userProfile + conversationSummary + recentMessages 拼好的
    long estimatedBaseTokens      // JTokkit 预算好的基础 Token 数
) {
    /**
     * Agent 追加自己的 systemPrompt，返回完整 prompt。
     * 只需计算增量 Token，不需要重新算基础部分。
     */
    public String buildFullPrompt(String agentSystemPrompt) {
        return sharedSystemPrompt + "\n\n" + agentSystemPrompt;
    }

    /**
     * 预估完整 prompt 的 Token 数 = 基础 + 增量。
     */
    public long estimateTotalTokens(String agentSystemPrompt) {
        return estimatedBaseTokens + JtokkitEncoder.countTokens(agentSystemPrompt);
    }
}
```

```java
@Component
public class PromptContextBuilder {

    private final ConversationContextBuilder contextBuilder;

    /**
     * 在工作流开始时构建一次，后续所有 Agent 共享。
     * 避免 N 次重复拼接 + N 次重复 Token 计算。
     */
    public PromptContext build(ConversationContext context) {
        String shared = String.join("\n\n",
            "【用户画像】" + context.userProfile(),
            "【对话摘要】" + context.conversationSummary(),
            "【近期对话】" + formatRecentMessages(context.recentMessages())
        );

        long baseTokens = JtokkitEncoder.countTokens(shared);
        return new PromptContext(shared, baseTokens);
    }
}
```

```java
// === TaskExecutor 使用方式 ===
// 旧：每个 Agent 都重新拼 prompt + 重新算 token（N+1 问题）
// estimated = tokenTracker.estimatePromptTokens(buildPrompt(runner, context, msg));  // ×5

// 新：基础 prompt 只算一次，Agent 只算增量
PromptContext promptCtx = promptContextBuilder.build(conversationContext);

for (PlanStep step : workflow.steps()) {
    AgentRunner runner = agentRunners.get(step.agentId());
    String agentPrompt = runner.getSystemPrompt();

    // 只计算增量 Token（基础部分已缓存）
    long estimated = promptCtx.estimateTotalTokens(agentPrompt);

    if (!tokenTracker.canExecute(workflowId, budget, estimated)) {
        // SKIPPED_BY_BUDGET
    }

    // Agent 执行时也用同一个 PromptContext
    AgentOutput output = runner.run(conversationContext, userMessage, promptCtx);
}
```

### 6. FailurePolicy

```java
public enum FailurePolicy {
    /** 立即终止整个工作流（用于关键链路：预约、支付等） */
    FAIL_FAST,

    /** 重试 1 次，失败则跳过，继续执行后续 Agent */
    RETRY_THEN_SKIP,

    /** 重试 1 次，失败则整个工作流失败 */
    RETRY_THEN_FAIL,

    /** 直接跳过（用于临时下线的 Agent，如 salary-agent.enabled=false） */
    SKIP
}
```

---

## 二、核心组件

### 整体架构

```
User Message
     ↓
┌─────────────────────────────────────────────────────┐
│                WorkflowMatcher                       │
│  ┌─────────┐  ┌─────────┐  ┌─────────────────────┐ │
│  │ Rule    │→ │ LLM     │→ │ GENERIC_FALLBACK    │ │
│  │ Match   │  │ Match   │  │                     │ │
│  └─────────┘  └─────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────┘
     ↓ WorkflowMatchResult
┌─────────────────────────────────────────────────────┐
│              ConversationContextBuilder              │
│  userProfile + conversationSummary + recentMessages │
└─────────────────────────────────────────────────────┘
     ↓ ConversationContext
┌─────────────────────────────────────────────────────┐
│                TaskExecutor                          │
│                                                     │
│  for each PlanStep:                                 │
│    ┌──────────────────────────────────────────┐    │
│    │ 1. TokenBudgetCheck (JTokkit 预估)       │    │
│    │    → SKIPPED_BY_BUDGET if over           │    │
│    │ 2. FailurePolicy check                   │    │
│    │    → SKIPPED_BY_POLICY if prev failed    │    │
│    │ 3. Execute Agent                         │    │
│    │    → context.withOutput(result)          │    │
│    │ 4. Record TokenUsage (API actual)        │    │
│    │ 5. On failure → apply FailurePolicy      │    │
│    └──────────────────────────────────────────┘    │
│                                                     │
│  Output: List<ExecutionResult>                      │
└─────────────────────────────────────────────────────┘
     ↓ List<ExecutionResult>
┌─────────────────────────────────────────────────────┐
│              ResultAggregator                        │
│                                                     │
│  1. FormatterRegistry.format(each output)           │
│  2. LLM 生成最终汇总回答（流式）                      │
│  3. 标记跳过的 Agent                                │
│                                                     │
│  Output: Final Answer (Flux<String>)                │
└─────────────────────────────────────────────────────┘
     ↓
┌─────────────────────────────────────────────────────┐
│              Evaluator（P1, 可选）                   │
│  质量检查 → PASS / RETRY                            │
└─────────────────────────────────────────────────────┘
     ↓
  Final Answer → SSE → User
```

### 1. WorkflowMatcher（Score-based 匹配）

```java
@Component
public class WorkflowMatcher {

    private final WorkflowRegistry registry;
    private final ChatClient classifierClient;

    public WorkflowMatchResult match(String message) {
        // Layer 1: Score-based Rule Match（80% 命中率）
        // 不是命中即返回，而是给每个 workflow 打分，取最高分
        WorkflowMatchResult ruleResult = scoreBasedMatch(message);
        if (ruleResult != null && ruleResult.confidence() >= 0.6) {
            return ruleResult;
        }

        // Layer 2: LLM Match（15%）
        WorkflowMatchResult llmResult = llmMatch(message);
        if (llmResult != null && llmResult.confidence() >= 0.6) {
            return llmResult;
        }

        // Layer 3: GENERIC_FALLBACK（5%）
        return new WorkflowMatchResult("GENERIC_CAREER", MatchType.FALLBACK, 1.0);
    }

    /**
     * Score-based 匹配：给每个 workflow 打分，取最高分。
     * 解决"我要跳槽面试"同时命中 JOB_CHANGE 和 INTERVIEW 的歧义问题。
     *
     * 评分规则：
     * - 每命中一个关键词 +1 分
     * - 最终 confidence = 该 workflow 得分 / 最大可能得分
     */
    private WorkflowMatchResult scoreBasedMatch(String message) {
        Map<String, Integer> scores = new HashMap<>();

        for (WorkflowTemplate template : registry.getAll()) {
            if (template.keywords().isEmpty()) continue;

            int score = 0;
            for (String keyword : template.keywords()) {
                if (message.contains(keyword)) {
                    score++;
                }
            }
            if (score > 0) {
                scores.put(template.id(), score);
            }
        }

        if (scores.isEmpty()) return null;

        // 取最高分
        Map.Entry<String, Integer> best = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);

        if (best == null || best.getValue() == 0) return null;

        WorkflowTemplate template = registry.get(best.getKey());
        // confidence = 命中数 / 该 workflow 总关键词数
        double confidence = (double) best.getValue() / template.keywords().size();

        return new WorkflowMatchResult(best.getKey(), MatchType.RULE, Math.min(confidence, 1.0));
    }

    private WorkflowMatchResult llmMatch(String message) {
        String response = classifierClient.prompt()
            .user(LLM_MATCH_PROMPT.replace("{message}", message))
            .call()
            .content();
        return parseMatchResult(response);
    }
}
```

示例：
```
"我要跳槽面试"
  JOB_CHANGE: 跳槽(命中) → score=1
  INTERVIEW:  面试(命中) → score=1
  → 平局，取先定义的（或加权重字段，后续优化）

"我要跳槽，简历和薪资怎么准备"
  JOB_CHANGE: 跳槽(命中) → score=1 / keywords(5) = 0.2
  → 但 confidence 只有 0.2，可能触发 LLM 兜底
  → 实际应该放宽：只要命中就是 0.6+ 的基础分
```

```java
public record WorkflowMatchResult(
    String workflowId,
    MatchType matchType,
    double confidence
) {}

public enum MatchType {
    RULE,       // 规则命中
    LLM,        // LLM 分类
    FALLBACK    // 兜底
}
```

### 2. WorkflowRegistry + WorkflowTemplate

```java
@Component
public class WorkflowRegistry {

    private final Map<String, WorkflowTemplate> templates;

    public WorkflowRegistry() {
        this.templates = new HashMap<>();

        // 跳槽准备
        templates.put("JOB_CHANGE", new WorkflowTemplate(
            "JOB_CHANGE", "v1", "跳槽准备",
            List.of("跳槽", "换工作", "offer", "涨薪", "离职"),
            List.of(
                PlanStep.of("RESUME", "简历优化"),
                PlanStep.of("NEGOTIATION", "薪资分析"),
                PlanStep.of("GENERAL", "面试准备")
            ),
            FailurePolicy.RETRY_THEN_SKIP,
            new TokenBudget(8000, 4000, 12000),
            false
        ));

        // 面试准备
        templates.put("INTERVIEW", new WorkflowTemplate(
            "INTERVIEW", "v1", "面试准备",
            List.of("面试", "八股文", "自我介绍", "模拟面试"),
            List.of(
                PlanStep.of("RESUME", "简历优化"),
                PlanStep.of("GENERAL", "面试辅导")
            ),
            FailurePolicy.RETRY_THEN_SKIP,
            new TokenBudget(6000, 3000, 9000),
            false
        ));

        // 咨询预约
        templates.put("CONSULTATION", new WorkflowTemplate(
            "CONSULTATION", "v1", "咨询预约",
            List.of("预约", "咨询", "约时间"),
            List.of(
                PlanStep.of("CONSULTATION", "预约咨询")
            ),
            FailurePolicy.FAIL_FAST,
            new TokenBudget(4000, 2000, 6000),
            false
        ));

        // 通用职场（兜底）
        templates.put("GENERIC_CAREER", new WorkflowTemplate(
            "GENERIC_CAREER", "v1", "职场通用",
            List.of(),
            List.of(
                PlanStep.of("GENERAL", "职场顾问")
            ),
            FailurePolicy.RETRY_THEN_FAIL,
            new TokenBudget(4000, 2000, 6000),
            false
        ));
    }

    public WorkflowTemplate get(String id) { return templates.get(id); }
    public Collection<WorkflowTemplate> getAll() { return templates.values(); }
}
```

```java
public record WorkflowTemplate(
    String id,
    String version,                 // 版本号，如 "v1", "v2"
    String name,
    List<String> keywords,          // 规则匹配用
    List<PlanStep> steps,
    FailurePolicy failurePolicy,
    TokenBudget tokenBudget,
    boolean requiresPlanner         // true = 需要 Planner 细化步骤
) {
    /** 完整标识，如 "JOB_CHANGE:v2" */
    public String fullId() { return id + ":" + version; }
}

public record PlanStep(
    String agentId,                 // "RESUME", "NEGOTIATION", ...
    String taskDescription          // "简历优化" — 给 Agent 的上下文
) {
    public static PlanStep of(String agentId, String desc) {
        return new PlanStep(agentId, desc);
    }
    public static PlanStep of(String agentId) {
        return new PlanStep(agentId, "");
    }
}
```

### 3. TaskExecutor

```java
@Component
public class TaskExecutor {

    private final Map<String, AgentRunner> agentRunners;  // agentId → AgentRunner
    private final TokenUsageTracker tokenTracker;
    private final TraceRecorder traceRecorder;

    /**
     * 执行工作流的所有步骤，返回每个步骤的执行结果。
     */
    public List<ExecutionResult> execute(
            WorkflowTemplate workflow,
            ConversationContext conversationContext,
            RuntimeContext runtimeContext,          // 新增：可变执行状态
            PromptContext promptCtx,                // 新增：共享 Prompt（避免 N+1）
            String chatId,
            String userId,
            String userMessage,
            TraceContext traceCtx,
            Consumer<SseEmitter.SseEventBuilder> eventSink) {

        for (PlanStep step : workflow.steps()) {
            String taskId = UUID.randomUUID().toString().substring(0, 8);

            // 1. Token 预算检查（用 PromptContext，只算增量）
            AgentRunner runner = agentRunners.get(step.agentId());
            long estimated = promptCtx.estimateTotalTokens(runner.getSystemPrompt());

            if (!tokenTracker.canExecute(chatId, workflow.tokenBudget(), estimated)) {
                ExecutionResult skipped = ExecutionResult.skipped(
                    taskId, step.agentId(), TaskStatus.SKIPPED_BY_BUDGET);
                runtimeContext.addResult(skipped);
                emitSkipEvent(eventSink, step, "预算不足");
                continue;
            }

            // 2. 执行 Agent（只传 ConversationContext，不传 RuntimeContext）
            emitStartEvent(eventSink, step);
            long start = System.currentTimeMillis();
            int retryCount = 0;

            try {
                AgentOutput output = runner.run(conversationContext, userMessage, promptCtx);
                long duration = System.currentTimeMillis() - start;

                // 3. 记录 Token 使用（从 API response 拿 actual）
                TokenUsage usage = runner.getLastTokenUsage();
                tokenTracker.recordUsage(chatId, usage);

                ExecutionResult result = ExecutionResult.success(
                    taskId, step.agentId(), output, usage, duration, retryCount);
                runtimeContext.addResult(result);

                emitCompleteEvent(eventSink, step, result);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;

                // 4. 失败处理：应用 FailurePolicy
                ExecutionResult result = handleFailure(
                    workflow.failurePolicy(), taskId, step, e, duration,
                    retryCount, conversationContext, userMessage, promptCtx, eventSink);

                runtimeContext.addResult(result);

                if (workflow.failurePolicy() == FailurePolicy.FAIL_FAST) {
                    break;
                }
            }
        }

        return runtimeContext.getResults();
    }

    private ExecutionResult handleFailure(
            FailurePolicy policy, String taskId, PlanStep step,
            Exception error, long duration,
            ConversationContext context, String userMessage,
            Consumer<SseEmitter.SseEventBuilder> eventSink) {

        return switch (policy) {
            case FAIL_FAST -> ExecutionResult.failed(taskId, step.agentId(), error, duration);

            case RETRY_THEN_SKIP -> {
                // 重试 1 次
                try {
                    AgentRunner runner = agentRunners.get(step.agentId());
                    AgentOutput output = runner.run(context, userMessage);
                    yield ExecutionResult.success(taskId, step.agentId(),
                        output, runner.getLastTokenUsage(), duration);
                } catch (Exception retryError) {
                    yield ExecutionResult.skipped(taskId, step.agentId(),
                        TaskStatus.SKIPPED);
                }
            }

            case RETRY_THEN_FAIL -> {
                try {
                    AgentRunner runner = agentRunners.get(step.agentId());
                    AgentOutput output = runner.run(context, userMessage);
                    yield ExecutionResult.success(taskId, step.agentId(),
                        output, runner.getLastTokenUsage(), duration);
                } catch (Exception retryError) {
                    yield ExecutionResult.failed(taskId, step.agentId(),
                        retryError, duration);
                }
            }
        };
    }
}
```

### 4. ResultAggregator

```java
@Component
public class ResultAggregator {

    private final ChatClient aggregationClient;
    private final FormatterRegistry formatterRegistry;

    private static final String AGGREGATION_PROMPT = """
        你是结果汇总专家。将多个专家的分析结果合并为一份结构化回复。

        用户问题：{question}

        专家分析结果：
        {formattedResults}

        跳过的专家：
        {skippedAgents}

        要求：
        1. 按主题组织，不重复
        2. 突出各专家的核心建议
        3. 对跳过的专家简要说明原因
        4. 最后给出综合行动计划
        """;

    /**
     * 汇总所有 ExecutionResult，生成最终回答（流式）。
     */
    public Flux<String> aggregate(
            String question,
            List<ExecutionResult> results,
            String chatId) {

        // 1. 用 FormatterRegistry 格式化每个成功的结果
        String formattedResults = results.stream()
            .filter(ExecutionResult::isSuccess)
            .map(r -> formatterRegistry.format(r.output()))
            .collect(Collectors.joining("\n\n"));

        // 2. 收集跳过的 Agent
        String skippedAgents = results.stream()
            .filter(ExecutionResult::isSkipped)
            .map(r -> r.agentId() + "（" + r.status().getDescription() + "）")
            .collect(Collectors.joining("、"));

        // 3. LLM 生成汇总（流式）
        return aggregationClient.prompt()
            .user(AGGREGATION_PROMPT
                .replace("{question}", question)
                .replace("{formattedResults}", formattedResults)
                .replace("{skippedAgents}", skippedAgents.isEmpty() ? "无" : skippedAgents))
            .stream()
            .content();
    }
}
```

---

## 三、OrchestratorAgent V2 改造

```java
// OrchestratorAgent.java — 核心流程改造

public SseEmitter chatStream(String message, String chatId, String userId, String requestId) {
    SseEmitter emitter = new SseEmitter(300000L);
    TraceContext traceCtx = traceRecorder.startTrace(userId, chatId, requestId);

    CompletableFuture.runAsync(() -> {
        try {
            // 1. WorkflowMatcher（替代原来的 detectIntent）
            TraceSpan matchSpan = traceRecorder.startSpan(traceCtx,
                TraceStepType.WORKFLOW_MATCH, "工作流匹配");
            WorkflowMatchResult matchResult = workflowMatcher.match(message);
            WorkflowTemplate workflow = workflowRegistry.get(matchResult.workflowId());
            traceRecorder.putMetadata(matchSpan, "workflowId", workflow.fullId());
            traceRecorder.putMetadata(matchSpan, "matchType", matchResult.matchType().name());
            traceRecorder.endSpan(traceCtx, matchSpan);

            emitter.send(SseEmitter.event().name("routing")
                .data("[工作流：" + workflow.name() + "]"));

            // 2. 构建 ConversationContext（只调用一次，Immutable）
            TraceSpan ctxSpan = traceRecorder.startSpan(traceCtx,
                TraceStepType.CONTEXT_BUILD, "构建上下文");
            ConversationContext conversationContext = contextBuilder.build(chatId, userId);
            traceRecorder.endSpan(traceCtx, ctxSpan);

            // 3. 构建 PromptContext（共享基础 prompt，避免 N+1）
            PromptContext promptCtx = promptContextBuilder.build(conversationContext);

            // 4. 初始化 RuntimeContext（可变执行状态）
            RuntimeContext runtimeContext = new RuntimeContext();

            // 5. 执行所有步骤
            TraceSpan execSpan = traceRecorder.startSpan(traceCtx,
                TraceStepType.TASK_EXECUTION, "任务执行");
            List<ExecutionResult> results = taskExecutor.execute(
                workflow, conversationContext, runtimeContext, promptCtx,
                chatId, userId, message, traceCtx,
                event -> { try { emitter.send(event); } catch (IOException e) {} });
            traceRecorder.endSpan(traceCtx, execSpan);

            // 6. ResultAggregator 汇总（流式）
            TraceSpan aggSpan = traceRecorder.startSpan(traceCtx,
                TraceStepType.RESULT_AGGREGATION, "结果汇总");
            StringBuilder fullAnswer = new StringBuilder();
            resultAggregator.aggregate(message, results, chatId)
                .doOnNext(token -> {
                    try {
                        fullAnswer.append(token);
                        emitter.send(SseEmitter.event().name("message").data(token));
                    } catch (IOException e) { throw new RuntimeException(e); }
                })
                .doOnComplete(() -> {
                    traceRecorder.endSpan(traceCtx, aggSpan);

                    // 7. 持久化
                    chatMemoryAdapter.addMessage(chatId, "user", message,
                        MessageSource.USER, "user", "用户");
                    chatMemoryAdapter.addMessage(chatId, "assistant", fullAnswer.toString(),
                        MessageSource.SYNTHESIZER, "aggregator", "综合顾问");

                    traceRecorder.endTrace(traceCtx);
                    emitter.complete();
                })
                .subscribe();

        } catch (Exception e) {
            log.error("OrchestratorAgent 执行出错", e);
            emitter.completeWithError(e);
        }
    });

    return emitter;
}
```

---

## 四、前端改造

### SSE 事件扩展

```javascript
// 新增事件类型
eventSource.addEventListener('workflow-start', (e) => {
  const data = JSON.parse(e.data)
  // {workflowId: "JOB_CHANGE", workflowName: "跳槽准备", steps: [...]}
})

eventSource.addEventListener('step-start', (e) => {
  const data = JSON.parse(e.data)
  // {taskId: "abc123", agentId: "RESUME", agentName: "简历专家"}
})

eventSource.addEventListener('step-complete', (e) => {
  const data = JSON.parse(e.data)
  // {taskId: "abc123", agentId: "RESUME", status: "SUCCESS", duration: 3200}
})

eventSource.addEventListener('step-skipped', (e) => {
  const data = JSON.parse(e.data)
  // {taskId: "def456", agentId: "NEGOTIATION", reason: "SKIPPED_BY_BUDGET"}
})

// message 事件来源区分（通过 sourceType）
eventSource.addEventListener('message', (e) => {
  // 需要后端在 message 事件中携带 sourceType
  // 或者通过 step-start/step-complete 推断当前是哪个 Agent 在说话
})
```

### 消息气泡

```vue
<!-- 消息按 sourceType 区分样式 -->
<div v-for="msg in messages">
  <!-- 用户消息 -->
  <div v-if="msg.sourceType === 'USER'" class="message user">
    <div class="bubble">{{ msg.content }}</div>
  </div>

  <!-- Agent 消息（子步骤，可折叠） -->
  <div v-else-if="msg.sourceType === 'AGENT'" class="message agent-step">
    <div class="step-header" @click="msg.expanded = !msg.expanded">
      <span class="agent-icon">{{ getAgentIcon(msg.sourceId) }}</span>
      <span class="agent-name">{{ msg.sourceName }}</span>
      <span class="expand-icon">{{ msg.expanded ? '▼' : '▶' }}</span>
    </div>
    <div v-if="msg.expanded" class="step-content">{{ msg.content }}</div>
  </div>

  <!-- 最终汇总 -->
  <div v-else-if="msg.sourceType === 'SYNTHESIZER'" class="message final">
    <div class="bubble final-bubble" v-html="renderMarkdown(msg.content)"></div>
  </div>
</div>
```

---

## 五、文件清单

### V1（群聊模式，1 周）— 现在就做

| 文件 | 操作 | 说明 |
|------|------|------|
| `message/MessageSource.java` | 新增 | 消息来源枚举 |
| `message/PersistentChatMessage.java` | 修改 | 加 sourceType/sourceId/sourceName |
| `message/PersistentMessageRepository.java` | 修改 | save() 支持新字段 |
| `message/ChatMemoryAdapter.java` | 修改 | addMessage() 带 source 参数 |
| `agent/AgentIntent.java` | 修改 | 新增 fromMultiIntent() |
| `agent/OrchestratorAgent.java` | 修改 | 多意图串行执行 |
| `CareerAdvisor.vue` | 修改 | 消息气泡区分 Agent |

### V2（Task Orchestrator，2-3 周）— 接下来做

| 文件 | 操作 | 说明 |
|------|------|------|
| **模型层** | | |
| `agent/output/AgentOutput.java` | 新增 | 接口 |
| `agent/output/ResumeAnalysisOutput.java` | 新增 | 简历分析输出 |
| `agent/output/SalaryAnalysisOutput.java` | 新增 | 薪资分析输出 |
| `agent/output/InterviewAnalysisOutput.java` | 新增 | 面试分析输出 |
| `agent/output/TextOutput.java` | 新增 | 通用文本输出 |
| `agent/output/AgentOutputFormatter.java` | 新增 | Formatter 接口 |
| `agent/output/FormatterRegistry.java` | 新增 | Formatter 注册表 |
| `agent/task/ExecutionResult.java` | 新增 | 统一执行结果 |
| `agent/task/TaskStatus.java` | 新增 | 任务状态枚举 |
| `agent/task/FailurePolicy.java` | 新增 | 失败策略枚举 |
| **预算层** | | |
| `budget/TokenBudget.java` | 新增 | Token 预算 |
| `budget/TokenUsage.java` | 新增 | Token 使用记录 |
| `budget/TokenUsageTracker.java` | 新增 | 使用量追踪 |
| **上下文层** | | |
| `context/ConversationContext.java` | 新增 | 不可变上下文 |
| `context/ConversationContextBuilder.java` | 新增 | 上下文构建器 |
| **工作流层** | | |
| `workflow/WorkflowTemplate.java` | 新增 | 工作流模板 |
| `workflow/WorkflowRegistry.java` | 新增 | 工作流注册表 |
| `workflow/WorkflowMatcher.java` | 新增 | 工作流匹配器 |
| `workflow/WorkflowMatchResult.java` | 新增 | 匹配结果 |
| `workflow/MatchType.java` | 新增 | 匹配类型枚举 |
| **执行层** | | |
| `agent/TaskExecutor.java` | 新增 | 任务执行器 |
| `agent/ResultAggregator.java` | 新增 | 结果汇总器 |
| `agent/AgentRunner.java` | 新增 | Agent 执行接口 |
| **改造** | | |
| `agent/OrchestratorAgent.java` | 重构 | 集成新架构 |
| `config/AgentConfig.java` | 修改 | 注册新 Bean |
| `trace/model/TraceStepType.java` | 修改 | 新增 trace 类型 |
| 前端 | 修改 | 工作流 UI + 步骤展示 |

### V3（Agent DAG，长期）— 未来做

| 文件 | 操作 | 说明 |
|------|------|------|
| `dag/TaskNode.java` | 新增 | DAG 节点 |
| `dag/TaskGraph.java` | 新增 | DAG 图 |
| `dag/TaskGraphEngine.java` | 新增 | DAG 执行引擎 |
| `dag/NodeState.java` | 新增 | 节点状态机 |
| `agent/EvaluatorAgent.java` | 新增 | 质量评估 |
| `workflow/WorkflowTemplateLoader.java` | 新增 | 配置化加载 |

---

## 六、演进路径

```
V1 群聊模式                V2 Task Orchestrator           V3 Agent DAG
(1 周)                    (2-3 周)                       (4-6 周)
─────────────              ──────────────────             ──────────────
User                       User                           User
  ↓                          ↓                              ↓
detectIntents()            WorkflowMatcher                WorkflowMatcher
  ↓                          ↓                              ↓
[RESUME,NEGOTIATION]       WorkflowTemplate               TaskGraph(DAG)
  ↓                          ↓                              ↓
for intent:                TaskExecutor                    DAG Engine
  syncMemory                 ├─ TokenBudgetCheck             ├─ Layer1 并行
  agent.chat()               ├─ Execute Agent                ├─ Layer2 并行
  collect answer             └─ FailurePolicy                └─ Layer3
  ↓                          ↓                              ↓
persist messages           ResultAggregator               ResultAggregator
  ↓                          ↓                              ↓
done                       Evaluator(P1)                  Evaluator
                             ↓                              ↓
                           done                           done/Retry

新增模型:                  新增模型:                       新增模型:
  MessageSource              AgentOutput(接口)              TaskNode
  fromMultiIntent()          ExecutionResult               TaskGraph
                             TokenBudget                   NodeState
                             FailurePolicy
                             ConversationContext

投入: 1周                   投入: 2-3周                     投入: 4-6周
效果: 多专家群聊              效果: 企业级工作流               效果: DAG 编排引擎
```

---

## 七、V1 → V2 升级路径（无痛）

V1 的改动和 V2 完全兼容：

| V1 能力 | V2 复用方式 |
|---------|------------|
| MessageSource 枚举 | 直接复用，不需要改 |
| fromMultiIntent() | 不再需要，被 WorkflowMatcher 替代 |
| 多 Agent 串行执行 | 被 TaskExecutor 替代（加了预算+失败策略） |
| syncCrossAgentMemory | 被 ConversationContext 替代（一次性构建，Agent 共享） |

V1 不会有废弃代码，每一块都自然升级到 V2 的对应组件。

---

## 八、优先级排序

```
P0（V2 必须有，影响线上稳定性）
├── WorkflowMatcher      — 决定用哪个工作流
├── WorkflowRegistry     — 工作流定义
├── TaskExecutor         — 执行引擎
├── FailurePolicy        — 失败不崩
├── TokenBudget          — 成本可控
└── ResultAggregator     — 多 Agent 结果汇总

P1（V2 应该有，提升体验）
├── FormatterRegistry    — 结构化输出消费
├── Evaluator            — 质量门禁
└── WorkflowMatchResult  — 命中率统计

P2（V3 再做）
├── TaskGraph            — DAG 编排
├── NodeState            — 节点状态机
└── TaskGraphEngine      — DAG 执行引擎
```
