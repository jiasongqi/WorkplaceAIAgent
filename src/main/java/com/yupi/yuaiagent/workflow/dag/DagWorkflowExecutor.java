package com.yupi.yuaiagent.workflow.dag;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.agent.AgentRunner;
import com.yupi.yuaiagent.agent.ResultAggregator;
import com.yupi.yuaiagent.agent.collaboration.ExpertOpinion;
import com.yupi.yuaiagent.agent.output.AgentOutput;
import com.yupi.yuaiagent.agent.task.FailurePolicy;
import com.yupi.yuaiagent.context.ConversationContext;
import com.yupi.yuaiagent.workflow.runtime.WorkflowInstance;
import com.yupi.yuaiagent.workflow.runtime.WorkflowRepository;
import com.yupi.yuaiagent.workflow.runtime.WorkflowStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Ready-queue DAG executor: runs AGENT nodes via {@link AgentRunner},
 * SYNTHESIZE via {@link ResultAggregator#synthesizeDebate}.
 * <p>
 * Nodes with satisfied {@code dependsOn} run in parallel ({@link CompletableFuture}).
 */
@Slf4j
@Service
public class DagWorkflowExecutor {

    private static final int MAX_STEPS = 100;

    private final WorkflowRepository workflowRepository;
    private final ResultAggregator resultAggregator;
    private final Executor agentExecutor;

    private volatile Map<String, AgentRunner> agentRunners = Map.of();
    private volatile com.yupi.yuaiagent.agent.AgentRunnerRegistry runnerRegistry;
    private volatile boolean failIfMissingRunner;

    public DagWorkflowExecutor(WorkflowRepository workflowRepository,
                               ResultAggregator resultAggregator,
                               @org.springframework.beans.factory.annotation.Qualifier("agentExecutor")
                               Executor agentExecutor) {
        this.workflowRepository = workflowRepository;
        this.resultAggregator = resultAggregator;
        this.agentExecutor = agentExecutor;
    }

    public void setAgentRunners(Map<String, AgentRunner> runners) {
        this.agentRunners = runners != null ? Map.copyOf(runners) : Map.of();
    }

    public void setRunnerRegistry(com.yupi.yuaiagent.agent.AgentRunnerRegistry runnerRegistry, boolean failIfMissingRunner) {
        this.runnerRegistry = runnerRegistry;
        this.failIfMissingRunner = failIfMissingRunner;
    }

    /**
     * Synchronously execute a validated DAG and return the final answer.
     */
    public DagExecutionResult execute(DagDefinition dag,
                                      ConversationContext conversationContext,
                                      String userMessage,
                                      String userId,
                                      String chatId,
                                      DagProgressListener listener) {
        dag.validate();
        DagProgressListener progress = listener != null ? listener : DagProgressListener.noop();

        WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId(UUID.randomUUID().toString())
                .workflowId(dag.workflowId())
                .context(new HashMap<>())
                .userId(userId)
                .chatId(chatId)
                .status(WorkflowStatus.RUNNING)
                .createdAt(LocalDateTime.now())
                .build();
        instance.getContext().put("userMessage", userMessage);
        workflowRepository.save(instance);

        log.info("[DagWorkflowExecutor] start workflow={}, instance={}, nodes={}",
                dag.workflowId(), instance.getInstanceId(), dag.nodes().size());

        Set<String> completed = ConcurrentHashMap.newKeySet();
        Set<String> startedOrDone = ConcurrentHashMap.newKeySet();
        Set<String> artifactIds = ConcurrentHashMap.newKeySet();
        Map<String, ExpertOpinion> opinionsByNode = new ConcurrentHashMap<>();
        String finalAnswer = null;
        int stepCount = 0;

        try {
            while (completed.size() < dag.nodes().size()) {
                if (++stepCount > MAX_STEPS) {
                    throw new IllegalStateException("DAG exceeded MAX_STEPS=" + MAX_STEPS);
                }

                List<DagNodeSpec> ready = dag.readyNodes(completed, startedOrDone);
                if (ready.isEmpty()) {
                    throw new IllegalStateException(
                            "No ready nodes but graph incomplete; completed=" + completed);
                }

                for (DagNodeSpec n : ready) {
                    startedOrDone.add(n.id());
                }

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (DagNodeSpec node : ready) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        long start = System.currentTimeMillis();
                        progress.onNodeStarted(node);
                        WorkflowInstance.StepRecord record = WorkflowInstance.StepRecord.builder()
                                .nodeId(node.id())
                                .nodeName(node.taskDescription() != null ? node.taskDescription() : node.id())
                                .status(WorkflowStatus.RUNNING)
                                .startedAt(LocalDateTime.now())
                                .build();
                        try {
                            String resultText = executeNode(dag, node, conversationContext, userMessage,
                                    opinionsByNode, artifactIds);
                            long duration = System.currentTimeMillis() - start;
                            record.setStatus(WorkflowStatus.COMPLETED);
                            record.setResult(truncate(resultText, 500));
                            record.setCompletedAt(LocalDateTime.now());
                            record.setDurationMs(duration);
                            synchronized (instance) {
                                instance.getHistory().add(record);
                            }
                            completed.add(node.id());
                            boolean ok = node.type() != DagNodeType.AGENT
                                    || (opinionsByNode.get(node.id()) != null
                                    && opinionsByNode.get(node.id()).success());
                            progress.onNodeFinished(node, ok, duration);
                            if (node.type() == DagNodeType.SYNTHESIZE) {
                                synchronized (instance) {
                                    instance.getContext().put("finalAnswer", resultText);
                                }
                            }
                        } catch (Exception e) {
                            long duration = System.currentTimeMillis() - start;
                            log.error("[DagWorkflowExecutor] node {} failed: {}", node.id(), e.getMessage());
                            record.setStatus(WorkflowStatus.FAILED);
                            record.setResult("Error: " + e.getMessage());
                            record.setCompletedAt(LocalDateTime.now());
                            record.setDurationMs(duration);
                            synchronized (instance) {
                                instance.getHistory().add(record);
                            }
                            // Mark completed so dependents can decide; SYNTHESIZE may still run with partial opinions
                            if (node.type() == DagNodeType.AGENT) {
                                AgentIntent intent = toIntent(node.agentId());
                                opinionsByNode.put(node.id(),
                                        ExpertOpinion.failed(intent, e.getMessage(), duration));
                                completed.add(node.id());
                                progress.onNodeFinished(node, false, duration);
                                if (dag.failurePolicy() == FailurePolicy.FAIL_FAST
                                        || dag.failurePolicy() == FailurePolicy.RETRY_THEN_FAIL) {
                                    throw new DagAbortException("FAIL policy abort at node " + node.id(), e);
                                }
                            } else {
                                progress.onNodeFinished(node, false, duration);
                                throw new DagAbortException("SYNTHESIZE failed: " + e.getMessage(), e);
                            }
                        }
                    }, agentExecutor));
                }

                // Wait for this ready wave; unwrap DagAbortException
                for (CompletableFuture<Void> f : futures) {
                    try {
                        f.join();
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof DagAbortException abort) {
                            instance.setStatus(WorkflowStatus.FAILED);
                            workflowRepository.save(instance);
                            return new DagExecutionResult(
                                    instance.getInstanceId(), dag.workflowId(), WorkflowStatus.FAILED,
                                    null, List.copyOf(opinionsByNode.values()), abort.getMessage());
                        }
                        throw e;
                    }
                }
            }

            Object answerObj = instance.getContext().get("finalAnswer");
            finalAnswer = answerObj != null ? answerObj.toString() : "";
            if (!StringUtils.hasText(finalAnswer)) {
                // Fallback: merge successful opinions without synthesize node output
                List<ExpertOpinion> ok = opinionsByNode.values().stream()
                        .filter(ExpertOpinion::success)
                        .toList();
                finalAnswer = resultAggregator.synthesizeDebate(userMessage, ok);
            }

            instance.setStatus(WorkflowStatus.COMPLETED);
            instance.setUpdatedAt(LocalDateTime.now());
            workflowRepository.save(instance);

            log.info("[DagWorkflowExecutor] completed workflow={}, instance={}, answerChars={}",
                    dag.workflowId(), instance.getInstanceId(),
                    finalAnswer != null ? finalAnswer.length() : 0);

            return new DagExecutionResult(
                    instance.getInstanceId(),
                    dag.workflowId(),
                    WorkflowStatus.COMPLETED,
                    finalAnswer,
                    List.copyOf(opinionsByNode.values()),
                    null,
                    List.copyOf(artifactIds));
        } catch (DagAbortException e) {
            instance.setStatus(WorkflowStatus.FAILED);
            workflowRepository.save(instance);
            return new DagExecutionResult(
                    instance.getInstanceId(), dag.workflowId(), WorkflowStatus.FAILED,
                    null, List.copyOf(opinionsByNode.values()), e.getMessage());
        } catch (Exception e) {
            log.error("[DagWorkflowExecutor] unexpected failure: {}", e.getMessage(), e);
            instance.setStatus(WorkflowStatus.FAILED);
            workflowRepository.save(instance);
            return new DagExecutionResult(
                    instance.getInstanceId(), dag.workflowId(), WorkflowStatus.FAILED,
                    null, List.copyOf(opinionsByNode.values()), e.getMessage());
        }
    }

    private String executeNode(DagDefinition dag,
                               DagNodeSpec node,
                               ConversationContext conversationContext,
                               String userMessage,
                               Map<String, ExpertOpinion> opinionsByNode,
                               Set<String> artifactIds) {
        return switch (node.type()) {
            case AGENT -> executeAgent(
                    dag, node, conversationContext, userMessage, opinionsByNode, artifactIds);
            case SYNTHESIZE -> executeSynthesize(node, userMessage, opinionsByNode);
        };
    }

    private String executeAgent(DagDefinition dag,
                                DagNodeSpec node,
                                ConversationContext conversationContext,
                                String userMessage,
                                Map<String, ExpertOpinion> opinionsByNode,
                                Set<String> artifactIds) {
        AgentRunner runner = resolveRunner(node.agentId());
        if (runner == null) {
            throw new IllegalStateException("No AgentRunner for agentId=" + node.agentId());
        }
        AgentIntent intent = toIntent(node.agentId());
        String prompt = StringUtils.hasText(node.taskDescription())
                ? "【任务：" + node.taskDescription() + "】\n" + userMessage
                : userMessage;

        long start = System.currentTimeMillis();
        try {
            AgentOutput output = runner.run(conversationContext, prompt);
            addArtifactIds(output, artifactIds);
            long duration = System.currentTimeMillis() - start;
            String text = output != null && output.summary() != null ? output.summary() : "";
            opinionsByNode.put(node.id(), ExpertOpinion.ok(intent, text, duration));
            return text;
        } catch (Exception first) {
            if (dag.failurePolicy() == FailurePolicy.RETRY_THEN_SKIP
                    || dag.failurePolicy() == FailurePolicy.RETRY_THEN_FAIL) {
                try {
                    AgentOutput output = runner.run(conversationContext, prompt);
                    addArtifactIds(output, artifactIds);
                    long duration = System.currentTimeMillis() - start;
                    String text = output != null && output.summary() != null ? output.summary() : "";
                    opinionsByNode.put(node.id(), ExpertOpinion.ok(intent, text, duration));
                    return text;
                } catch (Exception retry) {
                    long duration = System.currentTimeMillis() - start;
                    if (dag.failurePolicy() == FailurePolicy.RETRY_THEN_FAIL) {
                        opinionsByNode.put(node.id(), ExpertOpinion.failed(intent, retry.getMessage(), duration));
                        throw retry;
                    }
                    // RETRY_THEN_SKIP: record failure opinion, return empty (node still "completed")
                    opinionsByNode.put(node.id(), ExpertOpinion.failed(intent, retry.getMessage(), duration));
                    log.warn("[DagWorkflowExecutor] agent {} skipped after retry: {}",
                            node.agentId(), retry.getMessage());
                    return "";
                }
            }
            if (dag.failurePolicy() == FailurePolicy.SKIP) {
                long duration = System.currentTimeMillis() - start;
                opinionsByNode.put(node.id(), ExpertOpinion.failed(intent, first.getMessage(), duration));
                return "";
            }
            throw first;
        }
    }

    private String executeSynthesize(DagNodeSpec node,
                                     String userMessage,
                                     Map<String, ExpertOpinion> opinionsByNode) {
        List<ExpertOpinion> upstream = new ArrayList<>();
        for (String dep : node.dependsOn()) {
            ExpertOpinion op = opinionsByNode.get(dep);
            if (op != null) {
                upstream.add(op);
            }
        }
        // Also include transitive agent opinions if deps are synthesize-only chains
        if (upstream.isEmpty()) {
            upstream.addAll(opinionsByNode.values());
        }
        String answer = resultAggregator.synthesizeDebate(userMessage, upstream);
        if (!StringUtils.hasText(answer)) {
            answer = "抱歉，本次工作流未能汇总出有效建议，请稍后重试或换一种问法。";
        }
        return answer;
    }

    private static AgentIntent toIntent(String agentId) {
        try {
            return AgentIntent.valueOf(agentId);
        } catch (Exception e) {
            return AgentIntent.GENERAL;
        }
    }

    private static void addArtifactIds(AgentOutput output, Set<String> artifactIds) {
        if (output != null && output.artifactIds() != null) {
            output.artifactIds().stream()
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(artifactIds::add);
        }
    }

    private AgentRunner resolveRunner(String agentId) {
        AgentRunner mapped = agentRunners.get(agentId);
        if (mapped != null) {
            return mapped;
        }
        return runnerRegistry == null ? null : runnerRegistry.get(agentId).orElse(null);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static final class DagAbortException extends RuntimeException {
        DagAbortException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
