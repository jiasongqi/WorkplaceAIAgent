package com.yupi.yuaiagent.access;

import com.yupi.yuaiagent.permission.AgentPermissionDeniedException;
import com.yupi.yuaiagent.permission.PermissionAuditLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified access decision service — aggregates votes from all {@link AccessVoter}s.
 *
 * <p>Decision strategy: one-vote-deny (any DENY → reject), all-abstain → reject (default secure).</p>
 *
 * <p><b>SECURITY WARNING: Do NOT add caching (Caffeine/Guava) to this service without implementing
 * cache eviction on permission change events.</b> A stale ALLOW in cache after permission revocation
 * creates a security vulnerability. If caching is needed for performance, subscribe to
 * {@code PermissionChangedEvent} and evict the affected entries immediately.</p>
 *
 * @author jsq
 */
@Slf4j
@Service
public class AccessDecisionService {

    @Resource
    private List<AccessVoter> voters;

    @Resource
    private PermissionAuditLog auditLog;

    /**
     * 检查是否允许访问（返回 boolean）
     *
     * @param context 访问决策上下文
     * @return true 表示允许
     */
    public boolean check(AccessDecisionContext context) {
        if (voters == null || voters.isEmpty()) {
            log.warn("[AccessDecision] no voters registered, defaulting to DENY");
            return false;
        }

        boolean hasAllow = false;
        StringBuilder denyReasons = new StringBuilder();

        for (AccessVoter voter : voters) {
            AccessVoter.Vote vote = voter.vote(context);
            log.debug("[AccessDecision] voter={} vote={} agent={} tool={}",
                    voter.getName(), vote, context.getAgentCode(), context.getToolName());

            if (vote == AccessVoter.Vote.DENY) {
                String reason = voter.getName() + " denied";
                auditLog.recordDenied(context.getAgentCode(), context.getToolName(), reason);
                log.warn("[AccessDecision] DENIED by {} for agent={} tool={}",
                        voter.getName(), context.getAgentCode(), context.getToolName());
                return false; // 一票否决
            }
            if (vote == AccessVoter.Vote.ALLOW) {
                hasAllow = true;
            }
        }

        if (hasAllow) {
            auditLog.recordAllowed(context.getAgentCode(), context.getToolName());
            return true;
        }

        // 全部弃权 = 默认拒绝
        log.warn("[AccessDecision] all voters abstained for agent={} tool={} -> DENY (default secure)",
                context.getAgentCode(), context.getToolName());
        auditLog.recordDenied(context.getAgentCode(), context.getToolName(),
                "All voters abstained (default deny)");
        return false;
    }

    /**
     * 检查访问权限，无权限时抛出 {@link AgentPermissionDeniedException}
     */
    public void checkOrThrow(AccessDecisionContext context) {
        if (!check(context)) {
            throw new AgentPermissionDeniedException(
                    String.format("访问被拒绝: Agent [%s] 无权调用 Tool [%s]",
                            context.getAgentCode(), context.getToolName()),
                    context.getAgentCode(),
                    context.getToolName());
        }
    }

    /**
     * 便捷方法：快速检查 Agent 调用 Tool 的权限（非 MCP 场景）
     */
    public boolean checkAgentTool(String agentCode, String toolName) {
        return check(AccessDecisionContext.builder()
                .agentCode(agentCode)
                .toolName(toolName)
                .build());
    }

    /**
     * 便捷方法：快速检查 Agent 通过 MCP Server 调用 Tool 的权限
     */
    public boolean checkAgentMcpTool(String agentCode, String mcpServerId, String toolName) {
        return check(AccessDecisionContext.builder()
                .agentCode(agentCode)
                .mcpServerId(mcpServerId)
                .toolName(toolName)
                .build());
    }

    /**
     * 获取已注册的 Voter 列表（用于管理和调试）
     */
    public List<String> getVoterNames() {
        return voters.stream()
                .map(AccessVoter::getName)
                .collect(Collectors.toList());
    }
}
