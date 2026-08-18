package com.yupi.yuaiagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.pack.ExpertPackDefinition;
import com.yupi.yuaiagent.pack.ExpertPackPreferenceRepository;
import com.yupi.yuaiagent.pack.ExpertPackRegistry;
import com.yupi.yuaiagent.pack.ExpertPackView;
import com.yupi.yuaiagent.pack.PackPreferenceMode;
import com.yupi.yuaiagent.pack.UserPackPreference;
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

    @Resource
    private ExpertPackPreferenceRepository preferenceRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.yupi.yuaiagent.permission.ActivationFingerprintCache activationFingerprintCache;

    @Value("${expert-pack.storage.dir:./tmp/expert-packs}")
    private String storageDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    /** userId -> (packId -> enabled) */
    private final Map<String, Map<String, Boolean>> prefs = new ConcurrentHashMap<>();
    private File storageFile;

    @PostConstruct
    public void init() {
        if (preferenceRepository != null) {
            return;
        }
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
        UserPackPreference current = currentPreference(userId);
        Map<String, Boolean> packs = new HashMap<>(current.packs());
        packs.put(pack.getPackId(), enabled);
        PackPreferenceMode mode = packs.values().stream().allMatch(v -> !Boolean.TRUE.equals(v))
                ? PackPreferenceMode.EXPLICIT_ALL_DISABLED
                : PackPreferenceMode.EXPLICIT_PARTIAL;
        savePreference(new UserPackPreference(userId, mode, packs, current.version()));
        if (activationFingerprintCache != null) {
            activationFingerprintCache.evictAll();
        }
    }

    public PackPreferenceMode preferenceMode(String userId) {
        return currentPreference(userId).mode();
    }

    public Map<String, Boolean> preferencePacks(String userId) {
        return currentPreference(userId).packs();
    }

    public long preferenceVersion(String userId) {
        return currentPreference(userId).version();
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
        // If the user has never saved prefs, fall back to default-enabled packs.
        // An explicit empty/all-false map means ALL_DISABLED and must not resurrect defaults.
        if (names.isEmpty() && preferenceMode(userId) == PackPreferenceMode.UNSET) {
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
        if (codes.isEmpty() && preferenceMode(userId) == PackPreferenceMode.UNSET) {
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

    public boolean isPackEnabledForUser(String userId, ExpertPackDefinition pack) {
        return isEnabled(userId, pack);
    }

    private boolean isEnabled(String userId, ExpertPackDefinition pack) {
        if (!StringUtils.hasText(userId)) {
            return pack.isEnabledByDefault();
        }
        UserPackPreference preference = currentPreference(userId);
        if (preference.mode() == PackPreferenceMode.EXPLICIT_ALL_DISABLED) {
            return false;
        }
        if (preference.packs().containsKey(pack.getPackId())) {
            return Boolean.TRUE.equals(preference.packs().get(pack.getPackId()));
        }
        return pack.isEnabledByDefault();
    }

    private UserPackPreference currentPreference(String userId) {
        if (!StringUtils.hasText(userId)) {
            return new UserPackPreference("", PackPreferenceMode.UNSET, Map.of(), 0);
        }
        if (preferenceRepository != null) {
            return preferenceRepository.find(userId)
                    .orElse(new UserPackPreference(userId, PackPreferenceMode.UNSET, Map.of(), 0));
        }
        Map<String, Boolean> local = prefs.get(userId);
        if (local == null) {
            return new UserPackPreference(userId, PackPreferenceMode.UNSET, Map.of(), 0);
        }
        PackPreferenceMode mode = local.isEmpty()
                ? PackPreferenceMode.UNSET
                : local.values().stream().allMatch(v -> !Boolean.TRUE.equals(v))
                ? PackPreferenceMode.EXPLICIT_ALL_DISABLED
                : PackPreferenceMode.EXPLICIT_PARTIAL;
        return new UserPackPreference(userId, mode, local, 1);
    }

    private void savePreference(UserPackPreference preference) {
        if (preferenceRepository != null) {
            preferenceRepository.save(preference);
            return;
        }
        prefs.put(preference.userId(), new ConcurrentHashMap<>(preference.packs()));
        persist();
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
