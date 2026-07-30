package com.yupi.yuaiagent.workflow.dag;

import com.yupi.yuaiagent.workflow.WorkflowRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagCompilerTest {

    private DagCompiler compiler;

    @BeforeEach
    void setUp() {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.init();
        compiler = new DagCompiler(registry);
    }

    @Test
    void compileJobChangeIsForkJoin() {
        DagDefinition dag = compiler.compile("JOB_CHANGE");
        assertEquals("JOB_CHANGE", dag.workflowId());
        assertEquals(3, dag.nodes().size());
        DagNodeSpec synth = dag.requireNode("synthesize");
        assertEquals(DagNodeType.SYNTHESIZE, synth.type());
        assertTrue(synth.dependsOn().containsAll(List.of("resume", "nego")));
        assertTrue(dag.requireNode("resume").dependsOn().isEmpty());
        assertTrue(dag.requireNode("nego").dependsOn().isEmpty());
    }

    @Test
    void compileInterviewIsSerialThenSynthesize() {
        DagDefinition dag = compiler.compile("INTERVIEW");
        assertEquals("INTERVIEW", dag.workflowId());
        assertEquals(List.of("resume"), dag.requireNode("general").dependsOn());
        assertTrue(dag.requireNode("synthesize").dependsOn().contains("general"));
        List<String> order = dag.topologicalOrder();
        assertTrue(order.indexOf("resume") < order.indexOf("general"));
        assertTrue(order.indexOf("general") < order.indexOf("synthesize"));
    }

    @Test
    void supportsOnlyJobChangeAndInterview() {
        assertTrue(compiler.supports("JOB_CHANGE"));
        assertTrue(compiler.supports("INTERVIEW"));
        assertTrue(!compiler.supports("CONSULTATION"));
    }
}
