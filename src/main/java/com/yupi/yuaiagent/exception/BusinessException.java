package com.yupi.yuaiagent.exception;

import com.yupi.yuaiagent.common.ResultCode;

/**
 * Base business exception for domain-specific errors.
 * Uses {@link ResultCode} for consistent error codes.
 *
 * @author jsq
 */
public class BusinessException extends RuntimeException {

    private final long code;

    public BusinessException(long code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public long getCode() {
        return code;
    }

    // --- factory methods ---

    public static BusinessException notLoggedIn() {
        return new BusinessException(ResultCode.UNAUTHORIZED);
    }

    public static BusinessException forbidden() {
        return new BusinessException(ResultCode.FORBIDDEN);
    }

    public static BusinessException notFound(String resource) {
        return new BusinessException(ResultCode.NOT_FOUND, resource + "不存在");
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.VALIDATE_FAILED, message);
    }
}
