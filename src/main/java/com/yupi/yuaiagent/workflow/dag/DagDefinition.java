package com.yupi.yuaiagent.workflow.dag;

import com.yupi.yuaiagent.agent.task.FailurePolicy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable DAG definition: nodes + explicit dependsOn edges.
 */
public record DagDefinition(
        String workflowId,
        FailurePolicy failurePolicy,
        List<DagNodeSpec> nodes
) {
    public DagDefinition {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId must not be blank");
        }
        failurePolicy = failurePolicy != null ? failurePolicy : FailurePolicy.RETRY_THEN_SKIP;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    /**
     * Validate unknown deps and cycles. Throws {@link IllegalArgumentException} on failure.
     */
    public void validate() {
        Map<String, DagNodeSpec> byId = new HashMap<>();
        for (DagNodeSpec node : nodes) {
            if (byId.put(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate DAG node id: " + node.id());
            }
        }
        for (DagNodeSpec node : nodes) {
            for (String dep : node.dependsOn()) {
                if (!byId.containsKey(dep)) {
                    throw new IllegalArgumentException(
                            "Unknown dependency '" + dep + "' on node '" + node.id() + "'");
                }
            }
        }
        List<String> order = topologicalOrder();
        if (order.size() != nodes.size()) {
            throw new IllegalArgumentException("DAG contains a cycle in workflow: " + workflowId);
        }
    }

    /**
     * Kahn topological sort. Returns empty/partial list if cycle (caller should compare sizes).
     */
    public List<String> topologicalOrder() {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> successors = new HashMap<>();
        for (DagNodeSpec node : nodes) {
            indegree.putIfAbsent(node.id(), 0);
            successors.putIfAbsent(node.id(), new ArrayList<>());
        }
        for (DagNodeSpec node : nodes) {
            for (String dep : node.dependsOn()) {
                indegree.merge(node.id(), 1, Integer::sum);
                successors.computeIfAbsent(dep, k -> new ArrayList<>()).add(node.id());
            }
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }
        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            order.add(id);
            for (String next : successors.getOrDefault(id, List.of())) {
                int d = indegree.merge(next, -1, Integer::sum);
                if (d == 0) {
                    queue.add(next);
                }
            }
        }
        return order;
    }

    /** Nodes with no unmet dependencies relative to {@code completed}. */
    public List<DagNodeSpec> readyNodes(Set<String> completed, Set<String> startedOrDone) {
        List<DagNodeSpec> ready = new ArrayList<>();
        for (DagNodeSpec node : nodes) {
            if (startedOrDone.contains(node.id())) {
                continue;
            }
            boolean depsMet = completed.containsAll(node.dependsOn());
            if (depsMet) {
                ready.add(node);
            }
        }
        return ready;
    }

    public DagNodeSpec requireNode(String id) {
        return nodes.stream()
                .filter(n -> n.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown node: " + id));
    }

    public Set<String> allNodeIds() {
        Set<String> ids = new HashSet<>();
        for (DagNodeSpec n : nodes) {
            ids.add(n.id());
        }
        return ids;
    }
}
