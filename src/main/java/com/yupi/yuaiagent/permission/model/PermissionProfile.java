package com.yupi.yuaiagent.permission.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Set;

/**
 * Agent 权限画像（配置驱动，非枚举硬编码）。
 * <p>
 * 从 {@code resources/permissions/*.yaml} 加载，支持运行时动态注册。
 * 新增 Agent 只需新增 YAML 文件即可上线，无需修改代码或重启。
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionProfile {

    /**
     * Agent 编码（全局唯一，如 resume-agent、negotiation-agent）
     */
    private String agentCode;

    /**
     * 可读名称
     */
    private String displayName;

    /**
     * 允许的 Tool 命名空间模式列表（支持通配符）。
     * <p>
     * 示例：
     * <ul>
     *     <li>{@code resume.*} — resume 命名空间下所有 Tool</li>
     *     <li>{@code rag.query} — 精确匹配</li>
     *     <li>{@code *} — 全部 Tool（仅 ADMIN）</li>
     * </ul>
     */
    @Builder.Default
    private Set<String> allowedToolPatterns = Collections.emptySet();

    /**
     * 允许访问的 MCP Server 信任等级下限（0-100）。
     * 低于此信任分的 MCP Server 将被拒绝。
     */
    @Builder.Default
    private int minMcpTrustScore = 0;

    /**
     * 单次请求最大 Tool 调用次数
     */
    @Builder.Default
    private int maxToolCallsPerRequest = 20;

    /**
     * 是否允许访问文件系统
     */
    @Builder.Default
    private boolean filesystemAccess = false;

    /**
     * 是否允许访问外部网络
     */
    @Builder.Default
    private boolean networkAccess = false;

    /**
     * 是否为超级管理员（跳过所有权限检查）
     */
    @Builder.Default
    private boolean admin = false;

    /**
     * 版本（用于灰度和审计）
     */
    @Builder.Default
    private String version = "1.0";

    /**
     * 描述
     */
    private String description;
}
