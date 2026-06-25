package com.yupi.yuaiagent.registry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent 描述符 — 声明式定义一个 Agent 的全部元数据。
 * <p>
 * 从 {@code resources/agents/*.yaml} 加载，支持 Agent Marketplace 场景。
 * 新增 Agent 只需新增 YAML 文件，配置即可上线。
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDescriptor {

    /**
     * Agent 编码（全局唯一，如 resume-agent）
     */
    private String agentCode;

    /**
     * Agent 版本（语义化版本）
     */
    @Builder.Default
    private String agentVersion = "1.0.0";

    /**
     * 可读名称
     */
    private String displayName;

    /**
     * 描述
     */
    private String description;

    /**
     * Prompt 版本（关联 Prompt Registry）
     */
    private String promptVersion;

    /**
     * 能力标签（用于路由和搜索）
     */
    private List<String> capabilities;

    /**
     * 绑定的 Skill 名称列表
     */
    private List<String> skillBindings;

    /**
     * 绑定的 MCP Server ID 列表
     */
    private List<String> mcpBindings;

    /**
     * 权限画像引用（关联 PermissionProfile.agentCode）
     */
    private String permissionProfile;

    /**
     * 意图关键词（用于 NLU 路由匹配）
     */
    private List<String> intentKeywords;

    /**
     * 元数据（图标、分类、作者等）
     */
    private Map<String, String> metadata;

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Agent 类型：CONSULTATION / TOOL_CALL / GENERAL
     */
    @Builder.Default
    private String agentType = "GENERAL";
}
