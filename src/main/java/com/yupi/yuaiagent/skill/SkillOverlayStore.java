package com.yupi.yuaiagent.skill;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory USER/SESSION overlays. Persistence can replace this later without changing resolve order.
 */
public class SkillOverlayStore {

    private final ConcurrentHashMap<String, SkillDefinition> userSkills = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SkillDefinition> sessionSkills = new ConcurrentHashMap<>();

    public void putUser(SkillDefinition skill) {
        if (skill != null && skill.getName() != null) {
            userSkills.put(skill.getName(), skill);
        }
    }

    public void putSession(SkillDefinition skill) {
        if (skill != null && skill.getName() != null) {
            sessionSkills.put(skill.getName(), skill);
        }
    }

    public SkillDefinition user(String name) {
        return name == null ? null : userSkills.get(name);
    }

    public SkillDefinition session(String name) {
        return name == null ? null : sessionSkills.get(name);
    }

    public Map<String, SkillDefinition> userSnapshot() {
        return Map.copyOf(userSkills);
    }
}
