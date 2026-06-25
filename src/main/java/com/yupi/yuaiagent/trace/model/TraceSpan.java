package com.yupi.yuaiagent.trace.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single span (step) within an {@link ExecutionTrace}.
 * <p>
 * Lifecycle: created with {@link TraceStepStatus#RUNNING}, transitioned to a terminal state
 * via {@link #terminate(TraceStepStatus)}. Once terminal, timing fields are frozen and
 * further calls to {@link #terminate} are no-ops.
 *
 * @author jsq
 */
public class TraceSpan {

    private final int sequence;
    private final TraceStepType stepType;
    private final String label;
    private final Instant startTime;

    private TraceStepStatus status;
    private Instant endTime;
    private String errorMessage;
    private final Map<String, String> metadata = new LinkedHashMap<>();

    /**
     * Creates a new span in RUNNING state.
     *
     * @param sequence ordinal position within the trace (0-based)
     * @param stepType the category of this step
     * @param label    human-readable label for this step
     */
    @JsonCreator
    public TraceSpan(
            @JsonProperty("sequence") int sequence,
            @JsonProperty("stepType") TraceStepType stepType,
            @JsonProperty("label") String label,
            @JsonProperty("startTime") Instant startTime,
            @JsonProperty("status") TraceStepStatus status,
            @JsonProperty("endTime") Instant endTime,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("metadata") Map<String, String> metadata) {
        this.sequence = sequence;
        this.stepType = stepType;
        this.label = label;
        this.startTime = startTime;
        this.status = status;
        this.endTime = endTime;
        this.errorMessage = errorMessage;
        if (metadata != null) {
            this.metadata.putAll(metadata);
        }
    }

    public TraceSpan(int sequence, TraceStepType stepType, String label) {
        this.sequence = sequence;
        this.stepType = stepType;
        this.label = label;
        this.startTime = Instant.now();
        this.status = TraceStepStatus.RUNNING;
    }

    /**
     * Transitions this span to a terminal status. No-op if already terminal.
     *
     * @param target terminal status (SUCCESS / FAILED / SKIPPED)
     */
    public void terminate(TraceStepStatus target) {
        if (status.isTerminal()) {
            return;
        }
        this.status = target;
        this.endTime = Instant.now();
    }

    /**
     * Terminates this span with FAILED status and records the error message.
     *
     * @param error error description (will be truncated by TraceRecorder)
     */
    public void fail(String error) {
        terminate(TraceStepStatus.FAILED);
        this.errorMessage = error;
    }

    /**
     * Returns true if this span is in a terminal state.
     */
    public boolean isTerminal() {
        return status.isTerminal();
    }

    // --- getters ---

    public int getSequence() {
        return sequence;
    }

    public TraceStepType getStepType() {
        return stepType;
    }

    public String getLabel() {
        return label;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public TraceStepStatus getStatus() {
        return status;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Puts a metadata key-value pair. Caller should ensure key/value limits
     * are enforced before calling (done by TraceRecorder).
     */
    public void putMetadata(String key, String value) {
        metadata.put(key, value);
    }
}
