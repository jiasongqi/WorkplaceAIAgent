package com.yupi.yuaiagent.guard;

import lombok.extern.slf4j.Slf4j;

/**
 * Tracks consecutive tool/think failures inside one Agent run.
 * When threshold is hit, the Agent should stop or escalate to HITL
 * (mm_agent_tutorial Ch1: avoid retry loops burning budget).
 */
@Slf4j
public class ConsecutiveFailureGuard {

    private final int maxConsecutive;
    private int consecutiveFailures;
    private String lastFailureSummary = "";

    public ConsecutiveFailureGuard(int maxConsecutive) {
        this.maxConsecutive = Math.max(1, maxConsecutive);
    }

    public void recordSuccess() {
        consecutiveFailures = 0;
        lastFailureSummary = "";
    }

    public void recordFailure(String summary) {
        consecutiveFailures++;
        lastFailureSummary = summary != null ? summary : "";
        log.warn("[ConsecutiveFailure] count={}/{} summary={}",
                consecutiveFailures, maxConsecutive, truncate(lastFailureSummary, 120));
    }

    public boolean shouldStop() {
        return consecutiveFailures >= maxConsecutive;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public int getMaxConsecutive() {
        return maxConsecutive;
    }

    public String getLastFailureSummary() {
        return lastFailureSummary;
    }

    public String stopMessage() {
        return String.format(
                "连续失败 %d 次，已自动终止以避免死循环烧 Token。最后失败原因：%s。"
                        + "请调整目标后重试，或转人工接管。",
                consecutiveFailures,
                truncate(lastFailureSummary, 200));
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isBlank()) {
            return "(未知)";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
