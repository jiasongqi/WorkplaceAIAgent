package com.yupi.yuaiagent.workflow.runtime;

/**
 * 工作流实例状态
 *
 * @author jsq
 */
public enum WorkflowStatus {
    PENDING("等待启动"),
    RUNNING("执行中"),
    PAUSED("已暂停（等待审批）"),
    COMPLETED("已完成"),
    FAILED("失败"),
    CANCELLED("已取消");

    private final String description;

    WorkflowStatus(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
