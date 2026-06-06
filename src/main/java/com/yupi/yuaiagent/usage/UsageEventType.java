package com.yupi.yuaiagent.usage;

/**
 * Usage event type for tracking.
 *
 * @author jsq
 */
public enum UsageEventType {

    CHAT("普通对话"),
    RAG("RAG 知识库查询"),
    TOOL_CALL("工具调用"),
    DOCUMENT_UPLOAD("文档上传"),
    EXPORT("数据导出"),
    COMPARE("Agent 对比"),
    QUALITY_REVIEW("质量审查");

    private final String displayName;

    UsageEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
