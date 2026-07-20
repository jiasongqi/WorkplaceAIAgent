package com.yupi.yuaiagent.auth;

import lombok.Data;

import java.time.Instant;

/**
 * Persisted user account (file or future JDBC).
 */
@Data
public class UserAccount {
    private String userId;
    private String username;
    private String passwordHash;
    private UserRole role = UserRole.GUEST;
    /** ACTIVE / DISABLED */
    private String status = "ACTIVE";
    private Instant createdAt;
    private Instant updatedAt;
}
