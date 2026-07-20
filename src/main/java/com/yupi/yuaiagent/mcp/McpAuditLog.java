package com.yupi.yuaiagent.mcp;

import com.yupi.yuaiagent.repository.entity.McpAuditLogEntity;
import com.yupi.yuaiagent.repository.jpa.McpAuditLogJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool Call Audit Log — JPA persistence for MCP tool calls.
 *
 * @author jsq
 */
@Slf4j
@Component
public class McpAuditLog {

    private final McpAuditLogJpaRepository jpaRepo;

    public McpAuditLog(McpAuditLogJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    /**
     * Record an MCP tool call.
     */
    public void record(String serverName, String toolName, String inputSummary,
                       String outputSummary, boolean success, long latencyMs, String errorMessage) {
        McpAuditLogEntity entity = new McpAuditLogEntity();
        entity.setServerId(serverName);
        entity.setToolName(toolName);
        entity.setInputSummary(truncate(inputSummary, 200));
        entity.setOutputSummary(truncate(outputSummary, 200));
        entity.setStatus(success ? "SUCCESS" : "FAILED");
        entity.setDurationMs((int) latencyMs);
        jpaRepo.save(entity);

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
        List<McpAuditLogEntity> entries = jpaRepo.findByServerId(serverName);
        long total = entries.size();
        long success = entries.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
        return total == 0 ? -1.0 : (double) success / total;
    }

    /**
     * Get success rate for a tool.
     */
    public double getToolSuccessRate(String toolName) {
        List<McpAuditLogEntity> entries = jpaRepo.findByToolName(toolName);
        long total = entries.size();
        long success = entries.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
        return total == 0 ? -1.0 : (double) success / total;
    }

    /**
     * Get recent entries.
     */
    public List<AuditEntry> getRecentEntries(int count) {
        return jpaRepo.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(count)
                .map(this::toDomain)
                .toList();
    }

    /**
     * Get total entry count.
     */
    public int getEntryCount() {
        return (int) jpaRepo.count();
    }

    /**
     * Get statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = jpaRepo.count();
        stats.put("totalEntries", total);
        stats.put("totalSuccess", jpaRepo.findAll().stream().filter(e -> "SUCCESS".equals(e.getStatus())).count());
        stats.put("totalFailed", jpaRepo.findAll().stream().filter(e -> "FAILED".equals(e.getStatus())).count());
        return stats;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private AuditEntry toDomain(McpAuditLogEntity e) {
        return new AuditEntry(
                e.getCreatedAt() != null ? e.getCreatedAt().toLocalDateTime() : null,
                e.getServerId(),
                e.getToolName(),
                e.getInputSummary(),
                e.getOutputSummary(),
                "SUCCESS".equals(e.getStatus()),
                e.getDurationMs() != null ? e.getDurationMs() : 0,
                null
        );
    }

    /**
     * Audit entry record.
     */
    public record AuditEntry(
            java.time.LocalDateTime timestamp,
            String serverName,
            String toolName,
            String inputSummary,
            String outputSummary,
            boolean success,
            long latencyMs,
            String errorMessage
    ) {}
}
