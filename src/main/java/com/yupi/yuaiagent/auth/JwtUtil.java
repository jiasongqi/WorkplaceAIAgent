package com.yupi.yuaiagent.auth;

import cn.hutool.core.date.DateUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtUtil {

    public static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";

    /** Must be set via env / application-local.yml — no insecure default in code. */
    @Value("${jwt.secret}")
    private String secretStr;

    @Value("${jwt.access-expire-ms:1800000}")
    private long accessExpireMs;

    @Value("${jwt.refresh-expire-ms:1209600000}")
    private long refreshExpireMs;

    private byte[] getSecret() {
        if (secretStr == null || secretStr.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret is empty — set JWT_SECRET or use application-local.yml (profile=local)");
        }
        return secretStr.getBytes();
    }

    public String generateToken(String userId, String username) {
        return generateAccessToken(userId, username, UserRole.GUEST);
    }

    public String generateAccessToken(String userId, String username, UserRole role) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("role", role.name());
        payload.put("typ", TYP_ACCESS);
        long ttl = Math.min(accessExpireMs, Integer.MAX_VALUE);
        payload.put("exp", DateUtil.offsetMillisecond(new Date(), (int) ttl).getTime());
        return JWTUtil.createToken(payload, getSecret());
    }

    public String generateRefreshJwt(String userId, String username, UserRole role) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("role", role.name());
        payload.put("typ", TYP_REFRESH);
        long ttl = Math.min(refreshExpireMs, Integer.MAX_VALUE);
        payload.put("exp", DateUtil.offsetMillisecond(new Date(), (int) ttl).getTime());
        return JWTUtil.createToken(payload, getSecret());
    }

    public long getRefreshExpireMs() {
        return refreshExpireMs;
    }

    public long getAccessExpireMs() {
        return accessExpireMs;
    }

    public String validateToken(String token) {
        AuthPrincipal principal = validateAccessToken(token);
        return principal != null ? principal.userId() : null;
    }

    public AuthPrincipal validateAccessToken(String token) {
        try {
            if (!JWTUtil.verify(token, getSecret())) {
                return null;
            }
            JWT jwt = JWTUtil.parseToken(token);
            if (!isNotExpired(jwt)) {
                return null;
            }
            Object typ = jwt.getPayload("typ");
            if (typ != null && !TYP_ACCESS.equals(String.valueOf(typ))) {
                return null;
            }
            String userId = (String) jwt.getPayload("userId");
            String username = (String) jwt.getPayload("username");
            UserRole role = UserRole.from((String) jwt.getPayload("role"));
            if (userId == null || userId.isBlank()) {
                return null;
            }
            return new AuthPrincipal(userId, username != null ? username : "游客", role);
        } catch (Exception e) {
            log.warn("Access token validation failed: {}", e.getMessage());
            return null;
        }
    }

    public AuthPrincipal validateRefreshJwt(String token) {
        try {
            if (!JWTUtil.verify(token, getSecret())) {
                return null;
            }
            JWT jwt = JWTUtil.parseToken(token);
            if (!isNotExpired(jwt)) {
                return null;
            }
            if (!TYP_REFRESH.equals(String.valueOf(jwt.getPayload("typ")))) {
                return null;
            }
            String userId = (String) jwt.getPayload("userId");
            String username = (String) jwt.getPayload("username");
            UserRole role = UserRole.from((String) jwt.getPayload("role"));
            if (userId == null) {
                return null;
            }
            return new AuthPrincipal(userId, username, role);
        } catch (Exception e) {
            log.warn("Refresh JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    public String getUsername(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            return (String) jwt.getPayload("username");
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isNotExpired(JWT jwt) {
        Object expObj = jwt.getPayload("exp");
        long exp = 0;
        if (expObj instanceof Number num) {
            exp = num.longValue();
        }
        return exp <= 0 || exp >= System.currentTimeMillis();
    }
}
