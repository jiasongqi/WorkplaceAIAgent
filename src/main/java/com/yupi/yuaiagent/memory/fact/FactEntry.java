package com.yupi.yuaiagent.memory.fact;

import java.time.Instant;

/**
 * 用户事实条目 — Fact Store (L2) 的核心数据模型。
 *
 * <p>每条事实以 {@code key} 为唯一标识（同一 userId 内），支持 upsert 语义。
 * 记录来源对话 ID 和最后更新时间，便于溯源和变更审计。
 *
 * @param key                    事实键名（如 "name", "budget", "industry"）
 * @param value                  事实值
 * @param category               事实类别（IDENTITY, CAREER, PREFERENCES, GOALS, CONSTRAINTS）
 * @param sourceConversationId   产生该事实的对话 ID
 * @param updatedAt              最后更新时间
 */
public record FactEntry(
        String key,
        String value,
        FactCategory category,
        String sourceConversationId,
        Instant updatedAt
) {

    /**
     * 创建一个更新了值和时间戳的新 FactEntry（保留 key 和 category）。
     *
     * @param newValue              新的事实值
     * @param newConversationId     新的来源对话 ID
     * @param newUpdatedAt          新的更新时间
     * @return 更新后的 FactEntry 实例
     */
    public FactEntry withUpdatedValue(String newValue, String newConversationId, Instant newUpdatedAt) {
        return new FactEntry(this.key, newValue, this.category, newConversationId, newUpdatedAt);
    }
}
