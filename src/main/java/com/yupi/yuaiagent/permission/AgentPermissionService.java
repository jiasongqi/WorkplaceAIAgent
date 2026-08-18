package com.yupi.yuaiagent.permission;

import com.yupi.yuaiagent.pack.PackPreferenceMode;
import com.yupi.yuaiagent.permission.model.PermissionProfile;
import com.yupi.yuaiagent.sessionstate.HandoffScopeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Set;

/**
 * Agent 权限校验服务 — 基于配置驱动的 PermissionProfile 进行 Tool 调用权限校验。
 * <p>
 * 支持精确方法名、类别通配符（{@code file.*}）以及历史 YAML 别名
 * （{@code rag.query} → {@code searchKnowledgeBase}）。
 * {@code doTerminate} / {@code checkAsyncToolTask} 对所有 Agent 放行。
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.yupi.yuaiagent.config.PlatformProperties platformProperties;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.yupi.yuaiagent.service.ExpertPackAppService expertPackAppService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.yupi.yuaiagent.pack.ExpertPackRegistry expertPackRegistry;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ActivationFingerprintCache activationFingerprintCache;

    /**
     * 检查 Agent 是否有权限调用指定 Tool
     *
     * @param agentCode Agent 编码
     * @param toolName  Tool 名称（如 {@code resume.optimize}）
     * @return true 表示有权限
     */
    public boolean checkPermission(String agentCode, String toolName) {
        return checkPermission(null, agentCode, toolName);
    }

    public boolean checkPermission(String userId, String agentCode, String toolName) {
        PermissionProfile profile = registry.getOrDefault(agentCode);

        // Admin 跳过所有检查
        if (profile.isAdmin()) {
            log.debug("[Permission] ADMIN agent={} tool={} -> ALLOWED", agentCode, toolName);
            return true;
        }

        if (ToolNameMatcher.isAlwaysAllowed(toolName)) {
            log.debug("[Permission] agent={} tool={} -> ALLOWED (control tool)", agentCode, toolName);
            return true;
        }

        boolean profileAllows = profileAllows(profile, toolName);
        boolean narrowedAllows = profileAllows;
        PermissionNarrowingService.Mode narrowingMode = narrowingMode();
        if (narrowingMode != PermissionNarrowingService.Mode.OFF
                && platformProperties != null
                && platformProperties.getPermission().isPackNarrowing()) {
            Set<String> effective = staticEffectivePatterns(userId, agentCode, profile);
            narrowedAllows = PermissionNarrowingService.allows(effective, toolName);
            if (narrowingMode == PermissionNarrowingService.Mode.OBSERVE && profileAllows != narrowedAllows) {
                log.warn("[Permission] observe drift agent={} tool={} legacy={} narrowed={}",
                        agentCode, toolName, profileAllows, narrowedAllows);
            }
            if (narrowingMode == PermissionNarrowingService.Mode.ENFORCE) {
                profileAllows = narrowedAllows;
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

    private boolean profileAllows(PermissionProfile profile, String toolName) {
        if (profile.getAllowedToolPatterns() == null) {
            return false;
        }
        for (String pattern : profile.getAllowedToolPatterns()) {
            if (matchToolPattern(pattern, toolName)) {
                return true;
            }
        }
        return false;
    }

    private PermissionNarrowingService.Mode narrowingMode() {
        if (platformProperties == null) {
            return PermissionNarrowingService.Mode.OFF;
        }
        try {
            return PermissionNarrowingService.Mode.valueOf(
                    platformProperties.getPermission().getNamespaceMode().trim().toUpperCase());
        } catch (RuntimeException ex) {
            return PermissionNarrowingService.Mode.OFF;
        }
    }

    public Set<String> staticEffectivePatternsFor(String userId, String agentCode) {
        return staticEffectivePatterns(userId, agentCode, registry.getOrDefault(agentCode));
    }

    private Set<String> staticEffectivePatterns(String userId, String agentCode, PermissionProfile profile) {
        java.util.function.Supplier<Set<String>> loader = () -> PermissionNarrowingService.effectivePatterns(
                profile, packUnionPatterns(userId), preferenceMode(userId));
        if (activationFingerprintCache == null || platformProperties == null
                || !platformProperties.getActivationCache().isEnabled()) {
            return loader.get();
        }
        String version = expertPackAppService == null ? "0" : String.valueOf(expertPackAppService.preferenceVersion(userId));
        String fingerprint = String.valueOf(registry.size());
        return activationFingerprintCache.getOrCompute(
                new ActivationFingerprintCache.Key(fingerprint, version, agentCode), loader);
    }

    private PackPreferenceMode preferenceMode(String userId) {
        return expertPackAppService == null
                ? PackPreferenceMode.UNSET
                : expertPackAppService.preferenceMode(userId);
    }

    private Set<String> packUnionPatterns(String userId) {
        Set<String> union = new java.util.LinkedHashSet<>();
        if (expertPackAppService == null || expertPackRegistry == null) {
            return union;
        }
        for (var pack : expertPackRegistry.list()) {
            if (!expertPackAppService.isPackEnabledForUser(userId, pack) || pack.getPermissionProfiles() == null) {
                continue;
            }
            for (String profileCode : pack.getPermissionProfiles()) {
                registry.get(profileCode).ifPresent(p -> {
                    if (p.getAllowedToolPatterns() != null) {
                        union.addAll(p.getAllowedToolPatterns());
                    }
                });
            }
        }
        return union;
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
        return ToolNameMatcher.matches(pattern, toolName);
    }
}
