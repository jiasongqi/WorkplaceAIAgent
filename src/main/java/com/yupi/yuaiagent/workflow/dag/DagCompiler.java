package com.yupi.yuaiagent.workflow.dag;

import com.yupi.yuaiagent.workflow.WorkflowRegistry;
import com.yupi.yuaiagent.workflow.WorkflowTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Compiles workflow templates into executable DAG definitions.
 * <p>
 * JOB_CHANGE: parallel RESUME + NEGOTIATION → SYNTHESIZE<br>
 * INTERVIEW: RESUME → GENERAL → SYNTHESIZE (serial)
 */
@Component
public class DagCompiler {

    public static final Set<String> SUPPORTED_WORKFLOWS = Set.of("JOB_CHANGE", "INTERVIEW");

    private final WorkflowRegistry workflowRegistry;

    public DagCompiler(WorkflowRegistry workflowRegistry) {
        this.workflowRegistry = workflowRegistry;
    }

    public boolean supports(String workflowId) {
        return SUPPORTED_WORKFLOWS.contains(workflowId);
    }

    public DagDefinition compile(String workflowId) {
        WorkflowTemplate template = workflowRegistry.get(workflowId);
        if (template == null) {
            throw new IllegalArgumentException("Unknown workflow template: " + workflowId);
        }
        return switch (workflowId) {
            case "JOB_CHANGE" -> compileJobChange(template);
            case "INTERVIEW" -> compileInterview(template);
            default -> throw new IllegalArgumentException(
                    "DAG compile not supported for workflow: " + workflowId);
        };
    }

    private DagDefinition compileJobChange(WorkflowTemplate template) {
        List<DagNodeSpec> nodes = List.of(
                DagNodeSpec.agent("resume", "RESUME", "简历优化", List.of()),
                DagNodeSpec.agent("nego", "NEGOTIATION", "薪资分析", List.of()),
                DagNodeSpec.synthesize("synthesize", List.of("resume", "nego"))
        );
        DagDefinition dag = new DagDefinition(template.id(), template.failurePolicy(), nodes);
        dag.validate();
        return dag;
    }

    private DagDefinition compileInterview(WorkflowTemplate template) {
        List<DagNodeSpec> nodes = List.of(
                DagNodeSpec.agent("resume", "RESUME", "简历优化", List.of()),
                DagNodeSpec.agent("general", "GENERAL", "面试辅导", List.of("resume")),
                DagNodeSpec.synthesize("synthesize", List.of("resume", "general"))
        );
        DagDefinition dag = new DagDefinition(template.id(), template.failurePolicy(), nodes);
        dag.validate();
        return dag;
    }

    /** Test helper: compile from explicit node list. */
    public static DagDefinition of(String workflowId, List<DagNodeSpec> nodes) {
        List<DagNodeSpec> copy = new ArrayList<>(nodes);
        DagDefinition dag = new DagDefinition(workflowId, null, copy);
        dag.validate();
        return dag;
    }
}
