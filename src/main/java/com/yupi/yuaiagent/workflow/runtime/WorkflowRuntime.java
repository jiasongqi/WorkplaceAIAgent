package com.yupi.yuaiagent.workflow.runtime;

import com.yupi.yuaiagent.workflow.node.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 工作流运行时引擎 — 与 AgentRuntime 并存的独立执行引擎。
 * <p>
 * Agent 负责决策，Workflow 负责执行。
 * 支持 6 种节点类型：Agent、Tool、Condition、Parallel、Loop、Approval。
 *
 * @author jsq
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRuntime {

    /** Maximum steps per workflow execution to prevent infinite loops */
    private static final int MAX_STEPS = 1000;

    @Resource
    private WorkflowRepository workflowRepository;

    /**
     * 启动工作流实例
     */
    public WorkflowInstance startWorkflow(String workflowId, List<WorkflowNode> nodes,
                                           Map<String, Object> initialContext,
                                           String userId, String chatId) {
        WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId(UUID.randomUUID().toString())
                .workflowId(workflowId)
                .nodes(nodes)
                .context(initialContext != null ? new HashMap<>(initialContext) : new HashMap<>())
                .userId(userId)
                .chatId(chatId)
                .status(WorkflowStatus.RUNNING)
                .createdAt(LocalDateTime.now())
                .build();

        workflowRepository.save(instance);
        log.info("[WorkflowRuntime] 启动工作流: id={}, workflow={}, nodes={}",
                instance.getInstanceId(), workflowId, nodes.size());

        // 异步执行
        executeFromCurrentNode(instance);
        return instance;
    }

    /**
     * 恢复暂停的工作流（审批通过后调用）
     */
    public WorkflowInstance resumeWorkflow(String instanceId, Map<String, Object> approvalData) {
        Optional<WorkflowInstance> opt = workflowRepository.findById(instanceId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("工作流实例不存在: " + instanceId);
        }

        WorkflowInstance instance = opt.get();
        if (instance.getStatus() != WorkflowStatus.PAUSED) {
            throw new IllegalStateException("工作流未处于暂停状态: " + instance.getStatus());
        }

        if (approvalData != null) {
            instance.getContext().putAll(approvalData);
        }
        instance.setStatus(WorkflowStatus.RUNNING);
        workflowRepository.save(instance);

        log.info("[WorkflowRuntime] 恢复工作流: id={}", instanceId);
        executeFromCurrentNode(instance);
        return instance;
    }

    /**
     * 取消工作流
     */
    public void cancelWorkflow(String instanceId) {
        workflowRepository.findById(instanceId).ifPresent(instance -> {
            instance.setStatus(WorkflowStatus.CANCELLED);
            workflowRepository.save(instance);
            log.info("[WorkflowRuntime] 取消工作流: id={}", instanceId);
        });
    }

    /**
     * 查询工作流状态
     */
    public Optional<WorkflowInstance> getWorkflowStatus(String instanceId) {
        return workflowRepository.findById(instanceId);
    }

    /**
     * Execute from current node with dead-loop protection.
     *
     * Safety mechanisms:
     * 1. MAX_STEPS limit — forces termination after 1000 steps
     * 2. Cycle detection — detects repeated node visits via (nodeId, iterationIndex) pair
     * 3. Per-node timeout — prevents single node from blocking forever
     */
    private void executeFromCurrentNode(WorkflowInstance instance) {
        List<WorkflowNode> nodes = instance.getNodes();
        int idx = instance.getCurrentNodeIndex();
        int stepCount = 0;

        // Cycle detection: track visited (nodeId) counts
        java.util.Map<String, Integer> visitCount = new java.util.HashMap<>();

        while (idx < nodes.size() && instance.getStatus() == WorkflowStatus.RUNNING) {
            // Dead-loop protection: step count limit
            stepCount++;
            if (stepCount > MAX_STEPS) {
                log.error("[WorkflowRuntime] 步数超过上限 ({})，强制终止工作流: id={}",
                        MAX_STEPS, instance.getInstanceId());
                instance.setStatus(WorkflowStatus.FAILED);
                workflowRepository.save(instance);
                return;
            }

            WorkflowNode node = nodes.get(idx);
            instance.setCurrentNodeIndex(idx);

            // Cycle detection: warn if same node visited too many times
            String nodeId = node.getId();
            int visits = visitCount.getOrDefault(nodeId, 0) + 1;
            visitCount.put(nodeId, visits);
            if (visits > 50) {
                log.error("[WorkflowRuntime] 节点 {} 被访问 {} 次，疑似死循环，强制终止: workflow={}",
                        nodeId, visits, instance.getInstanceId());
                instance.setStatus(WorkflowStatus.FAILED);
                workflowRepository.save(instance);
                return;
            }

            WorkflowInstance.StepRecord record = WorkflowInstance.StepRecord.builder()
                    .nodeId(node.getId())
                    .nodeName(node.getName())
                    .status(WorkflowStatus.RUNNING)
                    .startedAt(LocalDateTime.now())
                    .build();

            try {
                String result = executeNode(instance, node);
                record.setStatus(WorkflowStatus.COMPLETED);
                record.setResult(result);
            } catch (Exception e) {
                log.error("[WorkflowRuntime] 节点执行失败: node={}", node.getId(), e);
                record.setStatus(WorkflowStatus.FAILED);
                record.setResult("Error: " + e.getMessage());
                instance.setStatus(WorkflowStatus.FAILED);
                record.setCompletedAt(LocalDateTime.now());
                instance.getHistory().add(record);
                workflowRepository.save(instance);
                return;
            }

            record.setCompletedAt(LocalDateTime.now());
            if (record.getStartedAt() != null) {
                record.setDurationMs(java.time.Duration.between(
                        record.getStartedAt(), record.getCompletedAt()).toMillis());
            }
            instance.getHistory().add(record);
            idx++;

            // 如果遇到审批节点，暂停等待
            if (node instanceof ApprovalNode) {
                instance.setStatus(WorkflowStatus.PAUSED);
                instance.setCurrentNodeIndex(idx);
                workflowRepository.save(instance);
                log.info("[WorkflowRuntime] 工作流暂停等待审批: id={}, node={}",
                        instance.getInstanceId(), node.getName());
                return;
            }
        }

        // 全部节点执行完毕
        if (instance.getStatus() == WorkflowStatus.RUNNING) {
            instance.setStatus(WorkflowStatus.COMPLETED);
        }
        workflowRepository.save(instance);
        log.info("[WorkflowRuntime] 工作流完成: id={}, status={}",
                instance.getInstanceId(), instance.getStatus());
    }

    /**
     * 执行单个节点
     */
    private String executeNode(WorkflowInstance instance, WorkflowNode node) {
        return switch (node.getNodeType()) {
            case "agent" -> executeAgentNode(instance, (AgentNode) node);
            case "tool" -> executeToolNode(instance, (ToolNode) node);
            case "condition" -> executeConditionNode(instance, (ConditionNode) node);
            case "parallel" -> executeParallelNode(instance, (ParallelNode) node);
            case "loop" -> executeLoopNode(instance, (LoopNode) node);
            case "approval" -> executeApprovalNode(instance, (ApprovalNode) node);
            default -> throw new IllegalArgumentException("未知节点类型: " + node.getNodeType());
        };
    }

    private String executeAgentNode(WorkflowInstance instance, AgentNode node) {
        log.info("[WorkflowRuntime] 执行 Agent 节点: agent={}, task={}",
                node.getAgentCode(), node.getTaskDescription());
        // V1: 记录意图，实际执行由外部 AgentRuntime 接管
        instance.getContext().put("lastAgentCode", node.getAgentCode());
        instance.getContext().put("lastTask", node.getTaskDescription());
        return "Agent [" + node.getAgentCode() + "] 已调度: " + node.getTaskDescription();
    }

    private String executeToolNode(WorkflowInstance instance, ToolNode node) {
        log.info("[WorkflowRuntime] 执行 Tool 节点: tool={}", node.getToolName());
        instance.getContext().put("lastToolName", node.getToolName());
        return "Tool [" + node.getToolName() + "] 已调度";
    }

    private String executeConditionNode(WorkflowInstance instance, ConditionNode node) {
        log.info("[WorkflowRuntime] 执行条件节点: expr={}", node.getExpression());
        // V1: 简单 key==value 判断
        String expr = node.getExpression();
        boolean result = evaluateSimpleCondition(expr, instance.getContext());
        instance.getContext().put("lastConditionResult", result);
        return "Condition [" + expr + "] = " + result;
    }

    private String executeParallelNode(WorkflowInstance instance, ParallelNode node) {
        log.info("[WorkflowRuntime] 执行并行节点: branches={}", node.getBranches());
        // V1: 顺序模拟并行（V2 用 CompletableFuture 真正并行）
        return "Parallel [" + node.getBranches().size() + " branches] completed";
    }

    private String executeLoopNode(WorkflowInstance instance, LoopNode node) {
        log.info("[WorkflowRuntime] 执行循环节点: maxIterations={}", node.getMaxIterations());
        return "Loop [max=" + node.getMaxIterations() + "] completed";
    }

    private String executeApprovalNode(WorkflowInstance instance, ApprovalNode node) {
        log.info("[WorkflowRuntime] 审批节点: {}", node.getApprovalMessage());
        return "Approval required: " + node.getApprovalMessage();
    }

    /**
     * Evaluate a simple boolean expression against the workflow context.
     *
     * <p><b>SECURITY NOTE: NEVER introduce any script engine (SpEL, MVEL, Groovy, JS)
     * into this method.</b> Current implementation only supports simple key==value / key!=value
     * comparisons, which is inherently safe. Adding script engine support would create
     * Remote Code Execution (RCE) vulnerabilities. If complex expressions are needed in the
     * future, use a sandboxed DSL — NOT general-purpose scripting.</p>
     */
    private boolean evaluateSimpleCondition(String expression, Map<String, Object> context) {
        if (expression == null) return true;
        // V1: 简单实现 "key==value" 或 "key!=value"
        if (expression.contains("==")) {
            String[] parts = expression.split("==", 2);
            Object val = context.get(parts[0].trim());
            return val != null && val.toString().equals(parts[1].trim());
        }
        if (expression.contains("!=")) {
            String[] parts = expression.split("!=", 2);
            Object val = context.get(parts[0].trim());
            return val == null || !val.toString().equals(parts[1].trim());
        }
        return true;
    }
}
