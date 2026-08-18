package com.yupi.yuaiagent.permission;

import com.yupi.yuaiagent.pack.PackPreferenceMode;
import com.yupi.yuaiagent.permission.model.PermissionProfile;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Computes effective tool patterns: base ∩ enabled packs, or control-only when all packs are off.
 */
public final class PermissionNarrowingService {

    public enum Mode {
        OFF,
        OBSERVE,
        ENFORCE
    }

    private PermissionNarrowingService() {
    }

    public static Set<String> effectivePatterns(
            PermissionProfile base,
            Set<String> packUnionPatterns,
            PackPreferenceMode preferenceMode
    ) {
        Set<String> control = Set.of(ToolNameMatcher.TERMINATE_TOOL, ToolNameMatcher.ASYNC_STATUS_TOOL);
        if (preferenceMode == PackPreferenceMode.EXPLICIT_ALL_DISABLED) {
            return Set.copyOf(control);
        }
        Set<String> basePatterns = base == null || base.getAllowedToolPatterns() == null
                ? Set.of()
                : Set.copyOf(base.getAllowedToolPatterns());
        if (preferenceMode == PackPreferenceMode.UNSET || packUnionPatterns == null || packUnionPatterns.isEmpty()) {
            return basePatterns;
        }
        Set<String> intersection = new LinkedHashSet<>();
        for (String pattern : packUnionPatterns) {
            if (basePatterns.contains(pattern) || matchesAny(basePatterns, pattern)) {
                intersection.add(pattern);
            }
        }
        intersection.addAll(control);
        return Set.copyOf(intersection);
    }

    public static void rejectNakedWildcard(PermissionProfile profile) {
        if (profile == null || profile.isAdmin() || profile.getAllowedToolPatterns() == null) {
            return;
        }
        if (profile.getAllowedToolPatterns().contains("*")) {
            throw new IllegalStateException("non-admin permission profile cannot use naked *: " + profile.getAgentCode());
        }
    }

    public static boolean allows(Set<String> effectivePatterns, String toolName) {
        if (ToolNameMatcher.isAlwaysAllowed(toolName)) {
            return true;
        }
        if (effectivePatterns == null) {
            return false;
        }
        for (String pattern : effectivePatterns) {
            if (ToolNameMatcher.matches(pattern, toolName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAny(Set<String> basePatterns, String candidate) {
        for (String base : basePatterns) {
            if (base.equals(candidate) || ToolNameMatcher.matches(base, candidate)) {
                return true;
            }
        }
        return false;
    }
}
