package com.yupi.yuaiagent.profile.model;

/**
 * 沟通偏好枚举
 * 表示用户期望的沟通方式
 *
 * @author jsq
 */
public enum CommunicationPreference {

    /**
     * 简洁
     */
    CONCISE("简洁"),

    /**
     * 详细
     */
    DETAILED("详细");

    private final String description;

    CommunicationPreference(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
