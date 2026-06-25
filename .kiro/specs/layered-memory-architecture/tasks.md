# Tasks: Layered Memory Architecture

## Task 1: Create core memory package structure and enums

- [x] 1.1 Create `MemoryLayer` enum with values: SLIDING_WINDOW, FACT_STORE, SUMMARY, EXPERIENCE
  - File: `src/main/java/com/yupi/yuaiagent/memory/MemoryLayer.java`
- [x] 1.2 Create `ContextWindow` DTO to hold assembled context (layer contributions map + total token count + formatted SystemMessage)
  - File: `src/main/java/com/yupi/yuaiagent/memory/ContextWindow.java`
- [x] 1.3 Create `FactCategory` enum with values: IDENTITY, CAREER, PREFERENCES, GOALS, CONSTRAINTS
  - File: `src/main/java/com/yupi/yuaiagent/memory/fact/FactCategory.java`
- [x] 1.4 Add memory configuration properties to `application.yml` (coordinator timeout, total budget, per-layer settings)
  - File: `src/main/resources/application.yml`

## Task 2: Implement TokenBudgetAllocator

- [x] 2.1 Create `TokenBudgetAllocator` component with `allocate(totalBudget)` returning per-layer token budgets based on configured percentages
  - File: `src/main/java/com/yupi/yuaiagent/memory/TokenBudgetAllocator.java`
- [x] 2.2 Implement `truncateToTokens(content, maxTokens)` method that estimates token count and truncates content at appropriate boundary
  - File: `src/main/java/com/yupi/yuaiagent/memory/TokenBudgetAllocator.java`
- [x] 2.3 Write unit tests for TokenBudgetAllocator (allocation percentages sum correctly, truncation respects limits)
  - File: `src/test/java/com/yupi/yuaiagent/memory/TokenBudgetAllocatorTest.java`

## Task 3: Implement SlidingWindowLayer (L1)

- [x] 3.1 Create `SlidingWindowLayer` component wrapping existing `ChatMemoryManager`
  - File: `src/main/java/com/yupi/yuaiagent/memory/sliding/SlidingWindowLayer.java`
- [x] 3.2 Implement `getRecentMessages(conversationId, agentType, tokenBudget)` that retrieves messages and trims to budget
  - File: `src/main/java/com/yupi/yuaiagent/memory/sliding/SlidingWindowLayer.java`
- [x] 3.3 Write unit tests for SlidingWindowLayer (message retention, token budget trimming, ordering preservation)
  - File: `src/test/java/com/yupi/yuaiagent/memory/sliding/SlidingWindowLayerTest.java`

## Task 4: Implement FactStoreLayer (L2)

- [x] 4.1 Create `FactEntry` record with fields: key, value, category, sourceConversationId, updatedAt
  - File: `src/main/java/com/yupi/yuaiagent/memory/fact/FactEntry.java`
- [x] 4.2 Create `FactStoreLayer` repository with JSON file persistence (per-userId file), ReadWriteLock, and CRUD operations
  - File: `src/main/java/com/yupi/yuaiagent/memory/fact/FactStoreLayer.java`
- [x] 4.3 Implement `upsert(userId, factEntry)` with overwrite-on-same-key semantics and change logging
  - File: `src/main/java/com/yupi/yuaiagent/memory/fact/FactStoreLayer.java`
- [x] 4.4 Implement `migrateFromProfile(userId, userProfile)` to convert existing UserProfile to FactEntry records
  - File: `src/main/java/com/yupi/yuaiagent/memory/fact/FactStoreLayer.java`
- [x] 4.5 Write unit tests for FactStoreLayer (upsert idempotence, migration correctness, persistence round-trip)
  - File: `src/test/java/com/yupi/yuaiagent/memory/fact/FactStoreLayerTest.java`

## Task 5: Implement SummaryLayer (L3)

- [x] 5.1 Create `SummaryChecklist` record with fields: conversationId, createdAt, topics, decisions, actionItems, unresolvedQuestions
  - File: `src/main/java/com/yupi/yuaiagent/memory/summary/SummaryChecklist.java`
- [x] 5.2 Create `SummaryLayer` component with JSON persistence, FIFO eviction, and LLM-based generation
  - File: `src/main/java/com/yupi/yuaiagent/memory/summary/SummaryLayer.java`
- [x] 5.3 Implement `generateAndStore(userId, conversationId, messages)` using LLM to extract checklist (reusing MemoryCompressor pattern)
  - File: `src/main/java/com/yupi/yuaiagent/memory/summary/SummaryLayer.java`
- [x] 5.4 Implement `getRecentSummaries(userId, tokenBudget)` that returns formatted checklist text within budget
  - File: `src/main/java/com/yupi/yuaiagent/memory/summary/SummaryLayer.java`
- [x] 5.5 Write unit tests for SummaryLayer (FIFO eviction, max K enforcement, no original text stored)
  - File: `src/test/java/com/yupi/yuaiagent/memory/summary/SummaryLayerTest.java`

## Task 6: Implement ExperienceStoreLayer (L4)

- [x] 6.1 Create `ExperienceDocument` record with fields: id, userId, agentType, content, outcome, createdAt, metadata
  - File: `src/main/java/com/yupi/yuaiagent/memory/experience/ExperienceDocument.java`
- [x] 6.2 Create `ExperienceStoreLayer` component integrating with existing VectorStore (PgVector/SimpleVectorStore)
  - File: `src/main/java/com/yupi/yuaiagent/memory/experience/ExperienceStoreLayer.java`
- [x] 6.3 Implement `store(document)` that embeds content and stores with userId metadata for filtering
  - File: `src/main/java/com/yupi/yuaiagent/memory/experience/ExperienceStoreLayer.java`
- [x] 6.4 Implement `searchSimilar(userId, query, topK, threshold)` with userId filter and similarity threshold
  - File: `src/main/java/com/yupi/yuaiagent/memory/experience/ExperienceStoreLayer.java`
- [x] 6.5 Write unit tests for ExperienceStoreLayer (userId isolation, threshold filtering, empty result on no match)
  - File: `src/test/java/com/yupi/yuaiagent/memory/experience/ExperienceStoreLayerTest.java`

## Task 7: Implement ExtractionPipeline

- [x] 7.1 Create `ExtractionPipeline` component with async processing (dedicated thread pool)
  - File: `src/main/java/com/yupi/yuaiagent/memory/extraction/ExtractionPipeline.java`
- [x] 7.2 Implement LLM-based extraction prompt that classifies and extracts facts/summaries/experiences in a single pass (structured output)
  - File: `src/main/java/com/yupi/yuaiagent/memory/extraction/ExtractionPipeline.java`
- [x] 7.3 Implement routing logic: facts → FactStoreLayer, summaries → SummaryLayer, experiences → ExperienceStoreLayer
  - File: `src/main/java/com/yupi/yuaiagent/memory/extraction/ExtractionPipeline.java`
- [x] 7.4 Write unit tests for ExtractionPipeline (routing correctness, error isolation, async non-blocking behavior)
  - File: `src/test/java/com/yupi/yuaiagent/memory/extraction/ExtractionPipelineTest.java`

## Task 8: Implement MemoryCoordinator

- [x] 8.1 Create `MemoryCoordinator` service with `assembleContext(userId, conversationId, agentType)` method
  - File: `src/main/java/com/yupi/yuaiagent/memory/MemoryCoordinator.java`
- [x] 8.2 Implement parallel layer queries using CompletableFuture.allOf() with configurable timeout and fallback
  - File: `src/main/java/com/yupi/yuaiagent/memory/MemoryCoordinator.java`
- [x] 8.3 Implement context composition: apply token budget, format with section headers, build SystemMessage
  - File: `src/main/java/com/yupi/yuaiagent/memory/MemoryCoordinator.java`
- [x] 8.4 Implement `onTurnCompleted()` hook to trigger ExtractionPipeline asynchronously
  - File: `src/main/java/com/yupi/yuaiagent/memory/MemoryCoordinator.java`
- [x] 8.5 Write integration tests for MemoryCoordinator (full assembly, timeout fallback, partial failure)
  - File: `src/test/java/com/yupi/yuaiagent/memory/MemoryCoordinatorTest.java`

## Task 9: Backward compatibility and migration

- [x] 9.1 Implement lazy UserProfile → FactStore migration in FactStoreLayer.getFacts() (check if userId has facts, if not migrate from UserProfileService)
  - File: `src/main/java/com/yupi/yuaiagent/memory/fact/FactStoreLayer.java`
- [x] 9.2 Ensure MemoryCoordinator delegates L1 to existing ChatMemoryManager (no new persistence layer for messages)
  - File: `src/main/java/com/yupi/yuaiagent/memory/MemoryCoordinator.java`
- [x] 9.3 Add `@ConditionalOnProperty("memory.coordinator.enabled")` to MemoryCoordinator so it can be disabled
  - File: `src/main/java/com/yupi/yuaiagent/memory/MemoryCoordinator.java`
- [x] 9.4 Write migration test: verify UserProfile fields correctly map to FactEntry records
  - File: `src/test/java/com/yupi/yuaiagent/memory/fact/FactStoreMigrationTest.java`

## Task 10: Wire MemoryCoordinator into an Agent (integration)

- [x] 10.1 Inject MemoryCoordinator into GeneralCareerAgent and call assembleContext() before prompt construction
  - File: `src/main/java/com/yupi/yuaiagent/agent/GeneralCareerAgent.java`
- [x] 10.2 Call MemoryCoordinator.onTurnCompleted() after each Agent response to trigger async extraction
  - File: `src/main/java/com/yupi/yuaiagent/agent/GeneralCareerAgent.java`
- [x] 10.3 Add end-to-end integration test verifying context assembly in a realistic Agent interaction
  - File: `src/test/java/com/yupi/yuaiagent/memory/MemoryIntegrationTest.java`
