package com.yupi.yuaiagent.access;

/**
 * 访问投票器接口 — 类似 Spring Security 的 AccessDecisionVoter。
 * <p>
 * 每个 Voter 负责评估一个安全维度（Agent 权限、MCP 信任、Quota 配额等），
 * 由 {@link AccessDecisionService} 汇总投票结果做最终决策。
 *
 * @author jsq
 */
public interface AccessVoter {

    /**
     * 投票结果
     */
    enum Vote {
        /** 允许 */
        ALLOW,
        /** 拒绝 */
        DENY,
        /** 弃权（本 Voter 不关心此决策） */
        ABSTAIN
    }

    /**
     * 对访问请求进行投票
     *
     * @param context 访问决策上下文
     * @return 投票结果
     */
    Vote vote(AccessDecisionContext context);

    /**
     * 获取投票器名称（用于日志和审计）
     */
    String getName();
}
