package com.yupi.yuaiagent.exception;

/**
 * Base business exception for domain-specific errors.
 * Subclasses or direct usage with error codes enables structured error handling
 * in {@link GlobalExceptionHandler}.
 *
 * @author jsq
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);
    }

    public int getCode() {
        return code;
    }

    // --- factory methods for common scenarios ---

    public static BusinessException notLoggedIn() {
        return new BusinessException(401, "未授权，请先登录");
    }

    public static BusinessException forbidden() {
        return new BusinessException(403, "无权访问该资源");
    }

    public static BusinessException notFound(String resource) {
        return new BusinessException(404, resource + "不存在");
    }
}
