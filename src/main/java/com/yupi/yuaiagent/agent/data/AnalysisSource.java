package com.yupi.yuaiagent.agent.data;

/**
 * 数据分析师的输入来源枚举
 *
 * @author jsq
 */
public enum AnalysisSource {

    /**
     * 用户对话历史
     */
    CONVERSATION("对话历史"),

    /**
     * 用户上传文档
     */
    UPLOADED_DOCUMENT("上传文档");

    private final String description;

    AnalysisSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
