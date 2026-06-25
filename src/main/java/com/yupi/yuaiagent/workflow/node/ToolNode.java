package com.yupi.yuaiagent.workflow.node;

/**
 * Tool 节点 — 直接调用指定 Tool。
 *
 * @author jsq
 */
public class ToolNode extends WorkflowNode {

    /** Tool 名称（如 web.search） */
    private String toolName;
    /** Tool 参数（JSON 字符串或变量引用） */
    private String toolArgs;

    public ToolNode() { super(null, null, "tool"); }

    public ToolNode(String id, String name, String toolName) {
        super(id, name, "tool");
        this.toolName = toolName;
    }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getToolArgs() { return toolArgs; }
    public void setToolArgs(String toolArgs) { this.toolArgs = toolArgs; }
}
