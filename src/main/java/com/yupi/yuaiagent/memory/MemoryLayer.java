package com.yupi.yuaiagent.memory;

/**
 * 分层记忆架构 — 记忆层枚举
 * 定义四层记忆的类型，用于 Token 预算分配、日志标识和上下文组装。
 *
 * <p>层级优先级（Token 预算裁剪顺序）：L1 > L2 > L3 > L4
 */
public enum MemoryLayer {

    /** L1：滑动窗口 — 当前会话最近 N 条完整消息 */
    SLIDING_WINDOW("当前上下文", "最近对话消息的滑动窗口，保持当前会话连贯性"),

    /** L2：结构化事实存储 — 用户长期身份/偏好/目标等键值对 */
    FACT_STORE("用户事实", "结构化的用户长期事实（身份、偏好、目标等），精确匹配"),

    /** L3：轻量化摘要 — 近期对话提取的要点清单 */
    SUMMARY("对话摘要", "近期对话提炼的要点清单，包含话题、决策和待办事项"),

    /** L4：历史经验/案例 — 向量化语义检索 */
    EXPERIENCE("历史经验", "过往成功/失败案例的向量化存储，语义模糊匹配");

    private final String displayName;
    private final String description;

    MemoryLayer(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 获取层的中文显示名称（用于 UI 和日志）
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取层的描述信息（用于日志和调试）
     */
    public String getDescription() {
        return description;
    }
}
