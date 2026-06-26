package com.yupi.yuaiagent.tools.registry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool Discovery — automatically discovers and registers tools from Spring context.
 *
 * <p>Discovery mechanisms:</p>
 * <ul>
 *     <li>Auto-register all ToolCallback beans from Spring context</li>
 *     <li>Scan for @Tool annotated methods (future)</li>
 *     <li>Load from configuration (future)</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolDiscovery {

    private final ToolRegistry toolRegistry;

    /**
     * Auto-discover and register all ToolCallback beans.
     *
     * @param toolCallbacks all ToolCallback beans from Spring context
     */
    @Autowired(required = false)
    public void discoverFromSpringContext(List<ToolCallback> toolCallbacks) {
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            log.info("[ToolDiscovery] No ToolCallback beans found in Spring context");
            return;
        }

        log.info("[ToolDiscovery] Discovering tools from Spring context: {} beans", toolCallbacks.size());

        for (ToolCallback callback : toolCallbacks) {
            try {
                registerToolCallback(callback);
            } catch (Exception e) {
                log.warn("[ToolDiscovery] Failed to register tool: {}", e.getMessage());
            }
        }

        log.info("[ToolDiscovery] Discovery complete: {} tools registered", toolRegistry.getAll().size());
    }

    /**
     * Register a single ToolCallback.
     */
    private void registerToolCallback(ToolCallback callback) {
        // Extract tool name from callback
        String name = extractToolName(callback);
        if (name == null || name.isBlank()) {
            log.warn("[ToolDiscovery] Skipping tool with empty name");
            return;
        }

        // Skip if already registered
        if (toolRegistry.isRegistered(name)) {
            log.debug("[ToolDiscovery] Tool '{}' already registered, skipping", name);
            return;
        }

        // Extract description
        String description = extractToolDescription(callback);

        // Infer capabilities from name
        Set<String> capabilities = inferCapabilities(name);

        // Create definition
        ToolDefinition definition = ToolDefinition.of(name, description, capabilities, callback);

        // Register
        toolRegistry.register(definition);
    }

    /**
     * Extract tool name from callback.
     */
    private String extractToolName(ToolCallback callback) {
        try {
            // Try to get name from callback metadata
            // Spring AI ToolCallback has getToolDefinition() method
            var definition = callback.getToolDefinition();
            if (definition != null) {
                return definition.name();
            }
        } catch (Exception e) {
            log.debug("[ToolDefinition] Could not extract name from callback: {}", e.getMessage());
        }

        // Fallback: use class name
        return callback.getClass().getSimpleName();
    }

    /**
     * Extract tool description from callback.
     */
    private String extractToolDescription(ToolCallback callback) {
        try {
            var definition = callback.getToolDefinition();
            if (definition != null) {
                return definition.description();
            }
        } catch (Exception e) {
            log.debug("[ToolDiscovery] Could not extract description: {}", e.getMessage());
        }
        return "No description available";
    }

    /**
     * Infer capabilities from tool name.
     */
    private Set<String> inferCapabilities(String name) {
        Set<String> capabilities = new HashSet<>();
        String lower = name.toLowerCase();

        // Common capability patterns
        if (lower.contains("search") || lower.contains("query")) {
            capabilities.add("search");
        }
        if (lower.contains("file") || lower.contains("read") || lower.contains("write")) {
            capabilities.add("file");
        }
        if (lower.contains("web") || lower.contains("http") || lower.contains("url")) {
            capabilities.add("web");
        }
        if (lower.contains("terminal") || lower.contains("command") || lower.contains("exec")) {
            capabilities.add("terminal");
        }
        if (lower.contains("pdf") || lower.contains("document")) {
            capabilities.add("document");
        }
        if (lower.contains("download") || lower.contains("resource")) {
            capabilities.add("download");
        }
        if (lower.contains("terminate") || lower.contains("stop")) {
            capabilities.add("control");
        }

        return capabilities;
    }
}
