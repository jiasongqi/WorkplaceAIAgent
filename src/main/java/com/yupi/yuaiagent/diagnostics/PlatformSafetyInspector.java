package com.yupi.yuaiagent.diagnostics;

import com.yupi.yuaiagent.permission.ToolNameMatcher;
import com.yupi.yuaiagent.permission.model.PermissionProfile;
import com.yupi.yuaiagent.registry.AgentDescriptor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Inspects declarative Agent and permission metadata without changing authorization decisions.
 */
@Component
public class PlatformSafetyInspector {

    public PlatformSafetyReport inspect(Collection<String> toolNames,
                                        Collection<PermissionProfile> permissionProfiles,
                                        Collection<AgentDescriptor> agents) {
        Set<String> registeredTools = normalizedStrings(toolNames);
        Collection<PermissionProfile> safeProfiles = permissionProfiles == null ? Set.of() : permissionProfiles;
        Collection<AgentDescriptor> safeAgents = agents == null ? Set.of() : agents;

        Map<String, Set<String>> unmatchedPatterns = findUnmatchedPatterns(registeredTools, safeProfiles);
        Set<String> profileCodes = new LinkedHashSet<>();
        for (PermissionProfile profile : safeProfiles) {
            if (profile != null && profile.getAgentCode() != null && !profile.getAgentCode().isBlank()) {
                profileCodes.add(profile.getAgentCode());
            }
        }

        Set<String> missingProfiles = new TreeSet<>();
        for (AgentDescriptor agent : safeAgents) {
            if (agent == null || agent.getAgentCode() == null || agent.getAgentCode().isBlank()) {
                continue;
            }
            String profileCode = agent.getPermissionProfile();
            if (profileCode != null && !profileCode.isBlank() && !profileCodes.contains(profileCode)) {
                missingProfiles.add(agent.getAgentCode() + " -> " + profileCode);
            }
        }

        return new PlatformSafetyReport(unmatchedPatterns, missingProfiles);
    }

    private static Map<String, Set<String>> findUnmatchedPatterns(
            Set<String> toolNames,
            Collection<PermissionProfile> profiles
    ) {
        Map<String, Set<String>> unmatched = new LinkedHashMap<>();
        for (PermissionProfile profile : profiles) {
            if (profile == null || profile.getAgentCode() == null) {
                continue;
            }
            Set<String> patterns = profile.getAllowedToolPatterns();
            if (patterns == null) {
                continue;
            }
            for (String pattern : patterns) {
                boolean matches = toolNames.stream().anyMatch(toolName -> ToolNameMatcher.matches(pattern, toolName));
                if (!matches) {
                    unmatched.computeIfAbsent(profile.getAgentCode(), ignored -> new TreeSet<>()).add(pattern);
                }
            }
        }
        return unmatched;
    }

    private static Set<String> normalizedStrings(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> normalized = new TreeSet<>();
        values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(normalized::add);
        return normalized;
    }
}
