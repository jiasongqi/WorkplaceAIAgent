package com.yupi.yuaiagent.workflow.dag;

import com.yupi.yuaiagent.agent.task.FailurePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagDefinitionTest {

    @Test
    void rejectsCycle() {
        List<DagNodeSpec> nodes = List.of(
                DagNodeSpec.agent("a", "RESUME", "A", List.of("b")),
                DagNodeSpec.agent("b", "NEGOTIATION", "B", List.of("a"))
        );
        DagDefinition dag = new DagDefinition("CYCLE", FailurePolicy.FAIL_FAST, nodes);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, dag::validate);
        assertTrue(ex.getMessage().contains("cycle") || ex.getMessage().contains("Cycle")
                || ex.getMessage().toLowerCase().contains("cycle"));
    }

    @Test
    void rejectsUnknownDependency() {
        List<DagNodeSpec> nodes = List.of(
                DagNodeSpec.agent("a", "RESUME", "A", List.of("missing"))
        );
        DagDefinition dag = new DagDefinition("BAD", FailurePolicy.FAIL_FAST, nodes);
        assertThrows(IllegalArgumentException.class, dag::validate);
    }

    @Test
    void topologicalOrderForJobChangeShape() {
        List<DagNodeSpec> nodes = List.of(
                DagNodeSpec.agent("resume", "RESUME", "简历", List.of()),
                DagNodeSpec.agent("nego", "NEGOTIATION", "薪资", List.of()),
                DagNodeSpec.synthesize("synthesize", List.of("resume", "nego"))
        );
        DagDefinition dag = new DagDefinition("JOB_CHANGE", FailurePolicy.RETRY_THEN_SKIP, nodes);
        dag.validate();
        List<String> order = dag.topologicalOrder();
        assertEquals(3, order.size());
        assertTrue(order.indexOf("resume") < order.indexOf("synthesize"));
        assertTrue(order.indexOf("nego") < order.indexOf("synthesize"));
    }
}
