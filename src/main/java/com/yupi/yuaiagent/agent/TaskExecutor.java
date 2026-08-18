package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.output.AgentOutput;
import com.yupi.yuaiagent.agent.output.TextOutput;
import com.yupi.yuaiagent.agent.task.ExecutionResult;
import com.yupi.yuaiagent.agent.task.FailurePolicy;
import com.yupi.yuaiagent.agent.task.TaskStatus;
import com.yupi.yuaiagent.budget.TokenBudget;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.budget.TokenUsageTracker;
import com.yupi.yuaiagent.context.ConversationContext;
import com.yupi.yuaiagent.context.RuntimeContext;
import com.yupi.yuaiagent.workflow.PlanStep;
import com.yupi.yuaiagent.workflow.WorkflowTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow execution engine — runs all PlanSteps in a WorkflowTemplate.
 * Handles token budget checks, failure policies, and result collection.
 *
 * @author jsq
 */
@Slf4j
@Component
public class TaskExecutor {

    private final TokenUsageTracker tokenTracker;
    private Map<String, AgentRunner> agentRunners = Map.of();
    private AgentRunnerRegistry runnerRegistry;
    private boolean failIfMissingRunner;

    public TaskExecutor(TokenUsageTracker tokenTracker) {
        this.tokenTracker = tokenTracker;
    }

    public void setRunnerRegistry(AgentRunnerRegistry runnerRegistry, boolean failIfMissingRunner) {
        this.runnerRegistry = runnerRegistry;
        this.failIfMissingRunner = failIfMissingRunner;
    }

    /**
     * Inject agent runners — called by OrchestratorAgent after creating sub-agents.
     */
    public void setAgentRunners(Map<String, AgentRunner> runners) {
        this.agentRunners = runners;
    }

    /**
     * Execute all steps in the workflow.
     */
    public List<ExecutionResult> execute(
            WorkflowTemplate workflow,
            ConversationContext conversationContext,
            RuntimeContext runtimeContext,
            String userMessage) {

        String workflowId = workflow.fullId();
        tokenTracker.reset(workflowId);

        for (PlanStep step : workflow.steps()) {
            String taskId = UUID.randomUUID().toString().substring(0, 8);

            // 1. Token budget check
            AgentRunner runner = resolveRunner(step.agentId());
            if (runner == null) {
                if (failIfMissingRunner) {
                    throw new IllegalStateException("No runner for agentId=" + step.agentId());
                }
                log.warn("[TaskExecutor] No runner for agentId={}, skipping", step.agentId());
                runtimeContext.addResult(ExecutionResult.skipped(taskId, step.agentId(), TaskStatus.SKIPPED));
                continue;
            }

            long estimated = estimateTokens(userMessage);
            if (!tokenTracker.canExecute(workflowId, workflow.tokenBudget(), estimated)) {
                log.info("[TaskExecutor] Budget exceeded for {}, SKIPPED_BY_BUDGET", step.agentId());
                runtimeContext.addResult(ExecutionResult.skipped(taskId, step.agentId(), TaskStatus.SKIPPED_BY_BUDGET));
                continue;
            }

            // 2. FailurePolicy check
            if (runtimeContext.hasFailures() && workflow.failurePolicy() == FailurePolicy.FAIL_FAST) {
                runtimeContext.addResult(ExecutionResult.skipped(taskId, step.agentId(), TaskStatus.SKIPPED_BY_POLICY));
                continue;
            }

            // 3. Execute Agent
            long start = System.currentTimeMillis();
            try {
                log.info("[TaskExecutor] Executing step: {} ({})", step.agentId(), step.taskDescription());
                AgentOutput output = runner.run(conversationContext, userMessage);
                long duration = System.currentTimeMillis() - start;

                TokenUsage usage = runner.getLastTokenUsage();
                tokenTracker.recordUsage(workflowId, usage);
                runtimeContext.addResult(ExecutionResult.success(taskId, step.agentId(), output, usage, duration, 0));

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                log.error("[TaskExecutor] Step {} failed: {}", step.agentId(), e.getMessage());

                ExecutionResult result = handleFailure(workflow.failurePolicy(), taskId, step, e, duration,
                    conversationContext, userMessage, runner);
                runtimeContext.addResult(result);

                if (workflow.failurePolicy() == FailurePolicy.FAIL_FAST) {
                    break;
                }
            }
        }

        return runtimeContext.getResults();
    }

    private AgentRunner resolveRunner(String agentId) {
        AgentRunner mapped = agentRunners.get(agentId);
        if (mapped != null) {
            return mapped;
        }
        return runnerRegistry == null ? null : runnerRegistry.get(agentId).orElse(null);
    }

    private ExecutionResult handleFailure(FailurePolicy policy, String taskId, PlanStep step,
                                           Exception error, long duration,
                                           ConversationContext context, String userMessage,
                                           AgentRunner runner) {
        return switch (policy) {
            case FAIL_FAST -> ExecutionResult.failed(taskId, step.agentId(), error, duration);
            case RETRY_THEN_SKIP -> {
                try {
                    AgentOutput output = runner.run(context, userMessage);
                    yield ExecutionResult.success(taskId, step.agentId(), output,
                        runner.getLastTokenUsage(), duration, 1);
                } catch (Exception retryError) {
                    yield ExecutionResult.skipped(taskId, step.agentId(), TaskStatus.SKIPPED);
                }
            }
            case RETRY_THEN_FAIL -> {
                try {
                    AgentOutput output = runner.run(context, userMessage);
                    yield ExecutionResult.success(taskId, step.agentId(), output,
                        runner.getLastTokenUsage(), duration, 1);
                } catch (Exception retryError) {
                    yield ExecutionResult.failed(taskId, step.agentId(), retryError, duration);
                }
            }
            case SKIP -> ExecutionResult.skipped(taskId, step.agentId(), TaskStatus.SKIPPED);
        };
    }

    private long estimateTokens(String text) {
        // Rough estimate: 1 token per Chinese char, 1 per 4 English chars
        return text.length();
    }
}
