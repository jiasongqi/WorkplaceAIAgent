package com.yupi.yuaiagent.memory.experience;

import java.time.Instant;
import java.util.Map;

/**
 * L4 Experience Store document representing a notable experience/case
 * stored as a vector-embedded document for semantic similarity search.
 */
public record ExperienceDocument(
    String id,
    String userId,
    String agentType,
    String content,           // narrative description of the experience
    String outcome,           // success | failure | insight
    Instant createdAt,
    Map<String, String> metadata
) {}
