package com.yupi.yuaiagent.permission;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 权限审计日志 — 记录所有权限决策（通过/拒绝），用于安全审计和合规。
 * <p>
 * V1：内存环形缓冲（最近 1000 条），V2 替换为数据库持久化。
 *
 * @author jsq
 */
@Slf4j
@Component
public class PermissionAuditLog {

    private static final int MAX_ENTRIES = 1000;
    private final ConcurrentLinkedDeque<AuditEntry> entries = new ConcurrentLinkedDeque<>();

    /**
     * 记录一次权限决策
     *
     * @param agentCode  Agent 编码
     * @param toolName   Tool 名称
     * @param decision   决策结果（ALLOWED / DENIED）
     * @param reason     决策原因
     */
    public void record(String agentCode, String toolName, Decision decision, String reason) {
        AuditEntry entry = AuditEntry.builder()
                .timestamp(LocalDateTime.now())
                .agentCode(agentCode)
                .toolName(toolName)
                .decision(decision)
                .reason(reason)
                .build();

        entries.addLast(entry);

        // 超出容量时移除最旧的记录
        while (entries.size() > MAX_ENTRIES) {
            entries.pollFirst();
        }

        if (decision == Decision.DENIED) {
            log.warn("[AuditLog] DENIED agent={} tool={} reason={}", agentCode, toolName, reason);
        } else {
            log.debug("[AuditLog] ALLOWED agent={} tool={}", agentCode, toolName);
        }
    }

    /**
     * 记录 ALLOWED 决策
     */
    public void recordAllowed(String agentCode, String toolName) {
        record(agentCode, toolName, Decision.ALLOWED, "Pattern matched");
    }

    /**
     * 记录 DENIED 决策
     */
    public void recordDenied(String agentCode, String toolName, String reason) {
        record(agentCode, toolName, Decision.DENIED, reason);
    }

    /**
     * 获取最近的审计记录
     *
     * @param limit 最大返回条数
     */
    public List<AuditEntry> getRecent(int limit) {
        List<AuditEntry> result = new ArrayList<>(entries);
        Collections.reverse(result); // 最新在前
        return result.subList(0, Math.min(limit, result.size()));
    }

    /**
     * 获取指定 Agent 的审计记录
     */
    public List<AuditEntry> getByAgent(String agentCode, int limit) {
        List<AuditEntry> result = entries.stream()
                .filter(e -> agentCode.equals(e.getAgentCode()))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .toList();
        return result;
    }

    /**
     * 获取 DENIED 记录
     */
    public List<AuditEntry> getDenied(int limit) {
        return entries.stream()
                .filter(e -> e.getDecision() == Decision.DENIED)
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .toList();
    }

    /**
     * 获取总记录数
     */
    public int size() {
        return entries.size();
    }

    /**
     * 权限决策结果
     */
    public enum Decision {
        ALLOWED,
        DENIED
    }

    /**
     * 审计日志条目
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditEntry {
        private LocalDateTime timestamp;
        private String agentCode;
        private String toolName;
        private Decision decision;
        private String reason;
    }
}
