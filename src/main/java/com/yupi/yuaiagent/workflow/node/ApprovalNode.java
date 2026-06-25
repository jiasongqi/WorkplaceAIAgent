package com.yupi.yuaiagent.workflow.node;

/**
 * 人工审批节点 — 暂停工作流等待人工确认后继续。
 *
 * @author jsq
 */
public class ApprovalNode extends WorkflowNode {

    /** 审批说明 */
    private String approvalMessage;
    /** 审批超时（秒），超时后自动拒绝 */
    private int timeoutSeconds = 86400; // 默认 24 小时

    public ApprovalNode() { super(null, null, "approval"); }

    public ApprovalNode(String id, String name, String approvalMessage) {
        super(id, name, "approval");
        this.approvalMessage = approvalMessage;
    }

    public String getApprovalMessage() { return approvalMessage; }
    public void setApprovalMessage(String approvalMessage) { this.approvalMessage = approvalMessage; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
