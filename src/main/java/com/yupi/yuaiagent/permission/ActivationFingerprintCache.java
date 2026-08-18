package com.yupi.yuaiagent.permission;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches base ∩ pack patterns only. AccessDecision, quota, HITL and handoff stay request-scoped.
 */
public class ActivationFingerprintCache {

    public record Key(String manifestFingerprint, String preferenceVersion, String agentCode) {
        public Key {
            Objects.requireNonNull(agentCode, "agentCode");
        }
    }

    private final ConcurrentHashMap<Key, Set<String>> staticPatterns = new ConcurrentHashMap<>();

    public Set<String> getOrCompute(Key key, java.util.function.Supplier<Set<String>> loader) {
        return staticPatterns.computeIfAbsent(key, ignored -> Set.copyOf(loader.get()));
    }

    public void evict(String agentCode) {
        staticPatterns.keySet().removeIf(key -> key.agentCode().equals(agentCode));
    }

    public void evictAll() {
        staticPatterns.clear();
    }

    public int size() {
        return staticPatterns.size();
    }

    public Map<Key, Set<String>> snapshot() {
        return Map.copyOf(staticPatterns);
    }
}
