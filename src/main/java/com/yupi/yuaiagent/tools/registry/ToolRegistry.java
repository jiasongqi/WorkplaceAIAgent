package com.yupi.yuaiagent.tools.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tool Registry — central registry for dynamic tool management.
 *
 * <p>Provides:</p>
 * <ul>
 *     <li>Dynamic tool registration and deregistration</li>
 *     <li>Tool discovery by name or capability</li>
 *     <li>Tool health monitoring</li>
 *     <li>Tool filtering and querying</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@Slf4j
@Component
public class ToolRegistry {

    /** Name → ToolDefinition */
    private final ConcurrentHashMap<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /** Capability → Set of tool names (reverse index) */
    private final ConcurrentHashMap<String, Set<String>> capabilityIndex = new ConcurrentHashMap<>();

    /**
     * Register a tool.
     *
     * @param definition tool definition
     * @return true if registered successfully, false if name already exists
     */
    public boolean register(ToolDefinition definition) {
        if (definition == null || definition.name() == null || definition.name().isBlank()) {
            log.warn("[ToolRegistry] Attempted to register null or unnamed tool");
            return false;
        }

        String name = definition.name();

        // Check if already registered
        if (tools.containsKey(name)) {
            log.warn("[ToolRegistry] Tool '{}' already registered, skipping", name);
            return false;
        }

        // Register
        tools.put(name, definition);

        // Update capability index
        for (String capability : definition.capabilities()) {
            capabilityIndex.computeIfAbsent(capability, k -> ConcurrentHashMap.newKeySet()).add(name);
        }

        log.info("[ToolRegistry] Registered tool: name={}, capabilities={}", name, definition.capabilities());
        return true;
    }

    /**
     * Deregister a tool.
     *
     * @param name tool name
     * @return true if deregistered successfully
     */
    public boolean deregister(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        ToolDefinition removed = tools.remove(name);
        if (removed == null) {
            return false;
        }

        // Remove from capability index
        for (String capability : removed.capabilities()) {
            Set<String> toolNames = capabilityIndex.get(capability);
            if (toolNames != null) {
                toolNames.remove(name);
                if (toolNames.isEmpty()) {
                    capabilityIndex.remove(capability);
                }
            }
        }

        log.info("[ToolRegistry] Deregistered tool: name={}", name);
        return true;
    }

    /**
     * Get a tool by name.
     *
     * @param name tool name
     * @return tool definition or empty
     */
    public Optional<ToolDefinition> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * Get all registered tools.
     *
     * @return unmodifiable collection of all tools
     */
    public Collection<ToolDefinition> getAll() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * Get all healthy and available tools.
     *
     * @return list of available tools
     */
    public List<ToolDefinition> getAvailable() {
        return tools.values().stream()
                .filter(ToolDefinition::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Get tools by capability.
     *
     * @param capability capability tag
     * @return list of tools with the capability
     */
    public List<ToolDefinition> getByCapability(String capability) {
        Set<String> toolNames = capabilityIndex.get(capability);
        if (toolNames == null || toolNames.isEmpty()) {
            return Collections.emptyList();
        }

        return toolNames.stream()
                .map(tools::get)
                .filter(Objects::nonNull)
                .filter(ToolDefinition::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Get tools by multiple capabilities (intersection).
     *
     * @param capabilities required capabilities
     * @return list of tools with all capabilities
     */
    public List<ToolDefinition> getByCapabilities(Set<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return getAvailable();
        }

        return tools.values().stream()
                .filter(ToolDefinition::isAvailable)
                .filter(tool -> tool.capabilities().containsAll(capabilities))
                .collect(Collectors.toList());
    }

    /**
     * Get all registered capability tags.
     *
     * @return set of capabilities
     */
    public Set<String> getCapabilities() {
        return Collections.unmodifiableSet(capabilityIndex.keySet());
    }

    /**
     * Update tool health status.
     *
     * @param name   tool name
     * @param status new health status
     * @return true if updated successfully
     */
    public boolean updateHealthStatus(String name, ToolDefinition.HealthStatus status) {
        ToolDefinition existing = tools.get(name);
        if (existing == null) {
            return false;
        }

        tools.put(name, existing.withHealthStatus(status));
        log.debug("[ToolRegistry] Updated health status: name={}, status={}", name, status);
        return true;
    }

    /**
     * Get registry statistics.
     *
     * @return statistics map
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTools", tools.size());
        stats.put("availableTools", getAvailable().size());
        stats.put("totalCapabilities", capabilityIndex.size());
        stats.put("capabilities", getCapabilities());
        return stats;
    }

    /**
     * Check if a tool is registered.
     *
     * @param name tool name
     * @return true if registered
     */
    public boolean isRegistered(String name) {
        return tools.containsKey(name);
    }

    /**
     * Clear all registrations (for testing).
     */
    public void clear() {
        tools.clear();
        capabilityIndex.clear();
        log.info("[ToolRegistry] Cleared all registrations");
    }
}
