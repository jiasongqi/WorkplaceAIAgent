package com.yupi.yuaiagent.calendar;

import com.yupi.yuaiagent.calendar.CalendarService.CalendarException;
import com.yupi.yuaiagent.common.Result;
import com.yupi.yuaiagent.exception.GlobalExceptionHandler;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 5: Calendar API Error Handling
 *
 * <p>For any calendar API failure scenario, the system SHALL return a user-friendly error message
 * suggesting retry or customer service contact, and log the error details.</p>
 *
 * <p>Failure scenarios surface as {@link CalendarException} thrown by a
 * {@link CalendarService} implementation and are handled centrally by
 * {@link GlobalExceptionHandler#handleCalendarException}. These tests generate arbitrary failure
 * scenarios (varied raw messages, with or without an underlying cause) and assert the handler
 * produces a non-success {@link Result} carrying a fixed friendly message, without leaking the
 * raw exception text.</p>
 *
 * <p><b>Validates: Requirements 2.4</b></p>
 */
class CalendarApiErrorHandlingPropertyTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * Generator over realistic raw failure messages a calendar API might raise, including ones
     * that contain sensitive/internal details that must NOT be leaked to the user.
     */
    @Provide
    Arbitrary<String> rawErrorMessages() {
        return Arbitraries.of(
                "HTTP 500 Internal Server Error from open.feishu.cn",
                "java.net.SocketTimeoutException: Read timed out",
                "Invalid access_token: stack trace at com.yupi...",
                "DingTalk API rate limit exceeded, code=90018",
                "NullPointerException at FeishuApiClient.line:142",
                "connection refused: 10.0.0.5:443",
                "",
                "   ",
                "预约时间冲突",
                "{\"code\":99991663,\"msg\":\"tenant access token invalid\"}"
        );
    }

    /**
     * Generator over arbitrary CalendarException failure scenarios, sometimes wrapping an
     * underlying cause (simulating an IO/HTTP exception from the provider SDK).
     */
    @Provide
    Arbitrary<CalendarException> calendarExceptions() {
        Arbitrary<String> messages = rawErrorMessages();
        Arbitrary<Boolean> withCause = Arbitraries.of(true, false);
        return Combinators.combine(messages, withCause).as((message, hasCause) ->
                hasCause
                        ? new CalendarException(message, new RuntimeException("underlying provider failure: " + message))
                        : new CalendarException(message));
    }

    /**
     * For any calendar failure scenario, the handler returns a non-null, non-success result
     * (HTTP code is not the 200 success code). A success result would wrongly signal the
     * appointment was created.
     *
     * <p><b>Validates: Requirements 2.4</b></p>
     */
    @Property
    void handlerReturnsNonSuccessResult(@ForAll("calendarExceptions") CalendarException exception) {
        Result<String> result = handler.handleCalendarException(exception);

        assertThat(result).as("handler must always produce a result").isNotNull();
        assertThat(result.getCode())
                .as("calendar failures must not be reported as success (200)")
                .isNotEqualTo(200);
    }

    /**
     * For any calendar failure scenario, the user-facing message is friendly and actionable:
     * it suggests retrying later and contacting customer service. This is the standardized
     * recovery guidance required by Requirement 2.4.
     *
     * <p><b>Validates: Requirements 2.4</b></p>
     */
    @Property
    void handlerReturnsFriendlyRetryAndSupportMessage(@ForAll("calendarExceptions") CalendarException exception) {
        Result<String> result = handler.handleCalendarException(exception);

        assertThat(result.getMessage())
                .as("user-facing message must be present")
                .isNotNull()
                .isNotBlank();
        assertThat(result.getMessage())
                .as("message must suggest retrying later")
                .contains("重试");
        assertThat(result.getMessage())
                .as("message must suggest contacting customer service")
                .contains("人工客服");
    }

    /**
     * For any calendar failure scenario, the raw exception details (which may contain stack
     * traces, tokens, internal hosts, or SDK error codes) must NOT leak into the user-facing
     * message. The handler returns a fixed, sanitized message regardless of the raw input.
     *
     * <p><b>Validates: Requirements 2.4</b></p>
     */
    @Property
    void handlerDoesNotLeakRawExceptionDetails(@ForAll("calendarExceptions") CalendarException exception) {
        Result<String> result = handler.handleCalendarException(exception);

        String raw = exception.getMessage();
        if (raw != null && !raw.isBlank()) {
            assertThat(result.getMessage())
                    .as("user-facing message must not echo the raw exception text: <%s>", raw)
                    .doesNotContain(raw.trim());
        }
        // The friendly message is fixed/stable and independent of the raw failure content.
        assertThat(result.getMessage())
                .isEqualTo("预约服务暂时不可用，请稍后重试，或联系人工客服为您处理。");
    }
}
