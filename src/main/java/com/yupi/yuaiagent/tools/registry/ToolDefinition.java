package com.yupi.yuaiagent.tools.registry;

import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Tool Definition — metadata describing a registered tool.
 *
 * <p>Contains tool metadata, capabilities, and health status for dynamic discovery.</p>
 *
 * @param name         unique tool name
 * @param description  human-readable description
 * @param capabilities set of capability tags (e.g., "search", "file", "web")
 * @param callback     the actual tool callback
 * @param healthStatus current health status
 * @param registeredAt registration timestamp
 * @param metadata     additional metadata
 */
public record ToolDefinition(
    String name,
    String description,
    Set<String> capabilities,
    ToolCallback callback,
    HealthStatus healthStatus,
    Instant registeredAt,
    Map<String, Object> metadata
) {
    /**
     * Health status of a tool.
     */
    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }

    /**
     * Create a simple tool definition.
     */
    public static ToolDefinition of(String name, String description, Set<String> capabilities, ToolCallback callback) {
        return new ToolDefinition(
            name,
            description,
            capabilities != null ? capabilities : Collections.emptySet(),
            callback,
            HealthStatus.HEALTHY,
            Instant.now(),
            Collections.emptyMap()
        );
    }

    /**
     * Create a tool definition with metadata.
     */
    public static ToolDefinition of(String name, String description, Set<String> capabilities,
                                     ToolCallback callback, Map<String, Object> metadata) {
        return new ToolDefinition(
            name,
            description,
            capabilities != null ? capabilities : Collections.emptySet(),
            callback,
            HealthStatus.HEALTHY,
            Instant.now(),
            metadata != null ? metadata : Collections.emptyMap()
        );
    }

    /**
     * Check if tool has a specific capability.
     */
    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    /**
     * Check if tool is healthy and available.
     */
    public boolean isAvailable() {
        return healthStatus == HealthStatus.HEALTHY || healthStatus == HealthStatus.DEGRADED;
    }

    /**
     * Create a copy with updated health status.
     */
    public ToolDefinition withHealthStatus(HealthStatus newStatus) {
        return new ToolDefinition(name, description, capabilities, callback, newStatus, registeredAt, metadata);
    }
}
