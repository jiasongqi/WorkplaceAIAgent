package com.yupi.yuaiagent.trace.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a complete execution trace for a single user request.
 * <p>
 * Lifecycle: created via {@link #start()}, spans appended during execution,
 * finalized via {@link #finalizeStatus()} which derives the trace-level status
 * from its constituent spans.
 *
 * @author jsq
 */
public class ExecutionTrace {

    private final String traceId;
    private final String userId;
    private final String chatId;
    private final String requestId;
    private final Instant startTime;

    private TraceStatus status;
    private Instant endTime;
    private final List<TraceSpan> spans = new ArrayList<>();

    private ExecutionTrace(String userId, String chatId, String requestId) {
        this.traceId = UUID.randomUUID().toString().replace("-", "");
        this.userId = userId;
        this.chatId = chatId;
        this.requestId = requestId;
        this.startTime = Instant.now();
        this.status = TraceStatus.RUNNING;
    }

    /**
     * Factory method to create and start a new trace.
     *
     * @param userId    the owning user ID (nullable for anonymous)
     * @param chatId    the chat session ID
     * @param requestId the HTTP request ID (for correlation)
     * @return a new RUNNING trace
     */
    public static ExecutionTrace start(String userId, String chatId, String requestId) {
        return new ExecutionTrace(userId, chatId, requestId);
    }

    /**
     * Derives the trace-level status from the constituent spans (Req 2.8).
     * <p>
     * Rules:
     * <ul>
     *   <li>If any span is RUNNING → trace is RUNNING</li>
     *   <li>If any span is FAILED → trace is FAILED</li>
     *   <li>If all spans are SUCCESS or SKIPPED → trace is SUCCESS</li>
     *   <li>If no spans exist → trace is SUCCESS</li>
     * </ul>
     * Also sets the endTime if the derived status is terminal.
     */
    public void finalizeStatus() {
        if (status.isTerminal()) {
            return;
        }

        boolean hasRunning = false;
        boolean hasFailed = false;

        for (TraceSpan span : spans) {
            if (span.getStatus() == TraceStepStatus.RUNNING) {
                hasRunning = true;
            } else if (span.getStatus() == TraceStepStatus.FAILED) {
                hasFailed = true;
            }
        }

        if (hasRunning) {
            this.status = TraceStatus.RUNNING;
        } else if (hasFailed) {
            this.status = TraceStatus.FAILED;
            this.endTime = Instant.now();
        } else {
            this.status = TraceStatus.SUCCESS;
            this.endTime = Instant.now();
        }
    }

    /**
     * Marks the trace as cancelled.
     */
    public void cancel() {
        if (!status.isTerminal()) {
            this.status = TraceStatus.CANCELLED;
            this.endTime = Instant.now();
        }
    }

    /**
     * Marks the trace as failed with an error message.
     */
    public void fail() {
        if (!status.isTerminal()) {
            this.status = TraceStatus.FAILED;
            this.endTime = Instant.now();
        }
    }

    // --- getters ---

    public String getTraceId() {
        return traceId;
    }

    public String getUserId() {
        return userId;
    }

    public String getChatId() {
        return chatId;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public TraceStatus getStatus() {
        return status;
    }

    public Instant getEndTime() {
        return endTime;
    }

    /**
     * Returns an unmodifiable view of the spans list.
     */
    public List<TraceSpan> getSpans() {
        return Collections.unmodifiableList(spans);
    }

    /**
     * Adds a span to this trace. Called by TraceContext.
     */
    public void addSpan(TraceSpan span) {
        spans.add(span);
    }
}
