package com.yupi.yuaiagent.workflow.node;

/**
 * Agent 节点 — 委托给指定 Agent 执行任务。
 *
 * @author jsq
 */
public class AgentNode extends WorkflowNode {

    /** 目标 Agent 编码 */
    private String agentCode;
    /** 传递给 Agent 的任务描述 */
    private String taskDescription;
    /** 输入变量引用 */
    private String inputRef;

    public AgentNode() { super(null, null, "agent"); }

    public AgentNode(String id, String name, String agentCode, String taskDescription) {
        super(id, name, "agent");
        this.agentCode = agentCode;
        this.taskDescription = taskDescription;
    }

    public String getAgentCode() { return agentCode; }
    public void setAgentCode(String agentCode) { this.agentCode = agentCode; }
    public String getTaskDescription() { return taskDescription; }
    public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }
    public String getInputRef() { return inputRef; }
    public void setInputRef(String inputRef) { this.inputRef = inputRef; }
}
