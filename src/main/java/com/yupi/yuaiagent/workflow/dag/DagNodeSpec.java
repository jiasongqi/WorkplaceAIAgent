package com.yupi.yuaiagent.workflow.dag;

import java.util.List;

/**
 * One node in a DAG workflow.
 *
 * @param id              unique node id within the graph
 * @param type            AGENT or SYNTHESIZE
 * @param agentId         agent key for AGENT nodes (e.g. RESUME); null for SYNTHESIZE
 * @param taskDescription human-readable task label
 * @param dependsOn       upstream node ids that must complete first
 */
public record DagNodeSpec(
        String id,
        DagNodeType type,
        String agentId,
        String taskDescription,
        List<String> dependsOn
) {
    public DagNodeSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("DagNodeSpec.id must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("DagNodeSpec.type must not be null");
        }
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        if (type == DagNodeType.AGENT && (agentId == null || agentId.isBlank())) {
            throw new IllegalArgumentException("AGENT node requires agentId: " + id);
        }
    }

    public static DagNodeSpec agent(String id, String agentId, String taskDescription, List<String> dependsOn) {
        return new DagNodeSpec(id, DagNodeType.AGENT, agentId, taskDescription, dependsOn);
    }

    public static DagNodeSpec synthesize(String id, List<String> dependsOn) {
        return new DagNodeSpec(id, DagNodeType.SYNTHESIZE, null, "综合裁决", dependsOn);
    }
}
