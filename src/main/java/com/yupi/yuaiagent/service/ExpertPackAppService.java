package com.yupi.yuaiagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.pack.ExpertPackDefinition;
import com.yupi.yuaiagent.pack.ExpertPackRegistry;
import com.yupi.yuaiagent.pack.ExpertPackView;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ExpertPackAppService {

    @Resource
    private ExpertPackRegistry expertPackRegistry;

    @Value("${expert-pack.storage.dir:./tmp/expert-packs}")
    private String storageDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    /** userId -> (packId -> enabled) */
    private final Map<String, Map<String, Boolean>> prefs = new ConcurrentHashMap<>();
    private File storageFile;

    @PostConstruct
    public void init() {
        try {
            File dir = new File(storageDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            storageFile = new File(dir, "user-prefs.json");
            if (storageFile.exists()) {
                Map<String, Map<String, Boolean>> loaded = objectMapper.readValue(
                        storageFile, new TypeReference<>() {});
                if (loaded != null) {
                    prefs.putAll(loaded);
                }
            }
        } catch (Exception e) {
            log.warn("加载专家包偏好失败: {}", e.getMessage());
        }
    }

    public List<ExpertPackView> listForUser(String userId) {
        return expertPackRegistry.list().stream()
                .map(p -> toView(userId, p))
                .toList();
    }

    public void setEnabled(String userId, String packId, boolean enabled) {
        if (!StringUtils.hasText(userId)) {
            throw BusinessException.notLoggedIn();
        }
        ExpertPackDefinition pack = expertPackRegistry.get(packId)
                .orElseThrow(() -> BusinessException.notFound("专家包"));
        prefs.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(pack.getPackId(), enabled);
        persist();
    }

    public Set<String> getEnabledSkillNames(String userId) {
        Set<String> names = new HashSet<>();
        for (ExpertPackDefinition pack : expertPackRegistry.list()) {
            if (isEnabled(userId, pack)) {
                if (pack.getSkillNames() != null) {
                    names.addAll(pack.getSkillNames());
                }
            }
        }
        // If user has no prefs and all packs disabled somehow, fall back to all pack skills
        if (names.isEmpty()) {
            for (ExpertPackDefinition pack : expertPackRegistry.list()) {
                if (pack.isEnabledByDefault() && pack.getSkillNames() != null) {
                    names.addAll(pack.getSkillNames());
                }
            }
        }
        return names;
    }

    /** Agent codes covered by currently enabled packs (for digital-employee template filter). */
    public Set<String> getEnabledAgentCodes(String userId) {
        Set<String> codes = new HashSet<>();
        for (ExpertPackDefinition pack : expertPackRegistry.list()) {
            if (isEnabled(userId, pack) && pack.getAgentCodes() != null) {
                codes.addAll(pack.getAgentCodes());
            }
        }
        if (codes.isEmpty()) {
            for (ExpertPackDefinition pack : expertPackRegistry.list()) {
                if (pack.isEnabledByDefault() && pack.getAgentCodes() != null) {
                    codes.addAll(pack.getAgentCodes());
                }
            }
        }
        return codes;
    }

    private ExpertPackView toView(String userId, ExpertPackDefinition pack) {
        return ExpertPackView.builder()
                .packId(pack.getPackId())
                .displayName(pack.getDisplayName())
                .description(pack.getDescription())
                .agentCodes(pack.getAgentCodes())
                .skillNames(pack.getSkillNames())
                .permissionProfiles(pack.getPermissionProfiles())
                .enabled(isEnabled(userId, pack))
                .build();
    }

    private boolean isEnabled(String userId, ExpertPackDefinition pack) {
        if (!StringUtils.hasText(userId)) {
            return pack.isEnabledByDefault();
        }
        Map<String, Boolean> userPrefs = prefs.get(userId);
        if (userPrefs != null && userPrefs.containsKey(pack.getPackId())) {
            return Boolean.TRUE.equals(userPrefs.get(pack.getPackId()));
        }
        return pack.isEnabledByDefault();
    }

    private void persist() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storageFile, new HashMap<>(prefs));
        } catch (Exception e) {
            log.warn("持久化专家包偏好失败: {}", e.getMessage());
        }
    }
}
