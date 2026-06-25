package com.yupi.yuaiagent.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP 信任管理服务 — 注册、评估和校验 MCP Server 的信任等级。
 * <p>
 * 核心职责：
 * <ul>
 *     <li>注册 MCP Server 并根据来源自动分配信任等级</li>
 *     <li>执行前校验 MCP Server 是否有权限提供指定 Tool</li>
 *     <li>支持运行时动态调整信任等级</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Service
public class McpTrustService {

    private final Map<String, McpServerProfile> servers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 注册内置 MCP Server（默认 VERIFIED）
        registerServer(McpServerProfile.builder()
                .serverId("yu-image-search-mcp-server")
                .name("图片搜索 MCP")
                .trustLevel(McpTrustLevel.VERIFIED)
                .source("OFFICIAL")
                .description("内置图片搜索 MCP Server")
                .registeredAt(LocalDateTime.now())
                .build());

        registerServer(McpServerProfile.builder()
                .serverId("amap-maps")
                .name("高德地图 MCP")
                .trustLevel(McpTrustLevel.PARTNER)
                .source("THIRD_PARTY")
                .description("高德地图 MCP Server")
                .registeredAt(LocalDateTime.now())
                .build());

        log.info("MCP 信任服务初始化完成，已注册 {} 个 MCP Server", servers.size());
    }

    /**
     * 注册 MCP Server（根据来源自动分配信任等级）
     */
    public void registerServer(McpServerProfile profile) {
        Objects.requireNonNull(profile.getServerId(), "serverId must not be null");

        // 根据来源自动调整信任等级（如未手动指定）
        if (profile.getTrustLevel() == null) {
            profile.setTrustLevel(inferTrustLevel(profile.getSource()));
        }
        if (profile.getRegisteredAt() == null) {
            profile.setRegisteredAt(LocalDateTime.now());
        }

        servers.put(profile.getServerId(), profile);
        log.info("注册 MCP Server: {} trust={} source={}",
                profile.getServerId(), profile.getTrustLevel(), profile.getSource());
    }

    /**
     * 校验 MCP Server 是否有权限提供指定 Tool
     *
     * @param serverId MCP Server ID
     * @param toolName Tool 名称
     * @return true 表示允许
     */
    public boolean checkMcpPermission(String serverId, String toolName) {
        McpServerProfile profile = servers.get(serverId);
        if (profile == null) {
            log.warn("[McpTrust] unknown server={} tool={} -> DENIED", serverId, toolName);
            return false;
        }
        if (!profile.isEnabled()) {
            log.warn("[McpTrust] disabled server={} tool={} -> DENIED", serverId, toolName);
            return false;
        }

        // 检查信任等级是否允许该 Tool
        boolean allowed = profile.getTrustLevel().allowsTool(toolName);

        // 检查额外允许的命名空间
        if (!allowed && profile.getExtraAllowedNamespaces() != null) {
            for (String ns : profile.getExtraAllowedNamespaces()) {
                if ("*".equals(ns) || toolName.startsWith(ns.replace(".*", "") + ".")) {
                    allowed = true;
                    break;
                }
            }
        }

        if (allowed) {
            log.debug("[McpTrust] server={} trust={} tool={} -> ALLOWED",
                    serverId, profile.getTrustLevel(), toolName);
        } else {
            log.warn("[McpTrust] server={} trust={} tool={} -> DENIED",
                    serverId, profile.getTrustLevel(), toolName);
        }
        return allowed;
    }

    /**
     * 检查 MCP Server 信任分是否满足 Agent 的最低要求
     */
    public boolean meetsMinTrustScore(String serverId, int minTrustScore) {
        McpServerProfile profile = servers.get(serverId);
        if (profile == null) {
            return false;
        }
        return profile.getTrustLevel().getTrustScore() >= minTrustScore;
    }

    /**
     * 动态调整信任等级
     */
    public void updateTrustLevel(String serverId, McpTrustLevel newLevel) {
        McpServerProfile profile = servers.get(serverId);
        if (profile != null) {
            McpTrustLevel oldLevel = profile.getTrustLevel();
            profile.setTrustLevel(newLevel);
            log.info("[McpTrust] server={} trust changed: {} -> {}", serverId, oldLevel, newLevel);
        }
    }

    /**
     * 按信任等级查询 MCP Server
     */
    public List<McpServerProfile> getServersByTrustLevel(McpTrustLevel level) {
        return servers.values().stream()
                .filter(p -> p.getTrustLevel() == level)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有已注册的 MCP Server
     */
    public Collection<McpServerProfile> getAllServers() {
        return Collections.unmodifiableCollection(servers.values());
    }

    /**
     * 获取指定 Server 画像
     */
    public Optional<McpServerProfile> getServer(String serverId) {
        return Optional.ofNullable(servers.get(serverId));
    }

    /**
     * 根据来源推断信任等级
     */
    private McpTrustLevel inferTrustLevel(String source) {
        if (source == null) return McpTrustLevel.PRIVATE;
        return switch (source.toUpperCase()) {
            case "OFFICIAL" -> McpTrustLevel.VERIFIED;
            case "THIRD_PARTY" -> McpTrustLevel.PARTNER;
            case "COMMUNITY" -> McpTrustLevel.COMMUNITY;
            default -> McpTrustLevel.PRIVATE;
        };
    }
}
