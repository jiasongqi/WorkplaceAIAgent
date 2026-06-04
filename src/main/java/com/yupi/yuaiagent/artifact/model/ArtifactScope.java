package com.yupi.yuaiagent.artifact.model;

/**
 * 交付物作用域枚举
 *
 * @author jsq
 */
public enum ArtifactScope {

    /**
     * 用户画像作用域：按 userId 长期存储，跨会话累积
     */
    USER_PROFILE("用户画像"),

    /**
     * 任务作用域：按 chatId 会话级存储
     */
    TASK("会话任务");

    private final String description;

    ArtifactScope(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
