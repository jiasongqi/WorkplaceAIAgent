package com.yupi.yuaiagent.permission;

import com.yupi.yuaiagent.common.ResultCode;
import com.yupi.yuaiagent.exception.BusinessException;

/**
 * Agent 权限拒绝异常 — 当 Agent 尝试调用其无权限的 Tool 时抛出。
 *
 * @author jsq
 */
public class AgentPermissionDeniedException extends BusinessException {

    private final String agentCode;
    private final String toolName;

    public AgentPermissionDeniedException(String message, String agentCode, String toolName) {
        super(ResultCode.FORBIDDEN, message);
        this.agentCode = agentCode;
        this.toolName = toolName;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getToolName() {
        return toolName;
    }
}
