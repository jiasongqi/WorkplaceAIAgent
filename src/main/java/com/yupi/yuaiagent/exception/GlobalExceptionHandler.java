package com.yupi.yuaiagent.exception;

import com.yupi.yuaiagent.calendar.CalendarService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler — converts exceptions to Response<T>.
 *
 * @author jsq
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Response<Void> handleBusiness(BusinessException e) {
        log.warn("Business exception: code={}, msg={}", e.getCode(), e.getMessage());
        return new Response<>(e.getCode(), e.getMessage(), null);
    }

    @ExceptionHandler(CalendarService.CalendarException.class)
    public Response<Void> handleCalendarException(CalendarService.CalendarException e) {
        log.error("Calendar service error: {}", e.getMessage(), e);
        return Response.failed(503, "预约服务暂时不可用，请稍后重试，或联系人工客服为您处理。");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return Response.failed(ResultCode.VALIDATE_FAILED.getCode(), e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("Missing parameter: {}", e.getMessage());
        return Response.failed(ResultCode.VALIDATE_FAILED.getCode(), "缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Response<Void> handleException(Exception e) {
        log.error("Internal server error", e);
        return Response.failed();
    }
}
