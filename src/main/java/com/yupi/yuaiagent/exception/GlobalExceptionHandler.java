package com.yupi.yuaiagent.exception;

import com.yupi.yuaiagent.calendar.CalendarService;
import com.yupi.yuaiagent.common.Result;
import com.yupi.yuaiagent.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<String> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getMessage());
        return Result.error(400, "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 处理日历服务异常
     * 当企业日历 API 调用失败（创建、取消、修改事件或可用性检查）时触发，
     * 记录详细错误日志，并向用户返回友好提示，建议稍后重试或联系人工客服。
     */
    @ExceptionHandler(CalendarService.CalendarException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<String> handleCalendarException(CalendarService.CalendarException e) {
        log.error("日历服务调用失败: {}", e.getMessage(), e);
        return Result.error(HttpStatus.SERVICE_UNAVAILABLE.value(),
                "预约服务暂时不可用，请稍后重试，或联系人工客服为您处理。");
    }

    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<String> handleException(Exception e) {
        log.error("服务器内部错误", e);
        return Result.error("服务器内部错误，请稍后重试");
    }
}
