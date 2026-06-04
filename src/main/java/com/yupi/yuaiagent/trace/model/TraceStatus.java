package com.yupi.yuaiagent.trace.model;

/**
 * Trace-level status enum.
 * <p>
 * Represents the overall lifecycle of an {@link com.yupi.yuaiagent.trace.model.ExecutionTrace}.
 *
 * @author jsq
 */
public enum TraceStatus {

    /**
     * Trace is actively executing (non-terminal).
     */
    RUNNING("执行中"),

    /**
     * Trace completed successfully (terminal).
     */
    SUCCESS("成功"),

    /**
     * Trace terminated with an error (terminal).
     */
    FAILED("失败"),

    /**
     * Trace was cancelled before completion (terminal).
     */
    CANCELLED("已取消");

    private final String displayName;

    TraceStatus(String displayName) {
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
