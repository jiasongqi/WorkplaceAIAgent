package com.yupi.yuaiagent.workflow.node;

/**
 * 循环节点 — 重复执行子节点直到条件满足或达到最大次数。
 *
 * @author jsq
 */
public class LoopNode extends WorkflowNode {

    /** 循环体节点 ID */
    private String bodyNodeId;
    /** 退出条件表达式 */
    private String exitCondition;
    /** 最大循环次数 */
    private int maxIterations = 10;

    public LoopNode() { super(null, null, "loop"); }

    public LoopNode(String id, String name, String bodyNodeId, int maxIterations) {
        super(id, name, "loop");
        this.bodyNodeId = bodyNodeId;
        this.maxIterations = maxIterations;
    }

    public String getBodyNodeId() { return bodyNodeId; }
    public void setBodyNodeId(String bodyNodeId) { this.bodyNodeId = bodyNodeId; }
    public String getExitCondition() { return exitCondition; }
    public void setExitCondition(String exitCondition) { this.exitCondition = exitCondition; }
    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
}
