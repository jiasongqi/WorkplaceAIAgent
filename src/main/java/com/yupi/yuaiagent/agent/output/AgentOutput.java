package com.yupi.yuaiagent.agent.output;

import java.util.List;

/**
 * Typed output interface for Agent results.
 * Each Agent produces a concrete AgentOutput that carries structured data.
 *
 * @author jsq
 */
public interface AgentOutput {

    /** Text summary for ResultAggregator. */
    String summary();

    /** Associated artifact IDs (weak reference to ArtifactShelf). */
    List<String> artifactIds();
}
