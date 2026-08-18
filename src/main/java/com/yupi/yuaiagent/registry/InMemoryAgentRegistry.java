package com.yupi.yuaiagent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yupi.yuaiagent.manifest.ManifestDualReadVerifier;
import com.yupi.yuaiagent.manifest.ManifestLoadPolicy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存 Agent 注册中心 — V1 实现。
 * <p>
 * 启动时从 {@code classpath:agents/*.yaml} 加载，支持运行时动态注册。
 *
 * @author jsq
 */
@Slf4j
@Component
public class InMemoryAgentRegistry implements AgentRegistry {

    private final Map<String, AgentDescriptor> agents = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Autowired(required = false)
    private ManifestDualReadVerifier manifestDualReadVerifier;

    @PostConstruct
    public void init() {
        loadBuiltinAgents();
        if (manifestDualReadVerifier != null) {
            manifestDualReadVerifier.verify(
                    "agents",
                    "classpath:agents/*.yaml",
                    AgentDescriptor.class,
                    AgentDescriptor::getAgentCode,
                    agents,
                    ManifestLoadPolicy.LENIENT
            );
        }
        log.info("Agent 注册中心初始化完成，共加载 {} 个 Agent", agents.size());
    }

    @Deprecated(since = "S1", forRemoval = false)
    private void loadBuiltinAgents() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:agents/*.yaml");

            for (Resource resource : resources) {
                try {
                    AgentDescriptor descriptor = yamlMapper.readValue(
                            resource.getInputStream(), AgentDescriptor.class);
                    if (descriptor != null && descriptor.getAgentCode() != null) {
                        agents.put(descriptor.getAgentCode(), descriptor);
                        log.debug("加载 Agent 描述: {} - {}",
                                descriptor.getAgentCode(), descriptor.getDisplayName());
                    }
                } catch (Exception e) {
                    log.warn("加载 Agent 描述文件失败: {}", resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            log.warn("扫描 Agent 描述目录失败，可能是目录不存在", e);
        }
    }

    @Override
    public void register(AgentDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "AgentDescriptor must not be null");
        Objects.requireNonNull(descriptor.getAgentCode(), "agentCode must not be null");
        agents.put(descriptor.getAgentCode(), descriptor);
        log.info("注册 Agent: {}", descriptor.getAgentCode());
    }

    @Override
    public Optional<AgentDescriptor> get(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(agents.get(agentCode));
    }

    @Override
    public Collection<AgentDescriptor> list() {
        return Collections.unmodifiableCollection(agents.values());
    }

    @Override
    public List<AgentDescriptor> findByCapability(String capability) {
        return agents.values().stream()
                .filter(d -> d.getCapabilities() != null && d.getCapabilities().contains(capability))
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentDescriptor> findByIntentKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        String lowerKeyword = keyword.toLowerCase();
        return agents.values().stream()
                .filter(d -> d.getIntentKeywords() != null
                        && d.getIntentKeywords().stream()
                        .anyMatch(k -> k.toLowerCase().contains(lowerKeyword)
                                || lowerKeyword.contains(k.toLowerCase())))
                .collect(Collectors.toList());
    }

    @Override
    public int size() {
        return agents.size();
    }
}
