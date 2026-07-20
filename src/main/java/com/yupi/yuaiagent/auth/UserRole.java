package com.yupi.yuaiagent.auth;

/**
 * Application roles for access control and daily quotas.
 */
public enum UserRole {
    GUEST,
    USER,
    ADMIN;

    public static UserRole from(String value) {
        if (value == null || value.isBlank()) {
            return GUEST;
        }
        try {
            return UserRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GUEST;
        }
    }
}
