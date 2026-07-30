package com.yupi.yuaiagent.workflow.dag;

/**
 * Node types supported by the Phase-1 DAG executor.
 * <p>
 * Parallelism is expressed via empty/shared {@code dependsOn} (ready-queue),
 * not a separate PARALLEL_GROUP node.
 */
public enum DagNodeType {
    /** Invoke an AgentRunner by agentId. */
    AGENT,
    /** Aggregate upstream ExpertOpinions via ResultAggregator.synthesizeDebate. */
    SYNTHESIZE
}
