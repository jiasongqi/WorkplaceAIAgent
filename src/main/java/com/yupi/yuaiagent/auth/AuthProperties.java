package com.yupi.yuaiagent.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Role-based daily quota limits and auth switches.
 */
@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** Allow guest login (demo only). Default off to protect LLM budget. */
    private boolean guestEnabled = false;

    /** Shared password required for legacy guest/admin name login when not using register. */
    private String guestPassword = "workpilot2024";
    private String adminPassword = "admin2024";

    private String storageDir = "./tmp/auth";

    /** Require password for guest login when guestEnabled. Default on. */
    private boolean guestPasswordRequired = true;

    private QuotaLimits quota = new QuotaLimits();

    @Data
    public static class QuotaLimits {
        private RoleQuota guest = new RoleQuota(3, 15_000);
        private RoleQuota user = new RoleQuota(50, 200_000);
        private RoleQuota admin = new RoleQuota(10_000, 50_000_000);
    }

    @Data
    public static class RoleQuota {
        /** Max chat requests per calendar day. */
        private int dailyChats = 3;
        /** Max estimated tokens per calendar day (placeholder until real token metering). */
        private int dailyTokens = 15_000;

        public RoleQuota() {}

        public RoleQuota(int dailyChats, int dailyTokens) {
            this.dailyChats = dailyChats;
            this.dailyTokens = dailyTokens;
        }
    }

    public RoleQuota limitsFor(UserRole role) {
        return switch (role) {
            case ADMIN -> quota.getAdmin();
            case USER -> quota.getUser();
            case GUEST -> quota.getGuest();
        };
    }
}
