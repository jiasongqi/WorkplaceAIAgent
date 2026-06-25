package com.yupi.yuaiagent.mcp;

import java.util.Collections;
import java.util.Set;

/**
 * MCP Server 信任等级枚举 — 控制不同来源 MCP 的权限边界。
 * <p>
 * 等级越高，可访问的 Tool 命名空间越广。
 * 与 Agent 权限（PermissionProfile）独立，二者通过 AccessDecisionService 联合决策。
 *
 * @author jsq
 */
public enum McpTrustLevel {

    /**
     * 官方认证 — 全部权限（trustScore=100）
     */
    VERIFIED(100, "官方认证", Set.of("*")),

    /**
     * 合作伙伴 — 受限权限（trustScore=70）
     */
    PARTNER(70, "合作伙伴", Set.of("data.*", "web.*", "rag.*")),

    /**
     * 社区上传 — 仅公开 Tool（trustScore=30）
     */
    COMMUNITY(30, "社区上传", Set.of("public.*")),

    /**
     * 私有/未审核 — 禁止访问敏感 Tool（trustScore=0）
     */
    PRIVATE(0, "私有/未审核", Collections.emptySet());

    private final int trustScore;
    private final String description;
    private final Set<String> allowedToolNamespaces;

    McpTrustLevel(int trustScore, String description, Set<String> allowedToolNamespaces) {
        this.trustScore = trustScore;
        this.description = description;
        this.allowedToolNamespaces = allowedToolNamespaces;
    }

    public int getTrustScore() {
        return trustScore;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getAllowedToolNamespaces() {
        return allowedToolNamespaces;
    }

    /**
     * 判断该信任等级是否允许访问指定 Tool 命名空间
     */
    public boolean allowsTool(String toolName) {
        for (String ns : allowedToolNamespaces) {
            if ("*".equals(ns)) {
                return true;
            }
            if (ns.endsWith(".*")) {
                String prefix = ns.substring(0, ns.length() - 2);
                if (toolName.startsWith(prefix + ".")) {
                    return true;
                }
            }
            if (ns.equals(toolName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按信任分获取对应等级（向下取整）
     */
    public static McpTrustLevel fromTrustScore(int score) {
        if (score >= VERIFIED.trustScore) return VERIFIED;
        if (score >= PARTNER.trustScore) return PARTNER;
        if (score >= COMMUNITY.trustScore) return COMMUNITY;
        return PRIVATE;
    }
}
