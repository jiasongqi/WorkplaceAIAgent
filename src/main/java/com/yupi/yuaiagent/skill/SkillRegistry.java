package com.yupi.yuaiagent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 技能注册中心 - 负责加载、管理和查找技能
 * 
 * 核心职责：
 * 1. 启动时自动扫描 classpath:/skills/*.yaml 并加载
 * 2. 提供按名称、标签、意图查找技能的能力
 * 3. 支持运行时动态注册新技能
 */
@Slf4j
@Component
public class SkillRegistry {
    
    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    
    @PostConstruct
    public void init() {
        loadBuiltinSkills();
        log.info("技能注册中心初始化完成，共加载 {} 个技能", skills.size());
    }
    
    /**
     * 加载内置技能文件
     */
    private void loadBuiltinSkills() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:skills/*.yaml");
            
            for (Resource resource : resources) {
                try {
                    SkillDefinition skill = yamlMapper.readValue(resource.getInputStream(), SkillDefinition.class);
                    if (skill.getName() != null) {
                        skills.put(skill.getName(), skill);
                        log.debug("加载技能: {} - {}", skill.getName(), skill.getDescription());
                    }
                } catch (Exception e) {
                    log.warn("加载技能文件失败: {}", resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            log.warn("扫描技能目录失败，可能是目录不存在", e);
        }
    }
    
    /**
     * 注册新技能（运行时动态添加）
     */
    public void register(SkillDefinition skill) {
        skills.put(skill.getName(), skill);
        log.info("注册新技能: {}", skill.getName());
    }
    
    /**
     * 按名称获取技能
     */
    public SkillDefinition getByName(String name) {
        return skills.get(name);
    }
    
    /**
     * 按标签查找技能
     */
    public List<SkillDefinition> findByTag(String tag) {
        return skills.values().stream()
                .filter(s -> s.getTags() != null && s.getTags().contains(tag))
                .collect(Collectors.toList());
    }
    
    /**
     * 智能匹配技能 - 根据用户意图查找最相关的技能
     * 简单实现：关键词匹配，后续可升级为向量相似度
     */
    public List<SkillDefinition> findByIntent(String userMessage) {
        return findByIntent(userMessage, null);
    }

    /**
     * 智能匹配技能；若 allowedNames 非空则仅在该集合内匹配（专家包作用域）。
     */
    public List<SkillDefinition> findByIntent(String userMessage, java.util.Collection<String> allowedNames) {
        String lowerMsg = userMessage.toLowerCase();

        return skills.values().stream()
                .filter(skill -> allowedNames == null || allowedNames.isEmpty() || allowedNames.contains(skill.getName()))
                .filter(skill -> {
                    String keywords = (skill.getDescription() + " " +
                            String.join(" ", skill.getTags() != null ? skill.getTags() : List.of()))
                            .toLowerCase();
                    return Arrays.stream(keywords.split("[\\s，、]+"))
                            .anyMatch(keyword -> keyword.length() > 1 && lowerMsg.contains(keyword));
                })
                .sorted((a, b) -> {
                    long aCount = countMatches(lowerMsg, a);
                    long bCount = countMatches(lowerMsg, b);
                    return Long.compare(bCount, aCount);
                })
                .collect(Collectors.toList());
    }
    
    private long countMatches(String message, SkillDefinition skill) {
        String keywords = (skill.getDescription() + " " + 
                String.join(" ", skill.getTags() != null ? skill.getTags() : List.of()))
                .toLowerCase();
        return Arrays.stream(keywords.split("[\\s，、]+"))
                .filter(k -> k.length() > 1 && message.contains(k))
                .count();
    }
    
    /**
     * 获取所有已注册技能
     */
    public Collection<SkillDefinition> getAll() {
        return Collections.unmodifiableCollection(skills.values());
    }
    
    /**
     * 获取技能数量
     */
    public int size() {
        return skills.size();
    }
}
