package com.yupi.yuaiagent.permission;

import com.yupi.yuaiagent.permission.model.PermissionProfile;
import com.yupi.yuaiagent.sessionstate.HandoffScopeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Set;

/**
 * Agent 权限校验服务 — 基于配置驱动的 PermissionProfile 进行 Tool 调用权限校验。
 * <p>
 * 支持通配符匹配（{@code resume.*} 匹配 {@code resume.optimize}）和精确匹配。
 * Admin 画像跳过所有检查。
 * <p>
 * 当 {@link HandoffScopeContext} 激活时，额外与移交 Packet.scope 求交，防止幽灵权限。
 *
 * @author jsq
 */
@Slf4j
@Service
public class AgentPermissionService {

    @Resource
    private PermissionProfileRegistry registry;

    /**
     * 检查 Agent 是否有权限调用指定 Tool
     *
     * @param agentCode Agent 编码
     * @param toolName  Tool 名称（如 {@code resume.optimize}）
     * @return true 表示有权限
     */
    public boolean checkPermission(String agentCode, String toolName) {
        PermissionProfile profile = registry.getOrDefault(agentCode);

        // Admin 跳过所有检查
        if (profile.isAdmin()) {
            log.debug("[Permission] ADMIN agent={} tool={} -> ALLOWED", agentCode, toolName);
            return true;
        }

        boolean profileAllows = false;
        for (String pattern : profile.getAllowedToolPatterns()) {
            if (matchToolPattern(pattern, toolName)) {
                profileAllows = true;
                break;
            }
        }
        if (!profileAllows) {
            log.warn("[Permission] agent={} tool={} -> DENIED (no matching pattern)", agentCode, toolName);
            return false;
        }

        // Handoff scope downgrade: must also match Packet.scope when installed
        if (HandoffScopeContext.isActive()) {
            Set<String> scope = HandoffScopeContext.current();
            boolean inScope = false;
            for (String pattern : scope) {
                if (matchToolPattern(pattern, toolName)) {
                    inScope = true;
                    break;
                }
            }
            if (!inScope) {
                log.warn("[Permission] agent={} tool={} -> DENIED (outside handoff scope {})",
                        agentCode, toolName, scope);
                return false;
            }
        }

        log.debug("[Permission] agent={} tool={} -> ALLOWED", agentCode, toolName);
        return true;
    }

    /**
     * 检查权限，无权限时抛出 {@link AgentPermissionDeniedException}
     */
    public void checkPermissionOrThrow(String agentCode, String toolName) {
        if (!checkPermission(agentCode, toolName)) {
            PermissionProfile profile = registry.getOrDefault(agentCode);
            throw new AgentPermissionDeniedException(
                    String.format("Agent [%s] 无权调用 Tool [%s]", profile.getDisplayName(), toolName),
                    agentCode, toolName);
        }
    }

    /**
     * 检查 Agent 是否有权限访问文件系统
     */
    public boolean canAccessFilesystem(String agentCode) {
        PermissionProfile profile = registry.getOrDefault(agentCode);
        return profile.isAdmin() || profile.isFilesystemAccess();
    }

    /**
     * 检查 Agent 是否有权限访问外部网络
     */
    public boolean canAccessNetwork(String agentCode) {
        PermissionProfile profile = registry.getOrDefault(agentCode);
        return profile.isAdmin() || profile.isNetworkAccess();
    }

    /**
     * 检查 Agent 单次请求的 Tool 调用次数是否超限
     */
    public boolean isWithinToolCallLimit(String agentCode, int currentCallCount) {
        PermissionProfile profile = registry.getOrDefault(agentCode);
        if (profile.isAdmin()) {
            return true;
        }
        return currentCallCount < profile.getMaxToolCallsPerRequest();
    }

    /**
     * 获取 Agent 的最低 MCP 信任分要求
     */
    public int getMinMcpTrustScore(String agentCode) {
        return registry.getOrDefault(agentCode).getMinMcpTrustScore();
    }

    /**
     * 匹配 Tool 模式（支持通配符 *）。
     * <ul>
     *     <li>{@code *} — 匹配所有</li>
     *     <li>{@code resume.*} — 匹配 resume 命名空间下所有 Tool</li>
     *     <li>{@code rag.query} — 精确匹配</li>
     * </ul>
     */
    private boolean matchToolPattern(String pattern, String toolName) {
        if ("*".equals(pattern)) {
            return true;
        }
        if (pattern.endsWith(".*")) {
            String namespace = pattern.substring(0, pattern.length() - 2);
            return toolName.startsWith(namespace + ".");
        }
        return pattern.equals(toolName);
    }
}
