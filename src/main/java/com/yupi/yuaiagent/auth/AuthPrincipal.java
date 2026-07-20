package com.yupi.yuaiagent.auth;

/**
 * Authenticated principal resolved from access JWT.
 */
public record AuthPrincipal(String userId, String username, UserRole role) {
}
