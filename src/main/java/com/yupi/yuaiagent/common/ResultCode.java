package com.yupi.yuaiagent.common;

/**
 * Unified result codes for API responses.
 *
 * @author jsq
 */
public enum ResultCode {

    SUCCESS(200, "success"),
    FAILED(500, "system error"),
    VALIDATE_FAILED(400, "Parameter validation failed"),
    UNAUTHORIZED(401, "Not logged in or token expired"),
    FORBIDDEN(403, "No relevant permissions"),
    NOT_FOUND(404, "Resource not found");

    private final long code;
    private final String message;

    ResultCode(long code, String message) {
        this.code = code;
        this.message = message;
    }

    public long getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
