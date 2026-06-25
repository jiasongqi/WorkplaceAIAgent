package com.yupi.yuaiagent.access;

import lombok.Builder;
import lombok.Data;

/**
 * 访问决策上下文 — 封装一次 Tool 调用涉及全部安全维度。
 * <p>
 * 由 {@link AccessDecisionService} 传给各 {@link AccessVoter} 进行投票。
 *
 * @author jsq
 */
@Data
@Builder
public class AccessDecisionContext {

    /**
     * 用户 ID（可为 null，匿名场景）
     */
    private String userId;

    /**
     * Agent 编码（如 resume-agent）
     */
    private String agentCode;

    /**
     * Tool 名称（如 resume.optimize）
     */
    private String toolName;

    /**
     * MCP Server ID（非 MCP Tool 时为 null）
     */
    private String mcpServerId;

    /**
     * 当前请求已调用的 Tool 次数（用于 Quota 检查）
     */
    @Builder.Default
    private int currentToolCallCount = 0;

    /**
     * 请求 ID（用于审计关联）
     */
    private String requestId;
}
