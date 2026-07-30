package com.yupi.yuaiagent.workflow.dag;

/**
 * Progress callbacks for DAG node lifecycle (SSE agent-progress).
 */
public interface DagProgressListener {

    void onNodeStarted(DagNodeSpec node);

    void onNodeFinished(DagNodeSpec node, boolean success, long durationMs);

    /** No-op listener. */
    static DagProgressListener noop() {
        return new DagProgressListener() {
            @Override
            public void onNodeStarted(DagNodeSpec node) {}

            @Override
            public void onNodeFinished(DagNodeSpec node, boolean success, long durationMs) {}
        };
    }
}
