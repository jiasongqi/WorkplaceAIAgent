package com.yupi.yuaiagent.access;

import com.yupi.yuaiagent.permission.AgentPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 配额投票器 — 检查 Agent 单次请求的 Tool 调用次数是否超限。
 *
 * @author jsq
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaPolicyVoter implements AccessVoter {

    private final AgentPermissionService agentPermissionService;

    @Override
    public Vote vote(AccessDecisionContext context) {
        if (context.getAgentCode() == null) {
            return Vote.ABSTAIN;
        }

        boolean withinLimit = agentPermissionService.isWithinToolCallLimit(
                context.getAgentCode(), context.getCurrentToolCallCount());

        if (withinLimit) {
            return Vote.ALLOW;
        } else {
            log.warn("[QuotaPolicyVoter] agent={} callCount={} -> DENY (quota exceeded)",
                    context.getAgentCode(), context.getCurrentToolCallCount());
            return Vote.DENY;
        }
    }

    @Override
    public String getName() {
        return "QuotaPolicyVoter";
    }
}
