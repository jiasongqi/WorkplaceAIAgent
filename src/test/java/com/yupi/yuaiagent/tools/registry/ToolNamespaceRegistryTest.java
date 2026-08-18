package com.yupi.yuaiagent.tools.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolNamespaceRegistryTest {

    @Test
    void resolvesWildcardFromExplicitCapabilities() {
        ToolNamespaceRegistry registry = new ToolNamespaceRegistry(List.of(
                ToolDefinition.of("readFile", "Read a file", Set.of("file"), null),
                ToolDefinition.of("writeFile", "Write a file", Set.of("file"), null),
                ToolDefinition.of("searchWeb", "Search the web", Set.of("web"), null)
        ));

        assertEquals(Set.of("readFile", "writeFile"), registry.resolve("file.*"));
        assertEquals(Set.of("searchWeb"), registry.resolve("web.*"));
    }

    @Test
    void metadataNamespacesExtendCapabilitiesWithoutNameGuessing() {
        ToolNamespaceRegistry registry = new ToolNamespaceRegistry(List.of(
                ToolDefinition.of(
                        "searchKnowledgeBase",
                        "Search knowledge",
                        Set.of("search"),
                        null,
                        Map.of(ToolNamespaceRegistry.NAMESPACES_METADATA_KEY, List.of("rag"))
                )
        ));

        assertEquals(Set.of("searchKnowledgeBase"), registry.resolve("rag.*"));
        assertEquals(Set.of(), registry.resolve("calendar.*"));
    }

    @Test
    void exactNamesAndGlobalWildcardResolveDeterministically() {
        ToolNamespaceRegistry registry = new ToolNamespaceRegistry(List.of(
                ToolDefinition.of("searchWeb", "Search", Set.of("web"), null),
                ToolDefinition.of("readFile", "Read", Set.of("file"), null)
        ));

        assertEquals(Set.of("searchWeb"), registry.resolve("searchWeb"));
        assertEquals(Set.of("readFile", "searchWeb"), registry.resolve("*"));
        assertEquals(Set.of(), registry.resolve("missingTool"));
    }

    @Test
    void nullAndBlankLookupsAreSafe() {
        ToolNamespaceRegistry registry = new ToolNamespaceRegistry(null);

        assertEquals(Set.of(), registry.resolve(null));
        assertEquals(Set.of(), registry.resolve(" "));
        assertEquals(Set.of(), registry.namespacesFor(null));
        assertEquals(Set.of(), registry.namespacesFor("missingTool"));
    }
}
