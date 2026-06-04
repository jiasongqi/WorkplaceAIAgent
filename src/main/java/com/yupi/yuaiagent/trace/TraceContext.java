package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.trace.model.ExecutionTrace;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStatus;
import com.yupi.yuaiagent.trace.model.TraceStepStatus;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Request-scoped trace context that manages spans for a single {@link ExecutionTrace}.
 * <p>
 * Thread-confined: created per HTTP request, never shared across threads.
 * All mutating methods are fail-safe — exceptions are caught and logged, never propagated.
 *
 * @author jsq
 */
@Slf4j
public class TraceContext {

    private final ExecutionTrace trace;
    private int nextSequence = 0;
    private volatile SseEmitter sseEmitter;
    private volatile boolean sseClosed = false;

    private TraceContext(ExecutionTrace trace) {
        this.trace = trace;
    }

    /**
     * Creates a new context wrapping the given trace.
     */
    public static TraceContext of(ExecutionTrace trace) {
        return new TraceContext(trace);
    }

    /**
     * Returns a no-op context that silently discards all operations.
     * Used when tracing is disabled or userId/chatId is unavailable.
     */
    public static TraceContext noop() {
        return NoOpTraceContext.INSTANCE;
    }

    /**
     * Binds an SSE emitter to this context for real-time trace event streaming (Req 10.1).
     *
     * @param emitter the SSE emitter to bind
     */
    public void bindSseEmitter(SseEmitter emitter) {
        this.sseEmitter = emitter;
        this.sseClosed = false;
    }

    /**
     * Marks the SSE connection as closed (Req 10.1).
     * After this call, no further trace events will be published.
     */
    public void markSseClosed() {
        this.sseClosed = true;
    }

    /**
     * Returns true if the SSE connection has been marked as closed (Req 10.1).
     */
    public boolean isSseClosed() {
        return sseClosed;
    }

    /**
     * Returns the bound SSE emitter, or null if not bound.
     */
    public SseEmitter getSseEmitter() {
        return sseEmitter;
    }

    /**
     * Appends a new RUNNING span to the trace.
     *
     * @param stepType the category of the step
     * @param label    human-readable label
     * @return the created span
     */
    public TraceSpan appendSpan(TraceStepType stepType, String label) {
        TraceSpan span = new TraceSpan(nextSequence++, stepType, label);
        trace.addSpan(span);
        return span;
    }

    /**
     * Terminates the given span with SUCCESS status.
     */
    public void finishSpan(TraceSpan span) {
        if (span == null || span.isTerminal()) {
            return;
        }
        span.terminate(TraceStepStatus.SUCCESS);
    }

    /**
     * Fails the given span with an error message and marks the trace as failed.
     */
    public void failSpan(TraceSpan span, String errorMessage) {
        if (span == null || span.isTerminal()) {
            return;
        }
        span.fail(errorMessage);
    }

    /**
     * Skips the given span.
     */
    public void skipSpan(TraceSpan span) {
        if (span == null || span.isTerminal()) {
            return;
        }
        span.terminate(TraceStepStatus.SKIPPED);
    }

    /**
     * Finalizes the trace status from constituent spans.
     */
    public void finalizeTrace() {
        trace.finalizeStatus();
    }

    /**
     * Marks the trace as failed.
     */
    public void failTrace() {
        trace.fail();
    }

    /**
     * Returns the underlying trace.
     */
    public ExecutionTrace getTrace() {
        return trace;
    }

    // --- No-op implementation ---

    /**
     * No-op context that silently discards all operations.
     */
    static class NoOpTraceContext extends TraceContext {

        static final NoOpTraceContext INSTANCE = new NoOpTraceContext();

        private NoOpTraceContext() {
            super(ExecutionTrace.start(null, null, null));
        }

        @Override
        public TraceSpan appendSpan(TraceStepType stepType, String label) {
            return null;
        }

        @Override
        public void finishSpan(TraceSpan span) {
            // no-op
        }

        @Override
        public void failSpan(TraceSpan span, String errorMessage) {
            // no-op
        }

        @Override
        public void skipSpan(TraceSpan span) {
            // no-op
        }

        @Override
        public void finalizeTrace() {
            // no-op
        }

        @Override
        public void failTrace() {
            // no-op
        }
    }
}
