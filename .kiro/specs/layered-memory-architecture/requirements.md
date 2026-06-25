# Requirements Document

## Introduction

WorkPilot AI Agent 平台的记忆系统重构：将现有分散的记忆组件（ChatMemoryManager、UserProfileService、MemoryCompressor、RAG VectorStore）整合为一个清晰的四层记忆架构（Layered Memory Architecture）。每一层针对不同的信息特征选择最合适的存储与检索机制，遵循"为正确场景选择正确工具"的工程原则。

四层架构：
- **L1 当前上下文（滑动窗口）**：最近 N 条消息，Token 超限时丢弃最旧消息
- **L2 用户长期事实（结构化存储）**：精确的用户身份/偏好/目标等 KV 键值对
- **L3 近期对话摘要（轻量化摘要）**：最近几轮对话提取的要点清单，静态注入
- **L4 历史经验/案例（向量化检索）**：非结构化的成功/失败案例，语义模糊匹配

## Glossary

- **Memory_Coordinator**: 记忆协调器，统一管理四层记忆的组装、注入和生命周期，是对外的唯一入口
- **Sliding_Window**: 第一层滑动窗口，维护当前会话的最近 N 条完整消息
- **Fact_Store**: 第二层结构化事实存储，以 userId 为主键存储用户长期事实（键值对）
- **Summary_Layer**: 第三层轻量化摘要层，保存最近对话的要点清单
- **Experience_Store**: 第四层历史经验/案例向量存储，支持语义模糊检索
- **Context_Window**: 由 Memory_Coordinator 组装的最终上下文，注入到 LLM prompt 中
- **Fact_Entry**: Fact_Store 中的单条事实记录，包含 key、value、source、updatedAt
- **Summary_Checklist**: Summary_Layer 产出的要点清单，包含 topic 列表和 actionItems
- **Experience_Document**: 存入 Experience_Store 的单条经验文档，包含 content、metadata、embedding

## Requirements

### Requirement 1: Memory Coordinator 统一组装

**User Story:** As a developer, I want a single coordinator that assembles context from all four memory layers, so that each Agent gets a complete, well-structured prompt without manually wiring memory sources.

#### Acceptance Criteria

1. THE Memory_Coordinator SHALL provide a single method `assembleContext(userId, conversationId, agentType)` that returns the composed Context_Window
2. WHEN assembleContext is invoked, THE Memory_Coordinator SHALL query all four layers in parallel and merge results within a configurable timeout (default 2000ms)
3. IF any layer fails to respond within the timeout, THEN THE Memory_Coordinator SHALL use that layer's cached fallback value and log a warning
4. THE Memory_Coordinator SHALL inject the composed Context_Window as a SystemMessage at the beginning of the prompt
5. WHEN composing the Context_Window, THE Memory_Coordinator SHALL respect a total token budget (configurable, default 6000 tokens) by prioritizing layers in order: L1 > L2 > L3 > L4

### Requirement 2: L1 滑动窗口（当前上下文）

**User Story:** As a user, I want my recent conversation messages preserved in full, so that the AI maintains continuity within the current dialogue.

#### Acceptance Criteria

1. THE Sliding_Window SHALL retain the most recent N messages for each conversationId (N configurable, default 20 messages)
2. WHEN a new message is added and total messages exceed N, THE Sliding_Window SHALL discard the oldest message
3. THE Sliding_Window SHALL store messages in-memory per active session with no additional persistence beyond the existing FileBasedChatMemory
4. WHEN the estimated token count of retained messages exceeds the L1 token budget (configurable, default 4000 tokens), THE Sliding_Window SHALL trim oldest messages until within budget
5. THE Sliding_Window SHALL preserve message ordering (insertion order) at all times

### Requirement 3: L2 结构化事实存储（用户长期事实）

**User Story:** As a user, I want the system to remember precise facts about me (name, career, budget, preferences), so that I don't need to repeat myself across sessions.

#### Acceptance Criteria

1. THE Fact_Store SHALL store Fact_Entry records as structured key-value pairs indexed by userId
2. WHEN a new fact is extracted from conversation, THE Fact_Store SHALL upsert the corresponding Fact_Entry (overwrite if key exists, insert if new)
3. THE Fact_Store SHALL support the following fact categories: identity, career, preferences, goals, constraints
4. WHEN the Memory_Coordinator requests user facts, THE Fact_Store SHALL return all Fact_Entry records for the given userId within 50ms (P99)
5. THE Fact_Store SHALL persist facts to a JSON file (per userId) so that facts survive application restarts
6. THE Fact_Store SHALL record the source conversationId and updatedAt timestamp for each Fact_Entry
7. WHEN a fact value conflicts with an existing entry of the same key, THE Fact_Store SHALL overwrite with the newer value and log the change

### Requirement 4: L3 轻量化对话摘要

**User Story:** As a user, I want the system to remember key topics and action items from my recent conversations without storing full transcripts, so that I get continuity across sessions with minimal token cost.

#### Acceptance Criteria

1. WHEN a conversation session ends or message count exceeds a threshold (configurable, default 10 messages), THE Summary_Layer SHALL generate a Summary_Checklist from that conversation
2. THE Summary_Layer SHALL extract the following elements into the Summary_Checklist: topics discussed, key decisions, action items, and unresolved questions
3. THE Summary_Layer SHALL store at most the K most recent Summary_Checklists per userId (K configurable, default 5)
4. WHEN K is exceeded, THE Summary_Layer SHALL discard the oldest Summary_Checklist
5. THE Summary_Layer SHALL NOT store original conversation text — only the distilled checklist items
6. WHEN injected into the Context_Window, THE Summary_Layer SHALL format the checklist as a concise bullet list (no more than 500 tokens)
7. THE Summary_Layer SHALL persist Summary_Checklists to a JSON file so they survive application restarts

### Requirement 5: L4 历史经验/案例（向量化检索）

**User Story:** As a user, I want the system to find relevant past experiences and cases similar to my current question, so that I get better advice informed by historical context.

#### Acceptance Criteria

1. THE Experience_Store SHALL store Experience_Document records in a vector database (PgVector or SimpleVectorStore)
2. WHEN a conversation produces a notable outcome (success case, failure lesson, important insight), THE Experience_Store SHALL create an Experience_Document with content, metadata (userId, agentType, timestamp, outcome), and embedding
3. WHEN the Memory_Coordinator requests experiences, THE Experience_Store SHALL perform semantic similarity search with the current user query and return the top-K most relevant documents (K configurable, default 3)
4. THE Experience_Store SHALL apply a userId filter so that each user only retrieves their own historical experiences
5. THE Experience_Store SHALL support a similarity threshold (configurable, default 0.7) below which documents are not returned
6. IF no documents pass the similarity threshold, THEN THE Experience_Store SHALL return an empty list without error

### Requirement 6: 分层注入与 Token 预算分配

**User Story:** As a developer, I want each memory layer to have a clear token budget, so that the total context stays within model limits and higher-priority information is never truncated.

#### Acceptance Criteria

1. THE Memory_Coordinator SHALL allocate the total token budget across layers with configurable percentages (default: L1=60%, L2=15%, L3=10%, L4=15%)
2. WHEN a layer's content exceeds its allocated budget, THE Memory_Coordinator SHALL truncate that layer's contribution (oldest items first for L1/L3, lowest-relevance items first for L4, least-recently-updated items first for L2)
3. THE Memory_Coordinator SHALL format each layer's contribution with a clear section header so the LLM can distinguish memory sources
4. WHEN total assembled context is under budget, THE Memory_Coordinator SHALL NOT pad or fill unused token space

### Requirement 7: 事实抽取流水线

**User Story:** As a developer, I want facts to be automatically extracted from conversations and stored in the correct layer, so that the memory system stays up-to-date without manual intervention.

#### Acceptance Criteria

1. WHEN a conversation turn is completed, THE Memory_Coordinator SHALL trigger fact extraction asynchronously (non-blocking to the user response)
2. THE Memory_Coordinator SHALL use LLM-based extraction to identify structured facts (Fact_Entry) from the conversation
3. WHEN a structured fact is identified, THE Memory_Coordinator SHALL route it to the Fact_Store (L2)
4. WHEN a conversation-level summary trigger condition is met, THE Memory_Coordinator SHALL route the summary to the Summary_Layer (L3)
5. WHEN a notable experience/case is identified (based on LLM classification), THE Memory_Coordinator SHALL route it to the Experience_Store (L4)
6. IF fact extraction fails, THEN THE Memory_Coordinator SHALL log the error and continue without affecting the user's ongoing conversation

### Requirement 8: 向后兼容与迁移

**User Story:** As a developer, I want the new layered architecture to be backward-compatible with existing ChatMemoryManager and UserProfileService, so that the migration is incremental and non-breaking.

#### Acceptance Criteria

1. THE Memory_Coordinator SHALL delegate L1 sliding window operations to the existing FileBasedChatMemory implementation
2. THE Memory_Coordinator SHALL migrate existing UserProfile data into Fact_Store entries upon first access for each userId
3. WHEN migrating UserProfile to Fact_Store, THE Memory_Coordinator SHALL map communicationPreference, tonePreference, focusAreas, knownBackground, and historicalDemands to corresponding Fact_Entry records
4. THE Memory_Coordinator SHALL preserve the existing ChatMemoryManager API so that Agents not yet migrated continue to function
5. WHILE the migration period is active, THE Memory_Coordinator SHALL support both legacy UserProfileService reads and new Fact_Store reads for the same userId

