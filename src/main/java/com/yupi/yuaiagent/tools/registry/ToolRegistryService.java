package com.yupi.yuaiagent.tools.registry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool Registry Service — high-level API for tool management and discovery.
 *
 * <p>Provides convenient methods for:</p>
 * <ul>
 *     <li>Tool registration and deregistration</li>
 *     <li>Tool discovery by capability</li>
 *     <li>Tool array creation for agent use</li>
 *     <li>Registry statistics and health</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolRegistryService {

    private final ToolRegistry toolRegistry;

    /**
     * Register a tool.
     *
     * @param name         tool name
     * @param description  tool description
     * @param capabilities capability tags
     * @param callback     tool callback
     * @return true if registered successfully
     */
    public boolean register(String name, String description, Set<String> capabilities, ToolCallback callback) {
        ToolDefinition definition = ToolDefinition.of(name, description, capabilities, callback);
        return toolRegistry.register(definition);
    }

    /**
     * Deregister a tool.
     *
     * @param name tool name
     * @return true if deregistered successfully
     */
    public boolean deregister(String name) {
        return toolRegistry.deregister(name);
    }

    /**
     * Get all available tools as ToolCallback array (for agent use).
     *
     * @return array of tool callbacks
     */
    public ToolCallback[] getToolCallbacks() {
        return toolRegistry.getAvailable().stream()
                .map(ToolDefinition::callback)
                .toArray(ToolCallback[]::new);
    }

    /**
     * Get tools by capability as ToolCallback array.
     *
     * @param capability required capability
     * @return array of tool callbacks
     */
    public ToolCallback[] getToolCallbacksByCapability(String capability) {
        return toolRegistry.getByCapability(capability).stream()
                .map(ToolDefinition::callback)
                .toArray(ToolCallback[]::new);
    }

    /**
     * Get tools by multiple capabilities as ToolCallback array.
     *
     * @param capabilities required capabilities
     * @return array of tool callbacks
     */
    public ToolCallback[] getToolCallbacksByCapabilities(Set<String> capabilities) {
        return toolRegistry.getByCapabilities(capabilities).stream()
                .map(ToolDefinition::callback)
                .toArray(ToolCallback[]::new);
    }

    /**
     * Get tool definition by name.
     *
     * @param name tool name
     * @return tool definition or empty
     */
    public Optional<ToolDefinition> getTool(String name) {
        return toolRegistry.get(name);
    }

    /**
     * Get all tool definitions.
     *
     * @return list of all tools
     */
    public List<ToolDefinition> getAllTools() {
        return new ArrayList<>(toolRegistry.getAll());
    }

    /**
     * Get all available tools.
     *
     * @return list of available tools
     */
    public List<ToolDefinition> getAvailableTools() {
        return toolRegistry.getAvailable();
    }

    /**
     * Get all capabilities.
     *
     * @return set of capabilities
     */
    public Set<String> getCapabilities() {
        return toolRegistry.getCapabilities();
    }

    /**
     * Search tools by name or description.
     *
     * @param query search query
     * @return matching tools
     */
    public List<ToolDefinition> searchTools(String query) {
        if (query == null || query.isBlank()) {
            return getAllTools();
        }

        String lower = query.toLowerCase();
        return toolRegistry.getAll().stream()
                .filter(tool -> tool.name().toLowerCase().contains(lower) ||
                               tool.description().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    /**
     * Update tool health status.
     *
     * @param name   tool name
     * @param status new health status
     * @return true if updated successfully
     */
    public boolean updateHealthStatus(String name, ToolDefinition.HealthStatus status) {
        return toolRegistry.updateHealthStatus(name, status);
    }

    /**
     * Get registry statistics.
     *
     * @return statistics map
     */
    public Map<String, Object> getStats() {
        return toolRegistry.getStats();
    }

    /**
     * Check if a tool is registered.
     *
     * @param name tool name
     * @return true if registered
     */
    public boolean isRegistered(String name) {
        return toolRegistry.isRegistered(name);
    }
}
