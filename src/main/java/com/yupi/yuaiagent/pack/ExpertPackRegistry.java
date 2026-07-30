package com.yupi.yuaiagent.pack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yupi.yuaiagent.permission.PermissionProfileRegistry;
import com.yupi.yuaiagent.registry.AgentRegistry;
import com.yupi.yuaiagent.skill.SkillRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ExpertPackRegistry {

    private final Map<String, ExpertPackDefinition> packs = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Resource
    private AgentRegistry agentRegistry;
    @Resource
    private SkillRegistry skillRegistry;
    @Resource
    private PermissionProfileRegistry permissionProfileRegistry;

    @PostConstruct
    public void init() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            var resources = resolver.getResources("classpath:packs/*.yaml");
            for (var resource : resources) {
                try {
                    ExpertPackDefinition pack = yamlMapper.readValue(resource.getInputStream(), ExpertPackDefinition.class);
                    if (pack != null && pack.getPackId() != null) {
                        validate(pack);
                        packs.put(pack.getPackId(), pack);
                        log.info("加载专家包: {} - {}", pack.getPackId(), pack.getDisplayName());
                    }
                } catch (Exception e) {
                    log.warn("加载专家包失败: {}", resource.getFilename(), e);
                }
            }
        } catch (Exception e) {
            log.warn("扫描专家包目录失败", e);
        }
        log.info("专家包注册中心初始化完成，共 {} 个", packs.size());
    }

    private void validate(ExpertPackDefinition pack) {
        if (pack.getAgentCodes() != null) {
            for (String code : pack.getAgentCodes()) {
                if (agentRegistry.get(code).isEmpty()) {
                    log.warn("专家包 {} 引用未知 agentCode={}", pack.getPackId(), code);
                }
            }
        }
        if (pack.getSkillNames() != null) {
            for (String name : pack.getSkillNames()) {
                if (skillRegistry.getByName(name) == null) {
                    log.warn("专家包 {} 引用未知 skill={}", pack.getPackId(), name);
                }
            }
        }
        if (pack.getPermissionProfiles() != null) {
            for (String profile : pack.getPermissionProfiles()) {
                if (permissionProfileRegistry.get(profile).isEmpty()) {
                    log.warn("专家包 {} 引用未知 permissionProfile={}", pack.getPackId(), profile);
                }
            }
        }
    }

    public Optional<ExpertPackDefinition> get(String packId) {
        return Optional.ofNullable(packs.get(packId));
    }

    public Collection<ExpertPackDefinition> list() {
        return Collections.unmodifiableCollection(packs.values());
    }
}
