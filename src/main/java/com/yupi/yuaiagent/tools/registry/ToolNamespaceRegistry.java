package com.yupi.yuaiagent.tools.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only namespace index derived from explicit tool metadata.
 *
 * <p>Capabilities are treated as namespaces. Tools may contribute additional namespaces through
 * the {@value #NAMESPACES_METADATA_KEY} metadata field. Tool names are never inspected or guessed.
 */
public final class ToolNamespaceRegistry {

    public static final String NAMESPACES_METADATA_KEY = "namespaces";

    private final Set<String> toolNames;
    private final Map<String, Set<String>> toolsByNamespace;

    public ToolNamespaceRegistry(Collection<ToolDefinition> definitions) {
        Set<String> names = new TreeSet<>();
        Map<String, Set<String>> index = new LinkedHashMap<>();

        if (definitions != null) {
            for (ToolDefinition definition : definitions) {
                if (definition == null || definition.name() == null || definition.name().isBlank()) {
                    continue;
                }
                names.add(definition.name());
                addNamespaces(index, definition.name(), definition.capabilities());
                addNamespaces(index, definition.name(), metadataNamespaces(definition.metadata()));
            }
        }

        this.toolNames = Collections.unmodifiableSet(names);
        Map<String, Set<String>> immutableIndex = new LinkedHashMap<>();
        index.forEach((namespace, tools) ->
                immutableIndex.put(namespace, Collections.unmodifiableSet(new TreeSet<>(tools))));
        this.toolsByNamespace = Collections.unmodifiableMap(immutableIndex);
    }

    /**
     * Resolves a global wildcard, exact tool name, or namespace wildcard to tool names.
     */
    public Set<String> resolve(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return Set.of();
        }
        if ("*".equals(pattern)) {
            return toolNames;
        }
        if (toolNames.contains(pattern)) {
            return Set.of(pattern);
        }
        if (pattern.endsWith(".*")) {
            String namespace = pattern.substring(0, pattern.length() - 2);
            return toolsByNamespace.getOrDefault(namespace, Set.of());
        }
        return Set.of();
    }

    public Set<String> namespacesFor(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Set.of();
        }
        Set<String> namespaces = new TreeSet<>();
        toolsByNamespace.forEach((namespace, names) -> {
            if (names.contains(toolName)) {
                namespaces.add(namespace);
            }
        });
        return Collections.unmodifiableSet(namespaces);
    }

    private static void addNamespaces(Map<String, Set<String>> index, String toolName,
                                      Collection<String> namespaces) {
        if (namespaces == null) {
            return;
        }
        for (String namespace : namespaces) {
            if (namespace == null || namespace.isBlank()) {
                continue;
            }
            index.computeIfAbsent(namespace.trim(), ignored -> new LinkedHashSet<>()).add(toolName);
        }
    }

    private static Collection<String> metadataNamespaces(Map<String, Object> metadata) {
        if (metadata == null) {
            return List.of();
        }
        Object value = metadata.get(NAMESPACES_METADATA_KEY);
        if (value instanceof String namespace) {
            return List.of(namespace);
        }
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }
}
