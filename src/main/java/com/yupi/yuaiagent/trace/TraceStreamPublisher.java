package com.yupi.yuaiagent.trace;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Real-time trace event stream publisher (P2, Req 10.2).
 * <p>
 * Pushes {@code trace} SSE events when spans start or end.
 * Fail-safe: publishing errors are caught and logged, never propagated.
 * When {@code trace.stream.enabled=false}, publishing is silently skipped
 * but persistence is unaffected.
 *
 * @author jsq
 */
@Slf4j
@Component
public class TraceStreamPublisher {

    @Resource
    private TraceProperties traceProperties;

    /**
     * Publishes a span-started event to the SSE emitter.
     *
     * @param emitter the SSE emitter (may be null)
     * @param span    the started span
     */
    public void publishSpanStarted(SseEmitter emitter, TraceSpan span) {
        if (!canPublish(emitter, span)) {
            return;
        }
        TraceEvent event = new TraceEvent(
                "SPAN_STARTED",
                span.getSequence(),
                span.getStepType().name(),
                span.getStepType().getDisplayName(),
                span.getLabel(),
                span.getStatus().name(),
                null
        );
        sendEvent(emitter, event);
    }

    /**
     * Publishes a span-ended event to the SSE emitter.
     *
     * @param emitter the SSE emitter (may be null)
     * @param span    the ended span
     */
    public void publishSpanEnded(SseEmitter emitter, TraceSpan span) {
        if (!canPublish(emitter, span)) {
            return;
        }
        TraceEvent event = new TraceEvent(
                "SPAN_ENDED",
                span.getSequence(),
                span.getStepType().name(),
                span.getStepType().getDisplayName(),
                span.getLabel(),
                span.getStatus().name(),
                span.getErrorMessage()
        );
        sendEvent(emitter, event);
    }

    private boolean canPublish(SseEmitter emitter, TraceSpan span) {
        if (emitter == null || span == null) {
            return false;
        }
        if (!traceProperties.isStreamEnabled()) {
            return false;
        }
        return true;
    }

    private void sendEvent(SseEmitter emitter, TraceEvent event) {
        try {
            emitter.send(SseEmitter.event().name("trace").data(event));
        } catch (IOException e) {
            // SSE connection may already be closed — log and continue
            log.debug("[trace] failed to publish SSE trace event: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[trace] unexpected error publishing trace event", e);
        }
    }

    /**
     * SSE trace event payload.
     */
    public record TraceEvent(
            @JsonProperty("type") String type,
            @JsonProperty("sequence") int sequence,
            @JsonProperty("stepType") String stepType,
            @JsonProperty("stepTypeDisplayName") String stepTypeDisplayName,
            @JsonProperty("label") String label,
            @JsonProperty("status") String status,
            @JsonProperty("errorMessage") String errorMessage
    ) {}
}
