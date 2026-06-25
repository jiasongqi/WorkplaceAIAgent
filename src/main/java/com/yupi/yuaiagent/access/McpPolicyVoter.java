package com.yupi.yuaiagent.access;

import com.yupi.yuaiagent.mcp.McpTrustService;
import com.yupi.yuaiagent.permission.AgentPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP 信任投票器 — 基于 McpTrustLevel 判断 MCP Server 是否有权提供 Tool。
 * <p>
 * 仅当 context 中包含 mcpServerId 时参与投票，否则弃权。
 *
 * @author jsq
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpPolicyVoter implements AccessVoter {

    private final McpTrustService mcpTrustService;
    private final AgentPermissionService agentPermissionService;

    @Override
    public Vote vote(AccessDecisionContext context) {
        // 非 MCP Tool，弃权
        if (context.getMcpServerId() == null || context.getMcpServerId().isBlank()) {
            return Vote.ABSTAIN;
        }

        String serverId = context.getMcpServerId();
        String toolName = context.getToolName();

        // 1. 检查 MCP Server 是否允许提供该 Tool
        if (!mcpTrustService.checkMcpPermission(serverId, toolName)) {
            log.warn("[McpPolicyVoter] server={} tool={} -> DENY (MCP trust check failed)",
                    serverId, toolName);
            return Vote.DENY;
        }

        // 2. 检查 MCP 信任分是否满足 Agent 的最低要求
        if (context.getAgentCode() != null) {
            int minScore = agentPermissionService.getMinMcpTrustScore(context.getAgentCode());
            if (!mcpTrustService.meetsMinTrustScore(serverId, minScore)) {
                log.warn("[McpPolicyVoter] server={} agent={} minScore={} -> DENY (trust score too low)",
                        serverId, context.getAgentCode(), minScore);
                return Vote.DENY;
            }
        }

        log.debug("[McpPolicyVoter] server={} tool={} -> ALLOW", serverId, toolName);
        return Vote.ALLOW;
    }

    @Override
    public String getName() {
        return "McpPolicyVoter";
    }
}
