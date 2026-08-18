package com.yupi.yuaiagent.diagnostics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable result of startup platform safety inspection.
 */
public record PlatformSafetyReport(
        Map<String, Set<String>> unmatchedPermissionPatterns,
        Set<String> agentsWithMissingPermissionProfiles
) {

    public PlatformSafetyReport {
        Map<String, Set<String>> patternsCopy = new LinkedHashMap<>();
        if (unmatchedPermissionPatterns != null) {
            unmatchedPermissionPatterns.forEach((agentCode, patterns) ->
                    patternsCopy.put(agentCode, patterns == null
                            ? Set.of()
                            : Collections.unmodifiableSet(new TreeSet<>(patterns))));
        }
        unmatchedPermissionPatterns = Collections.unmodifiableMap(patternsCopy);
        agentsWithMissingPermissionProfiles = agentsWithMissingPermissionProfiles == null
                ? Set.of()
                : Collections.unmodifiableSet(new TreeSet<>(agentsWithMissingPermissionProfiles));
    }

    public boolean hasWarnings() {
        return !unmatchedPermissionPatterns.isEmpty() || !agentsWithMissingPermissionProfiles.isEmpty();
    }
}
