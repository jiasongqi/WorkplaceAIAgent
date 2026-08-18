package com.yupi.yuaiagent.companion;

import com.yupi.yuaiagent.repository.entity.UserCompanionEntity;
import com.yupi.yuaiagent.repository.jpa.UserCompanionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserCompanionService {

    private static final Set<String> SELECTABLE_SKINS = Set.of("cat", "pilot");
    private static final Set<String> PRESENCE_VALUES = Set.of("onChair", "away");
    private static final String DEFAULT_SKIN = "cat";
    private static final int MAX_GIFTS = 50;

    private final UserCompanionJpaRepository companionRepository;

    @Transactional
    public UserCompanionView getOrClaim(String userId) {
        return toView(companionRepository.findByUserId(userId).orElseGet(() -> createDefault(userId)));
    }

    @Transactional
    public UserCompanionView update(String userId, UpdateCompanionRequest request) {
        UserCompanionEntity entity = companionRepository.findByUserId(userId)
                .orElseGet(() -> createDefault(userId));
        if (StringUtils.hasText(request.displayName())) {
            entity.setDisplayName(request.displayName().trim());
        }
        if (request.personaPrompt() != null) {
            entity.setPersonaPrompt(request.personaPrompt());
        }
        if (request.stylePrefs() != null) {
            entity.setStylePrefs(normalizeStylePrefs(mergePreferences(entity.getStylePrefs(), request.stylePrefs())));
        }
        if (request.enabledSkills() != null) {
            entity.setEnabledSkills(new ArrayList<>(request.enabledSkills()));
        }
        entity.setVersion(entity.getVersion() == null ? 2 : entity.getVersion() + 1);
        return toView(companionRepository.save(entity));
    }

    /**
     * Prompt injection block for Orchestrator context.
     * Auto-claims a default companion on first use.
     */
    public String buildPromptInjection(String userId) {
        if (!StringUtils.hasText(userId)) {
            return "";
        }
        UserCompanionView view = getOrClaim(userId);
        StringBuilder sb = new StringBuilder();
        sb.append("【个人智能体伙伴】\n");
        sb.append("名称：").append(view.displayName()).append('\n');
        Map<String, Object> prefs = view.stylePrefs();
        if (prefs != null && !prefs.isEmpty()) {
            Object tone = prefs.get("tone");
            Object focus = prefs.get("focus");
            if (tone != null && StringUtils.hasText(tone.toString())) {
                sb.append("语气偏好：").append(tone).append('\n');
            }
            if (focus != null && StringUtils.hasText(focus.toString())) {
                sb.append("关注方向：").append(focus).append('\n');
            }
        }
        if (StringUtils.hasText(view.personaPrompt())) {
            sb.append("人设规则：").append(view.personaPrompt()).append('\n');
        }
        sb.append("请以该伙伴身份与用户对话，保持风格一致。\n");
        return sb.toString();
    }

    private UserCompanionEntity createDefault(String userId) {
        UserCompanionEntity entity = new UserCompanionEntity();
        entity.setUserId(userId);
        entity.setDisplayName("你的职场伙伴");
        entity.setPersonaPrompt("你是用户的专属职场伙伴：先给结论，再给可执行步骤；语气简洁、少客套；不确定时主动澄清。");
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("tone", "简洁直接");
        prefs.put("focus", "简历、谈薪、职业方向");
        prefs.put("pet", normalizePet(Map.of(
                "enabled", true,
                "skin", "cat",
                "motion", "full",
                "bubbleLevel", "key"
        )));
        entity.setStylePrefs(prefs);
        entity.setEnabledSkills(new ArrayList<>());
        entity.setVersion(1);
        return companionRepository.save(entity);
    }

    private static UserCompanionView toView(UserCompanionEntity e) {
        return new UserCompanionView(
                e.getUserId(),
                e.getDisplayName(),
                e.getPersonaPrompt(),
                normalizeStylePrefs(e.getStylePrefs()),
                e.getEnabledSkills() != null ? List.copyOf(e.getEnabledSkills()) : List.of(),
                e.getVersion()
        );
    }

    private static Map<String, Object> normalizeStylePrefs(Map<String, Object> prefs) {
        Map<String, Object> next = mergePreferences(Map.of(), prefs);
        Object pet = next.get("pet");
        next.put("pet", normalizePet(pet instanceof Map<?, ?> map ? toStringMap(map) : Map.of()));
        return next;
    }

    private static Map<String, Object> normalizePet(Map<String, Object> pet) {
        Map<String, Object> next = new HashMap<>(pet == null ? Map.of() : pet);
        Object enabled = next.get("enabled");
        next.put("enabled", enabled instanceof Boolean flag ? flag : true);
        next.put("skin", resolveSkin(next.get("skin")));
        next.put("motion", textOrDefault(next.get("motion"), "full"));
        next.put("bubbleLevel", textOrDefault(next.get("bubbleLevel"), "key"));
        next.put("world", normalizeWorld(next.get("world")));
        return next;
    }

    private static Map<String, Object> normalizeWorld(Object raw) {
        Map<String, Object> world = raw instanceof Map<?, ?> map ? toStringMap(map) : new HashMap<>();
        Map<String, Object> next = new HashMap<>();
        String presence = world.get("presence") instanceof String value ? value : "onChair";
        next.put("presence", PRESENCE_VALUES.contains(presence) ? presence : "onChair");
        next.put("chair", textOrDefault(world.get("chair"), "wood"));
        next.put("rug", textOrDefault(world.get("rug"), "plain"));
        next.put("gifts", normalizeGifts(world.get("gifts")));
        return next;
    }

    private static List<Object> normalizeGifts(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Object> gifts = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> gift = toStringMap(map);
            Object id = gift.get("id");
            if (!(id instanceof String giftId) || !StringUtils.hasText(giftId) || !seen.add(giftId)) {
                continue;
            }
            gifts.add(gift);
            if (gifts.size() >= MAX_GIFTS) {
                break;
            }
        }
        return gifts;
    }

    private static String resolveSkin(Object skin) {
        return skin instanceof String id && SELECTABLE_SKINS.contains(id) ? id : DEFAULT_SKIN;
    }

    private static String textOrDefault(Object value, String fallback) {
        return value instanceof String text && StringUtils.hasText(text) ? text : fallback;
    }

    private static Map<String, Object> mergePreferences(
            Map<String, Object> current,
            Map<String, Object> updates
    ) {
        Map<String, Object> merged = new HashMap<>();
        if (current != null) {
            current.forEach((key, value) -> merged.put(key, copyPreferenceValue(value)));
        }
        if (updates == null) {
            return merged;
        }
        updates.forEach((key, value) -> {
            Object existing = merged.get(key);
            if (existing instanceof Map<?, ?> existingMap && value instanceof Map<?, ?> updateMap) {
                merged.put(key, mergePreferences(toStringMap(existingMap), toStringMap(updateMap)));
            } else {
                merged.put(key, copyPreferenceValue(value));
            }
        });
        return merged;
    }

    private static Object copyPreferenceValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return mergePreferences(Map.of(), toStringMap(map));
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return value;
    }

    private static Map<String, Object> toStringMap(Map<?, ?> source) {
        Map<String, Object> result = new HashMap<>();
        source.forEach((key, value) -> {
            if (key instanceof String stringKey) {
                result.put(stringKey, value);
            }
        });
        return result;
    }

    public record UserCompanionView(
            String userId,
            String displayName,
            String personaPrompt,
            Map<String, Object> stylePrefs,
            List<String> enabledSkills,
            Integer version
    ) {
    }

    public record UpdateCompanionRequest(
            String displayName,
            String personaPrompt,
            Map<String, Object> stylePrefs,
            List<String> enabledSkills
    ) {
    }
}
