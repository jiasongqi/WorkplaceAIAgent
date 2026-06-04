package com.yupi.yuaiagent.artifact.model;

/**
 * 交付物状态枚举
 *
 * @author jsq
 */
public enum ArtifactStatus {

    /**
     * 生产中
     */
    PENDING("生产中"),

    /**
     * 可被消费
     */
    READY("可被消费"),

    /**
     * 已被消费
     */
    CONSUMED("已被消费");

    private final String description;

    ArtifactStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
