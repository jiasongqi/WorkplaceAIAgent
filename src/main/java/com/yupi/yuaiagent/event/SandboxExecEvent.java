package com.yupi.yuaiagent.event;

/**
 * 沙箱执行事件
 *
 * @author jsq
 */
public class SandboxExecEvent extends GovernanceEvent {

    private final String agentCode;
    private final String command;
    private final boolean success;
    private final long durationMs;

    public SandboxExecEvent(Object source, String userId, String agentCode,
                             String command, boolean success, long durationMs) {
        super(source, "SANDBOX_EXEC", userId);
        this.agentCode = agentCode;
        this.command = command;
        this.success = success;
        this.durationMs = durationMs;
    }

    public String getAgentCode() { return agentCode; }
    public String getCommand() { return command; }
    public boolean isSuccess() { return success; }
    public long getDurationMs() { return durationMs; }
}
