package com.yupi.yuaiagent.skill;

import com.yupi.yuaiagent.guard.PromptInjectionDetector;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Overlay order: SESSION &gt; USER &gt; EXTERNAL &gt; BUILTIN.
 */
public class SkillOverlayResolver {

    public enum Layer {
        BUILTIN,
        EXTERNAL,
        USER,
        SESSION
    }

    private final PromptInjectionDetector injectionDetector;

    public SkillOverlayResolver(PromptInjectionDetector injectionDetector) {
        this.injectionDetector = injectionDetector;
    }

    public SkillDefinition resolve(String name, Map<Layer, SkillDefinition> layers) {
        if (layers == null || layers.isEmpty()) {
            return null;
        }
        for (Layer layer : new Layer[]{Layer.SESSION, Layer.USER, Layer.EXTERNAL, Layer.BUILTIN}) {
            SkillDefinition skill = layers.get(layer);
            if (skill == null) {
                continue;
            }
            if ((layer == Layer.USER || layer == Layer.SESSION) && injectionDetector != null) {
                var detection = injectionDetector.detect(skill.getSystemPrompt() == null ? "" : skill.getSystemPrompt());
                if (detection != null && !detection.safe()) {
                    continue;
                }
            }
            if (name == null || name.equals(skill.getName())) {
                return skill;
            }
        }
        return null;
    }

    public static Map<Layer, SkillDefinition> layers(SkillDefinition builtin, SkillDefinition external,
                                                     SkillDefinition user, SkillDefinition session) {
        Map<Layer, SkillDefinition> map = new LinkedHashMap<>();
        if (builtin != null) {
            map.put(Layer.BUILTIN, builtin);
        }
        if (external != null) {
            map.put(Layer.EXTERNAL, external);
        }
        if (user != null) {
            map.put(Layer.USER, user);
        }
        if (session != null) {
            map.put(Layer.SESSION, session);
        }
        return map;
    }
}
