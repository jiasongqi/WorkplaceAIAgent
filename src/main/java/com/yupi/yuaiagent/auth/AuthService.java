package com.yupi.yuaiagent.auth;

import com.yupi.yuaiagent.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Centralized authentication service.
 * <p>
 * Resolves JWT from either URL query parameter ({@code token}) or
 * {@code Authorization: Bearer xxx} header, validates it, and returns the userId.
 * Throws {@link BusinessException} on failure so callers don't need null-checks.
 *
 * @author jsq
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;

    /**
     * Authenticates the request and returns the userId.
     *
     * @param tokenParam  JWT from URL query parameter (may be null)
     * @param authHeader  JWT from Authorization header (may be null)
     * @return the authenticated userId
     * @throws BusinessException(401) if token is missing or invalid
     */
    public String authenticate(String tokenParam, String authHeader) {
        String token = resolveToken(tokenParam, authHeader);
        if (token == null || token.isBlank()) {
            throw BusinessException.notLoggedIn();
        }
        String userId = jwtUtil.validateToken(token);
        if (userId == null) {
            throw BusinessException.notLoggedIn();
        }
        return userId;
    }

    private String resolveToken(String tokenParam, String authHeader) {
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam;
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
