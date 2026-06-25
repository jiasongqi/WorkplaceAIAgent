# Design Document: Layered Memory Architecture

## Overview

将 WorkPilot 现有分散的记忆组件重构为统一的四层记忆架构。Memory Coordinator 作为唯一入口，并行查询四层并按 Token 预算组装最终上下文。

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Memory Coordinator                         │
│  assembleContext(userId, conversationId, agentType)          │
│  ┌──────────┬──────────┬──────────┬──────────┐              │
│  │ L1 Query │ L2 Query │ L3 Query │ L4 Query │  (parallel)  │
│  └────┬─────┴────┬─────┴────┬─────┴────┬─────┘              │
│       ▼          ▼          ▼          ▼                     │
│  Token Budget Allocator (6000 tokens total)                  │
│  L1=60% | L2=15% | L3=10% | L4=15%                         │
│       ▼                                                      │
│  Context Window (SystemMessage)                              │
└─────────────────────────────────────────────────────────────┘
         │              │              │              │
         ▼              ▼              ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ L1: Sliding  │ │ L2: Fact     │ │ L3: Summary  │ │ L4: Experi-  │
│    Window    │ │    Store     │ │    Layer     │ │   ence Store │
│              │ │              │ │              │ │              │
│ In-memory    │ │ JSON file    │ │ JSON file    │ │ PgVector /   │
│ + Kryo file  │ │ (per user)   │ │ (per user)   │ │ SimpleVector │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘

         ▲ (async extraction after each turn)
         │
┌──────────────────────────────────────────┐
│         Extraction Pipeline              │
│  LLM → classify → route to L2/L3/L4     │
└──────────────────────────────────────────┘
```

## Technology Stack

- Java 21 + Spring Boot 3.4 + Spring AI 1.0
- DashScope (LLM for extraction & summarization)
- PgVector / SimpleVectorStore (L4 vector search)
- Jackson ObjectMapper (L2/L3 JSON persistence)
- Kryo (L1 existing persistence via FileBasedChatMemory)
- CompletableFuture (parallel layer queries)

## Components

### 1. MemoryCoordinator

**Package:** `com.yupi.yuaiagent.memory`

**Responsibilities:**
- Provide `assembleContext(userId, conversationId, agentType)` as the single entry point
- Query all 4 layers in parallel with configurable timeout
- Apply token budget allocation and truncation
- Format and return the composed SystemMessage

**Key Design Decisions:**
- Uses `CompletableFuture.allOf()` with `orTimeout()` for parallel queries
- Fallback: cached last-good value per layer, per userId (in-memory ConcurrentHashMap)
- Extends existing `ChatMemoryManager` delegation pattern for backward compatibility

```java
@Service
public class MemoryCoordinator {
    private final SlidingWindowLayer slidingWindow;
    private final FactStoreLayer factStore;
    private final SummaryLayer summaryLayer;
    private final ExperienceStoreLayer experienceStore;
    private final TokenBudgetAllocator budgetAllocator;
    private final ExtractionPipeline extractionPipeline;
    
    public SystemMessage assembleContext(String userId, String conversationId, String agentType) { ... }
    public void onTurnCompleted(String userId, String conversationId, List<Message> messages) { ... }
}
```

### 2. SlidingWindowLayer (L1)

**Package:** `com.yupi.yuaiagent.memory.sliding`

**Responsibilities:**
- Wraps existing `FileBasedChatMemory` — no new persistence
- Maintains configurable max message count (default 20) and token budget (default 4000)
- Trims oldest messages when limits are exceeded

**Key Design Decisions:**
- Delegates to existing `ChatMemoryManager.getMemory(agentType)` for storage
- Token estimation reuses existing `TokenCompressionStrategy` logic
- No new file format; fully backward-compatible

```java
@Component
public class SlidingWindowLayer {
    private final ChatMemoryManager chatMemoryManager;
    
    public List<Message> getRecentMessages(String conversationId, String agentType, int tokenBudget) { ... }
}
```

### 3. FactStoreLayer (L2)

**Package:** `com.yupi.yuaiagent.memory.fact`

**Responsibilities:**
- Store/retrieve structured key-value facts per userId
- Support upsert (overwrite same key) with change logging
- Persist to JSON file (`./tmp/memory/facts/{userId}.json`)
- Migrate existing UserProfile data on first access

**Data Model:**
```java
public record FactEntry(
    String key,                // e.g. "name", "budget", "industry"
    String value,             
    String category,          // identity | career | preferences | goals | constraints
    String sourceConversationId,
    Instant updatedAt
) {}
```

**Key Design Decisions:**
- JSON file per userId (not a single large file) — scales with user count, avoids lock contention
- On first access, checks if UserProfile exists and migrates to FactEntry records
- ReadWriteLock per userId (via Striped lock pattern or ConcurrentHashMap-based)
- Returns all facts for a userId as a flat list; Memory Coordinator formats for injection

```java
@Repository
public class FactStoreLayer {
    public List<FactEntry> getFacts(String userId) { ... }
    public void upsert(String userId, FactEntry entry) { ... }
    public void migrateFromProfile(String userId, UserProfile profile) { ... }
}
```

### 4. SummaryLayer (L3)

**Package:** `com.yupi.yuaiagent.memory.summary`

**Responsibilities:**
- Generate lightweight checklist from conversation (topics, decisions, action items, unresolved)
- Store at most K recent checklists per userId (default 5)
- Persist to JSON file (`./tmp/memory/summaries/{userId}.json`)
- Return formatted checklist text within token budget

**Data Model:**
```java
public record SummaryChecklist(
    String conversationId,
    Instant createdAt,
    List<String> topics,
    List<String> decisions,
    List<String> actionItems,
    List<String> unresolvedQuestions
) {}
```

**Key Design Decisions:**
- Reuses `MemoryCompressor` LLM call pattern but with a simpler prompt targeting checklist format
- Triggered when session ends or message count exceeds threshold (default 10)
- FIFO eviction when K checklists exceeded
- No original text stored — only distilled bullet points

```java
@Component
public class SummaryLayer {
    public List<SummaryChecklist> getRecentSummaries(String userId, int tokenBudget) { ... }
    public void generateAndStore(String userId, String conversationId, List<Message> messages) { ... }
}
```

### 5. ExperienceStoreLayer (L4)

**Package:** `com.yupi.yuaiagent.memory.experience`

**Responsibilities:**
- Store notable experiences/cases as vector-embedded documents
- Semantic similarity search with user query
- Filter by userId, apply similarity threshold
- Support both PgVector and SimpleVectorStore backends

**Data Model:**
```java
public record ExperienceDocument(
    String id,
    String userId,
    String agentType,
    String content,           // narrative description of the experience
    String outcome,           // success | failure | insight
    Instant createdAt,
    Map<String, String> metadata
) {}
```

**Key Design Decisions:**
- Leverages existing `PgVectorVectorStoreConfig` / `AiChatVectorStoreConfig` infrastructure
- Documents are embedded with DashScope embedding model
- Metadata filter: `userId == currentUserId` ensures user isolation
- Similarity threshold (default 0.7) prevents irrelevant results
- Returns top-K results (default 3) ranked by similarity score

```java
@Component
public class ExperienceStoreLayer {
    private final VectorStore vectorStore;
    
    public List<ExperienceDocument> searchSimilar(String userId, String query, int topK, double threshold) { ... }
    public void store(ExperienceDocument document) { ... }
}
```

### 6. TokenBudgetAllocator

**Package:** `com.yupi.yuaiagent.memory`

**Responsibilities:**
- Allocate total token budget across layers by configurable percentages
- Truncate each layer's output to its budget
- Handle under-budget scenarios (no padding)

```java
@Component
public class TokenBudgetAllocator {
    public Map<MemoryLayer, Integer> allocate(int totalBudget) { ... }
    public String truncateToTokens(String content, int maxTokens) { ... }
}
```

### 7. ExtractionPipeline

**Package:** `com.yupi.yuaiagent.memory.extraction`

**Responsibilities:**
- Async post-turn processing: classify content and route to correct layer
- Extract structured facts → L2
- Generate summaries on trigger → L3
- Classify notable experiences → L4
- Non-blocking, error-tolerant (failures logged, not propagated)

**Key Design Decisions:**
- Runs on dedicated thread pool (`@Async` with custom executor, same pattern as `profileExecutor`)
- Single LLM call with structured output to classify + extract in one pass
- Uses Spring AI structured output (JSON schema) for reliable parsing

```java
@Component
public class ExtractionPipeline {
    public void processAsync(String userId, String conversationId, String agentType, List<Message> messages) { ... }
}
```

### 8. Backward Compatibility Adapter

**Package:** `com.yupi.yuaiagent.memory`

**Responsibilities:**
- Ensure existing `ChatMemoryManager` API remains functional
- Existing Agents that call `chatMemoryManager.getMemory(agentType)` continue to work unchanged
- `UserProfileService` remains operational during migration period
- Memory Coordinator is opt-in: new/migrated Agents use it, legacy Agents use old path

**Strategy:**
- `MemoryCoordinator` is a new bean that does NOT replace `ChatMemoryManager`
- Agents that want the new architecture inject `MemoryCoordinator` directly
- `MemoryCoordinator` internally calls `ChatMemoryManager` for L1, ensuring single source of truth
- `FactStoreLayer` lazy-migrates UserProfile data on first read per userId

## Configuration

```yaml
# 分层记忆配置
memory:
  coordinator:
    enabled: true
    timeout-ms: 2000
    total-token-budget: 6000
  layers:
    sliding-window:
      max-messages: 20
      token-budget-percent: 60
    fact-store:
      storage-dir: ./tmp/memory/facts
      token-budget-percent: 15
    summary:
      storage-dir: ./tmp/memory/summaries
      max-checklists: 5
      trigger-threshold: 10
      token-budget-percent: 10
    experience:
      token-budget-percent: 15
      top-k: 3
      similarity-threshold: 0.7
```

## File Structure

```
src/main/java/com/yupi/yuaiagent/memory/
├── MemoryCoordinator.java
├── MemoryLayer.java                    (enum: SLIDING_WINDOW, FACT_STORE, SUMMARY, EXPERIENCE)
├── TokenBudgetAllocator.java
├── ContextWindow.java                  (assembled context DTO)
├── sliding/
│   └── SlidingWindowLayer.java
├── fact/
│   ├── FactStoreLayer.java
│   ├── FactEntry.java
│   └── FactCategory.java              (enum)
├── summary/
│   ├── SummaryLayer.java
│   └── SummaryChecklist.java
├── experience/
│   ├── ExperienceStoreLayer.java
│   └── ExperienceDocument.java
└── extraction/
    └── ExtractionPipeline.java
```

## Migration Strategy

1. **Phase 1**: Create new `memory` package with all components. MemoryCoordinator delegates L1 to existing ChatMemoryManager.
2. **Phase 2**: FactStoreLayer implements lazy migration from UserProfile on first access.
3. **Phase 3**: Migrate GeneralCareerAgent (and OrchestratorAgent) to use MemoryCoordinator.
4. **Phase 4**: Deprecate direct ChatMemoryManager usage in new Agent code (old Agents unaffected).

## Error Handling

- All layer failures are isolated: one layer's error does not affect others
- MemoryCoordinator returns partial context (available layers) on failure
- ExtractionPipeline errors are logged but never propagate to user response
- File I/O failures: warn and serve from in-memory cache

## Testing Strategy

- Unit tests per layer with mock data
- Integration test: MemoryCoordinator with all layers wired
- Property-based test: Token budget allocation always sums to total budget
- Property-based test: Fact upsert maintains single entry per key (idempotence)
- Property-based test: Summary FIFO eviction never exceeds K entries
