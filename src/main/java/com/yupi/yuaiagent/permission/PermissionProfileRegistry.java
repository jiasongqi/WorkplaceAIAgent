package com.yupi.yuaiagent.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yupi.yuaiagent.manifest.ManifestDualReadVerifier;
import com.yupi.yuaiagent.manifest.ManifestLoadPolicy;
import com.yupi.yuaiagent.permission.model.PermissionProfile;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限画像注册中心 — 从 YAML 配置文件加载 Agent 权限画像。
 * <p>
 * 启动时自动扫描 {@code classpath:permissions/*.yaml}，
 * 并支持运行时通过 {@link #register(PermissionProfile)} 动态注册。
 * <p>
 * 设计原则：新增 Agent 只需新增 YAML 文件，无需修改代码或重启。
 *
 * @author jsq
 */
@Slf4j
@Component
public class PermissionProfileRegistry {

    private final Map<String, PermissionProfile> profiles = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Autowired(required = false)
    private ManifestDualReadVerifier manifestDualReadVerifier;

    @PostConstruct
    public void init() {
        loadBuiltinProfiles();
        if (manifestDualReadVerifier != null) {
            manifestDualReadVerifier.verify(
                    "permissions",
                    "classpath:permissions/*.yaml",
                    PermissionProfile.class,
                    PermissionProfile::getAgentCode,
                    profiles,
                    ManifestLoadPolicy.STRICT
            );
        }
        log.info("权限画像注册中心初始化完成，共加载 {} 个画像", profiles.size());
    }

    /**
     * 加载内置权限配置文件
     */
    @Deprecated(since = "S1", forRemoval = false)
    private void loadBuiltinProfiles() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:permissions/*.yaml");

            for (Resource resource : resources) {
                try {
                    PermissionProfile profile = yamlMapper.readValue(
                            resource.getInputStream(), PermissionProfile.class);
                    if (profile != null && profile.getAgentCode() != null) {
                        profiles.put(profile.getAgentCode(), profile);
                        log.debug("加载权限画像: {} - {}", profile.getAgentCode(), profile.getDisplayName());
                    }
                } catch (Exception e) {
                    log.warn("加载权限画像文件失败: {}", resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            log.warn("扫描权限画像目录失败，可能是目录不存在", e);
        }
    }

    /**
     * 动态注册权限画像（运行时添加）
     */
    public void register(PermissionProfile profile) {
        Objects.requireNonNull(profile, "PermissionProfile must not be null");
        Objects.requireNonNull(profile.getAgentCode(), "agentCode must not be null");
        profiles.put(profile.getAgentCode(), profile);
        log.info("动态注册权限画像: {}", profile.getAgentCode());
    }

    /**
     * 按 agentCode 获取权限画像
     *
     * @return 权限画像，不存在时返回 {@link Optional#empty()}
     */
    public Optional<PermissionProfile> get(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profiles.get(agentCode));
    }

    /**
     * 按 agentCode 获取权限画像，不存在则返回默认画像（空权限）
     */
    public PermissionProfile getOrDefault(String agentCode) {
        return profiles.getOrDefault(agentCode, PermissionProfile.builder()
                .agentCode(agentCode)
                .displayName("Unknown Agent")
                .description("未注册的 Agent，默认无权限")
                .build());
    }

    /**
     * 获取所有已注册的权限画像
     */
    public Collection<PermissionProfile> getAll() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    /**
     * 获取已注册画像数量
     */
    public int size() {
        return profiles.size();
    }

    /**
     * 检查 agentCode 是否已注册
     */
    public boolean contains(String agentCode) {
        return profiles.containsKey(agentCode);
    }
}
