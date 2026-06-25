package com.yupi.yuaiagent.access;

import com.yupi.yuaiagent.permission.AgentPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 权限投票器 — 基于 PermissionProfile 判断 Agent 是否有权调用 Tool。
 *
 * @author jsq
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPolicyVoter implements AccessVoter {

    private final AgentPermissionService agentPermissionService;

    @Override
    public Vote vote(AccessDecisionContext context) {
        if (context.getAgentCode() == null || context.getToolName() == null) {
            return Vote.ABSTAIN;
        }

        boolean allowed = agentPermissionService.checkPermission(
                context.getAgentCode(), context.getToolName());

        if (allowed) {
            log.debug("[AgentPolicyVoter] agent={} tool={} -> ALLOW",
                    context.getAgentCode(), context.getToolName());
            return Vote.ALLOW;
        } else {
            log.warn("[AgentPolicyVoter] agent={} tool={} -> DENY",
                    context.getAgentCode(), context.getToolName());
            return Vote.DENY;
        }
    }

    @Override
    public String getName() {
        return "AgentPolicyVoter";
    }
}
