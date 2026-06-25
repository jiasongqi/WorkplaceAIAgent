package com.yupi.yuaiagent.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * MCP Server 画像 — 包含 Server 的身份信息、来源、信任等级和权限边界。
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerProfile {

    /**
     * Server 唯一标识
     */
    private String serverId;

    /**
     * Server 名称
     */
    private String name;

    /**
     * 信任等级
     */
    @Builder.Default
    private McpTrustLevel trustLevel = McpTrustLevel.PRIVATE;

    /**
     * 来源：OFFICIAL / THIRD_PARTY / USER_UPLOADED
     */
    @Builder.Default
    private String source = "USER_UPLOADED";

    /**
     * 额外允许的 Tool 命名空间（覆盖 trustLevel 的默认值）
     */
    private Set<String> extraAllowedNamespaces;

    /**
     * 注册时间
     */
    private LocalDateTime registeredAt;

    /**
     * 描述
     */
    private String description;

    /**
     * 是否已启用
     */
    @Builder.Default
    private boolean enabled = true;
}
