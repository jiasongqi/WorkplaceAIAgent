package com.yupi.yuaiagent.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Prompt 注册中心 — 管理 Prompt 版本，支持灰度发布和 A/B 测试流量分配。
 *
 * @author jsq
 */
@Slf4j
@Service
public class PromptRegistry {

    /** Maximum versions to keep per prompt key to prevent OOM */
    private static final int MAX_VERSIONS_PER_KEY = 50;

    private final Map<String, List<PromptVersion>> prompts = new ConcurrentHashMap<>();

    /**
     * 注册 Prompt 版本
     */
    public void register(PromptVersion version) {
        prompts.computeIfAbsent(version.getPromptKey(), k -> new CopyOnWriteArrayList<>());
        List<PromptVersion> list = prompts.get(version.getPromptKey());
        list.add(version);

        // Enforce version limit: remove oldest versions if exceeded
        if (list.size() > MAX_VERSIONS_PER_KEY) {
            int toRemove = list.size() - MAX_VERSIONS_PER_KEY;
            log.warn("[PromptRegistry] key={} versions exceeded limit ({}), trimming {} oldest",
                    version.getPromptKey(), MAX_VERSIONS_PER_KEY, toRemove);
            // CopyOnWriteArrayList: create a new list with only the latest versions
            List<PromptVersion> trimmed = new CopyOnWriteArrayList<>(
                    list.subList(toRemove, list.size()));
            prompts.put(version.getPromptKey(), trimmed);
        }

        log.info("Prompt registered: key={}, version={}, status={}",
                version.getPromptKey(), version.getVersion(), version.getStatus());
    }

    /**
     * 获取当前活跃版本（按流量分配）
     */
    public PromptVersion getActiveVersion(String promptKey) {
        List<PromptVersion> versions = prompts.get(promptKey);
        if (versions == null || versions.isEmpty()) {
            log.warn("Prompt 不存在: {}", promptKey);
            return null;
        }

        List<PromptVersion> activeVersions = versions.stream()
                .filter(v -> "ACTIVE".equals(v.getStatus()) || "CANARY".equals(v.getStatus()))
                .toList();

        if (activeVersions.isEmpty()) {
            return versions.get(versions.size() - 1);
        }

        if (activeVersions.size() == 1) {
            return activeVersions.get(0);
        }

        // A/B 测试流量分配
        int random = ThreadLocalRandom.current().nextInt(100);
        int cumulative = 0;
        for (PromptVersion v : activeVersions) {
            cumulative += v.getTrafficPercent();
            if (random < cumulative) {
                return v;
            }
        }
        return activeVersions.get(activeVersions.size() - 1);
    }

    /**
     * 渲染 Prompt（变量替换）
     */
    public String render(String promptKey, Map<String, String> variables) {
        PromptVersion version = getActiveVersion(promptKey);
        if (version == null) {
            log.warn("找不到 Prompt: {}", promptKey);
            return "";
        }
        String result = version.getTemplate();
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    /**
     * 创建默认 Prompt
     */
    public PromptVersion createPrompt(String promptKey, String template, String author, String description) {
        PromptVersion version = PromptVersion.builder()
                .promptKey(promptKey)
                .version(1)
                .status("ACTIVE")
                .template(template)
                .author(author)
                .description(description)
                .trafficPercent(100)
                .createdAt(LocalDateTime.now())
                .build();
        register(version);
        return version;
    }

    public List<PromptVersion> getVersions(String promptKey) {
        return prompts.getOrDefault(promptKey, Collections.emptyList());
    }

    public Set<String> getAllKeys() {
        return prompts.keySet();
    }
}
