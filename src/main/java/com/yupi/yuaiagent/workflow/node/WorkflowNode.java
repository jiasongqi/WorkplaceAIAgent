package com.yupi.yuaiagent.workflow.node;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 工作流节点基类 — 所有节点类型的统一抽象。
 * <p>
 * 支持 6 种节点类型：
 * <ul>
 *     <li>{@link AgentNode} — 委托给 Agent 执行</li>
 *     <li>{@link ToolNode} — 直接调用 Tool</li>
 *     <li>{@link ConditionNode} — 条件分支</li>
 *     <li>{@link ParallelNode} — 并行执行</li>
 *     <li>{@link LoopNode} — 循环执行</li>
 *     <li>{@link ApprovalNode} — 人工审批</li>
 * </ul>
 *
 * @author jsq
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "nodeType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentNode.class, name = "agent"),
        @JsonSubTypes.Type(value = ToolNode.class, name = "tool"),
        @JsonSubTypes.Type(value = ConditionNode.class, name = "condition"),
        @JsonSubTypes.Type(value = ParallelNode.class, name = "parallel"),
        @JsonSubTypes.Type(value = LoopNode.class, name = "loop"),
        @JsonSubTypes.Type(value = ApprovalNode.class, name = "approval")
})
public abstract class WorkflowNode {

    /**
     * 节点唯一 ID
     */
    private String id;

    /**
     * 节点名称
     */
    private String name;

    /**
     * 节点类型标识
     */
    private String nodeType;

    public WorkflowNode() {}

    public WorkflowNode(String id, String name, String nodeType) {
        this.id = id;
        this.name = name;
        this.nodeType = nodeType;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
}
