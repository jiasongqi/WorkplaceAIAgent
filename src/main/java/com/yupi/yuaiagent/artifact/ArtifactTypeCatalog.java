package com.yupi.yuaiagent.artifact;

import com.yupi.yuaiagent.artifact.model.ArtifactScope;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central catalog for publishable structured artifact types.
 */
public final class ArtifactTypeCatalog {

    private final Map<String, TypeDefinition> definitions;

    public ArtifactTypeCatalog(Map<String, TypeDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    public Optional<TypeDefinition> find(String type) {
        return type == null ? Optional.empty() : Optional.ofNullable(definitions.get(type));
    }

    public static ArtifactTypeCatalog defaults() {
        Map<String, TypeDefinition> types = new LinkedHashMap<>();
        types.put("PROMOTION_PLAN", reusable(ArtifactScope.TASK,
                List.of("GENERAL", "NEGOTIATION"), Duration.ofDays(180)));
        types.put("DATA_ANALYSIS_REPORT", reusable(ArtifactScope.TASK,
                List.of("GENERAL", "RESUME", "NEGOTIATION"), Duration.ofDays(90)));
        types.put("CAREER_COACH_ADVICE", reusable(ArtifactScope.TASK,
                List.of("GENERAL", "RESUME", "NEGOTIATION", "ESCAPE"), Duration.ofDays(90)));
        types.put("USER_PROFILE_SUMMARY", reusable(ArtifactScope.USER_PROFILE,
                List.of("GENERAL", "RESUME", "NEGOTIATION", "ESCAPE", "CONSULTATION"),
                Duration.ofDays(365)));
        types.put("LEARNING_RESOURCE_RECOMMENDATION", reusable(ArtifactScope.TASK,
                List.of("GENERAL", "RESUME"), Duration.ofDays(90)));
        types.put("MULTI_AGENT_DEBATE", processOnly());
        types.put("AGENT_HANDOFF", processOnly());
        return new ArtifactTypeCatalog(types);
    }

    private static TypeDefinition reusable(ArtifactScope scope, List<String> targetAgents, Duration ttl) {
        return new TypeDefinition(true, scope, targetAgents, 1, ttl);
    }

    private static TypeDefinition processOnly() {
        return new TypeDefinition(false, ArtifactScope.TASK, List.of(), 1, null);
    }

    public record TypeDefinition(
            boolean reusable,
            ArtifactScope scope,
            List<String> targetAgents,
            int schemaVersion,
            Duration ttl) {
    }
}
