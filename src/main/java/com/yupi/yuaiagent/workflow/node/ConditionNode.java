package com.yupi.yuaiagent.workflow.node;

import java.util.List;

/**
 * 条件节点 — 根据条件表达式选择分支执行。
 *
 * @author jsq
 */
public class ConditionNode extends WorkflowNode {

    /** 条件表达式（SpEL 或简单 key==value） */
    private String expression;
    /** 条件为 true 时执行的节点 ID */
    private String trueBranch;
    /** 条件为 false 时执行的节点 ID */
    private String falseBranch;

    public ConditionNode() { super(null, null, "condition"); }

    public ConditionNode(String id, String name, String expression, String trueBranch, String falseBranch) {
        super(id, name, "condition");
        this.expression = expression;
        this.trueBranch = trueBranch;
        this.falseBranch = falseBranch;
    }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getTrueBranch() { return trueBranch; }
    public void setTrueBranch(String trueBranch) { this.trueBranch = trueBranch; }
    public String getFalseBranch() { return falseBranch; }
    public void setFalseBranch(String falseBranch) { this.falseBranch = falseBranch; }
}
