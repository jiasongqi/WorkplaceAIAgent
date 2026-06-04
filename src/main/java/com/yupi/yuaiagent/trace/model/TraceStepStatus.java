package com.yupi.yuaiagent.trace.model;

/**
 * Step-level status enum for individual spans within a trace.
 *
 * @author jsq
 */
public enum TraceStepStatus {

    /**
     * Step is actively executing (non-terminal).
     */
    RUNNING("执行中"),

    /**
     * Step completed successfully (terminal).
     */
    SUCCESS("成功"),

    /**
     * Step terminated with an error (terminal).
     */
    FAILED("失败"),

    /**
     * Step was skipped (terminal).
     */
    SKIPPED("已跳过");

    private final String displayName;

    TraceStepStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns true if this status is a terminal state.
     */
    public boolean isTerminal() {
        return this != RUNNING;
    }
}
