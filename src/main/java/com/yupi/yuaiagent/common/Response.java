package com.yupi.yuaiagent.common;

import lombok.Data;

/**
 * Unified API response wrapper.
 * All controllers return {@code Response<T>}.
 *
 * @param <T> response data type
 * @author jsq
 */
@Data
public class Response<T> {

    private long code;
    private String message;
    private T data;

    public Response() {
    }

    public Response(long code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== success ====================

    public static <T> Response<T> success() {
        return new Response<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Response<T> success(T data) {
        return new Response<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    // ==================== failed ====================

    public static <T> Response<T> failed() {
        return new Response<>(ResultCode.FAILED.getCode(), ResultCode.FAILED.getMessage(), null);
    }

    public static <T> Response<T> failed(String message) {
        return new Response<>(ResultCode.FAILED.getCode(), message, null);
    }

    public static <T> Response<T> failed(long code, String message) {
        return new Response<>(code, message, null);
    }

    public static <T> Response<T> failed(ResultCode resultCode) {
        return new Response<>(resultCode.getCode(), resultCode.getMessage(), null);
    }
}
