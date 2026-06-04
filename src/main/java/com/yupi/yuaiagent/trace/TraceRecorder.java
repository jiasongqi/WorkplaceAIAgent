package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.trace.model.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Recording facade for execution traces (Req 4.1 / 4.2).
 * <p>
 * All public methods are wrapped in try-catch to guarantee that tracing failures
 * never propagate to the main business flow. Metadata values are truncated to
 * the configured character limit using codepoint-aware truncation (Req 4.2).
 *
 * @author jsq
 */
@Slf4j
@Component
public class TraceRecorder {

    @Resource
    private TraceProperties traceProperties;

    // P2 placeholder — injected as null when not registered as Bean
    @Resource
    private TraceStreamPublisher streamPublisher;

    /**
     * Creates and starts a new trace context.
     *
     * @param userId    the owning user ID (nullable)
     * @param chatId    the chat session ID
     * @param requestId the HTTP request ID
     * @return a new TraceContext, never null (returns noop on error)
     */
    public TraceContext startTrace(String userId, String chatId, String requestId) {
        try {
            ExecutionTrace trace = ExecutionTrace.start(userId, chatId, requestId);
            TraceContext ctx = TraceContext.of(trace);
            log.debug("[trace] started traceId={}, userId={}, chatId={}", trace.getTraceId(), userId, chatId);
            return ctx;
        } catch (Exception e) {
            log.error("[trace] failed to start trace", e);
            return TraceContext.noop();
        }
    }

    /**
     * Appends a new span to the context and publishes a SPAN_STARTED event.
     *
     * @param ctx      the trace context (may be noop)
     * @param stepType the step category
     * @param label    human-readable label
     * @return the created span, or null if context is noop
     */
    public TraceSpan startSpan(TraceContext ctx, TraceStepType stepType, String label) {
        if (ctx == null) {
            return null;
        }
        try {
            TraceSpan span = ctx.appendSpan(stepType, label);
            publishSpanStarted(ctx, span);
            return span;
        } catch (Exception e) {
            log.error("[trace] failed to start span: {}", label, e);
            return null;
        }
    }

    /**
     * Ends a span with SUCCESS status and publishes a SPAN_ENDED event.
     */
    public void endSpan(TraceContext ctx, TraceSpan span) {
        if (ctx == null || span == null) {
            return;
        }
        try {
            ctx.finishSpan(span);
            publishSpanEnded(ctx, span);
        } catch (Exception e) {
            log.error("[trace] failed to end span", e);
        }
    }

    /**
     * Fails a span with an error message and publishes a SPAN_ENDED event.
     */
    public void failSpan(TraceContext ctx, TraceSpan span, String errorMessage) {
        if (ctx == null || span == null) {
            return;
        }
        try {
            String truncated = truncateError(errorMessage);
            ctx.failSpan(span, truncated);
            publishSpanEnded(ctx, span);
        } catch (Exception e) {
            log.error("[trace] failed to mark span as failed", e);
        }
    }

    /**
     * Skips a span and publishes a SPAN_ENDED event.
     */
    public void skipSpan(TraceContext ctx, TraceSpan span) {
        if (ctx == null || span == null) {
            return;
        }
        try {
            ctx.skipSpan(span);
            publishSpanEnded(ctx, span);
        } catch (Exception e) {
            log.error("[trace] failed to skip span", e);
        }
    }

    /**
     * Ends the trace successfully — derives final status from spans.
     */
    public void endTrace(TraceContext ctx) {
        if (ctx == null) {
            return;
        }
        try {
            ctx.finalizeTrace();
            log.debug("[trace] ended traceId={}", ctx.getTrace().getTraceId());
        } catch (Exception e) {
            log.error("[trace] failed to end trace", e);
        }
    }

    /**
     * Fails the entire trace.
     */
    public void failTrace(TraceContext ctx) {
        if (ctx == null) {
            return;
        }
        try {
            ctx.failTrace();
            log.debug("[trace] failed traceId={}", ctx.getTrace().getTraceId());
        } catch (Exception e) {
            log.error("[trace] failed to mark trace as failed", e);
        }
    }

    // --- metadata helpers ---

    /**
     * Puts metadata on a span with key/value truncation (Req 4.2).
     * <p>
     * Limits:
     * <ul>
     *   <li>Max 50 keys per span (excess silently dropped)</li>
     *   <li>Key max 128 characters</li>
     *   <li>Value max configurable (default 2000 codepoints)</li>
     * </ul>
     *
     * @param span  the target span
     * @param key   metadata key
     * @param value metadata value
     */
    public void putMetadata(TraceSpan span, String key, String value) {
        if (span == null || key == null || value == null) {
            return;
        }
        try {
            // Enforce max keys
            if (span.getMetadata().size() >= TraceConstants.MAX_METADATA_ENTRIES) {
                log.warn("[trace] metadata limit reached ({} keys), dropping key={}",
                        TraceConstants.MAX_METADATA_ENTRIES, key);
                return;
            }

            // Truncate key
            String safeKey = truncateToCodepoints(key, TraceConstants.MAX_METADATA_KEY_CHARS);

            // Truncate value
            int maxValueChars = traceProperties.getMetadataMaxValueChars();
            String safeValue = truncateToCodepoints(value, maxValueChars);

            span.putMetadata(safeKey, safeValue);
        } catch (Exception e) {
            log.error("[trace] failed to put metadata", e);
        }
    }

    /**
     * Puts a map of metadata entries on a span.
     */
    public void putAllMetadata(TraceSpan span, Map<String, String> metadata) {
        if (span == null || metadata == null) {
            return;
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            putMetadata(span, entry.getKey(), entry.getValue());
        }
    }

    // --- internal helpers ---

    /**
     * Truncates error message to the configured max codepoints (Req 4.2).
     */
    private String truncateError(String error) {
        if (error == null) {
            return "";
        }
        return truncateToCodepoints(error, TraceConstants.MAX_ERROR_CHARS);
    }

    /**
     * Truncates a string to the given number of Unicode codepoints.
     * <p>
     * Uses codepoint-aware indexing to avoid splitting surrogate pairs (e.g., emoji).
     *
     * @param s          the input string
     * @param maxPoints  maximum codepoints to keep
     * @return truncated string
     */
    static String truncateToCodepoints(String s, int maxPoints) {
        if (s == null) {
            return "";
        }
        if (s.codePointCount(0, s.length()) <= maxPoints) {
            return s;
        }
        int endIndex = s.offsetByCodePoints(0, maxPoints);
        return s.substring(0, endIndex);
    }

    // --- SSE publishing helpers (Req 10.2) ---

    /**
     * Publishes a SPAN_STARTED event to the bound SSE emitter (fail-safe).
     */
    private void publishSpanStarted(TraceContext ctx, TraceSpan span) {
        if (streamPublisher == null || ctx == null || ctx.isSseClosed()) {
            return;
        }
        try {
            streamPublisher.publishSpanStarted(ctx.getSseEmitter(), span);
        } catch (Exception e) {
            log.debug("[trace] failed to publish span-started event: {}", e.getMessage());
        }
    }

    /**
     * Publishes a SPAN_ENDED event to the bound SSE emitter (fail-safe).
     */
    private void publishSpanEnded(TraceContext ctx, TraceSpan span) {
        if (streamPublisher == null || ctx == null || ctx.isSseClosed()) {
            return;
        }
        try {
            streamPublisher.publishSpanEnded(ctx.getSseEmitter(), span);
        } catch (Exception e) {
            log.debug("[trace] failed to publish span-ended event: {}", e.getMessage());
        }
    }
}
