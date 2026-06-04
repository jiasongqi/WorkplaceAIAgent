package com.yupi.yuaiagent.quality;

/**
 * Risk level assessed by QualityGuardAgent.
 *
 * @author jsq
 */
public enum RiskLevel {

    LOW("低风险", "日常建议，无风险"),
    MEDIUM("中风险", "需要用户自行判断"),
    HIGH("高风险", "建议咨询专业人士"),
    CRITICAL("极高风险", "建议阻断回答");

    private final String displayName;
    private final String description;

    RiskLevel(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isBlocking() {
        return this == CRITICAL;
    }
}
