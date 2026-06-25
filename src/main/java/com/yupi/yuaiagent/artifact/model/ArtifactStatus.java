package com.yupi.yuaiagent.artifact.model;

/**
 * 交付物状态枚举
 *
 * @author jsq
 */
public enum ArtifactStatus {

    // ─── 生命周期状态（新增） ───
    DRAFT("草稿"),
    REVIEWING("审核中"),
    APPROVED("已批准"),
    PUBLISHED("已发布"),
    ARCHIVED("已归档"),

    // ─── 兼容旧状态 ───
    PENDING("生产中"),
    READY("可被消费"),
    CONSUMED("已被消费");

    private final String description;

    ArtifactStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
