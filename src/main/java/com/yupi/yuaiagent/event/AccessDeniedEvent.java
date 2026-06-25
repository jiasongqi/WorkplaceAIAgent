package com.yupi.yuaiagent.event;

/**
 * 权限拒绝事件
 *
 * @author jsq
 */
public class AccessDeniedEvent extends GovernanceEvent {

    private final String agentCode;
    private final String toolName;
    private final String reason;

    public AccessDeniedEvent(Object source, String userId, String agentCode, String toolName, String reason) {
        super(source, "ACCESS_DENIED", userId);
        this.agentCode = agentCode;
        this.toolName = toolName;
        this.reason = reason;
    }

    public String getAgentCode() { return agentCode; }
    public String getToolName() { return toolName; }
    public String getReason() { return reason; }
}
