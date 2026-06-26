package com.yupi.yuaiagent.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * MCP Tool Call Audit Log — records every MCP tool call with input/output/latency/success.
 *
 * <p>Provides accountability and debugging for external MCP service interactions.
 * Keeps a bounded ring buffer (last 1000 entries) to prevent memory exhaustion.</p>
 *
 * @author jsq
 */
@Slf4j
@Component
public class McpAuditLog {

    private static final int MAX_ENTRIES = 1000;
    private final Deque<AuditEntry> entries = new ConcurrentLinkedDeque<>();

    /**
     * Record an MCP tool call.
     */
    public void record(String serverName, String toolName, String inputSummary,
                       String outputSummary, boolean success, long latencyMs, String errorMessage) {
        AuditEntry entry = new AuditEntry(
                LocalDateTime.now(), serverName, toolName,
                truncate(inputSummary, 200), truncate(outputSummary, 200),
                success, latencyMs, errorMessage);

        entries.addLast(entry);

        // Evict old entries
        while (entries.size() > MAX_ENTRIES) {
            entries.pollFirst();
        }

        if (!success) {
            log.warn("[McpAudit] FAILED: server={}, tool={}, latency={}ms, error={}",
                    serverName, toolName, latencyMs, errorMessage);
        } else {
            log.debug("[McpAudit] OK: server={}, tool={}, latency={}ms",
                    serverName, toolName, latencyMs);
        }
    }

    /**
     * Get success rate for a server.
     */
    public double getServerSuccessRate(String serverName) {
        long total = entries.stream().filter(e -> e.serverName.equals(serverName)).count();
        long success = entries.stream().filter(e -> e.serverName.equals(serverName) && e.success).count();
        return total == 0 ? -1.0 : (double) success / total;
    }

    /**
     * Get success rate for a specific tool.
     */
    public double getToolSuccessRate(String serverName, String toolName) {
        long total = entries.stream()
                .filter(e -> e.serverName.equals(serverName) && e.toolName.equals(toolName)).count();
        long success = entries.stream()
                .filter(e -> e.serverName.equals(serverName) && e.toolName.equals(toolName) && e.success).count();
        return total == 0 ? -1.0 : (double) success / total;
    }

    /**
     * Get average latency for a tool.
     */
    public long getAvgLatency(String serverName, String toolName) {
        return entries.stream()
                .filter(e -> e.serverName.equals(serverName) && e.toolName.equals(toolName))
                .mapToLong(e -> e.latencyMs)
                .average()
                .orElse(-1.0).longValue();
    }

    /**
     * Get recent failures (for alerting).
     */
    public List<AuditEntry> getRecentFailures(int limit) {
        return entries.stream()
                .filter(e -> !e.success)
                .sorted((a, b) -> b.timestamp.compareTo(a.timestamp))
                .limit(limit)
                .toList();
    }

    /**
     * Get all entries for a server.
     */
    public List<AuditEntry> getByServer(String serverName, int limit) {
        return entries.stream()
                .filter(e -> e.serverName.equals(serverName))
                .sorted((a, b) -> b.timestamp.compareTo(a.timestamp))
                .limit(limit)
                .toList();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /**
     * Audit entry record.
     */
    public record AuditEntry(
            LocalDateTime timestamp,
            String serverName,
            String toolName,
            String inputSummary,
            String outputSummary,
            boolean success,
            long latencyMs,
            String errorMessage
    ) {}
}
