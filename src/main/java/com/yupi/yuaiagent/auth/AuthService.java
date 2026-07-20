package com.yupi.yuaiagent.auth;

import com.yupi.yuaiagent.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves JWT access token from query param or Bearer header.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;

    public String authenticate(String tokenParam, String authHeader) {
        return authenticatePrincipal(tokenParam, authHeader).userId();
    }

    public AuthPrincipal authenticatePrincipal(String tokenParam, String authHeader) {
        String token = resolveToken(tokenParam, authHeader);
        if (token == null || token.isBlank()) {
            throw BusinessException.notLoggedIn();
        }
        AuthPrincipal principal = jwtUtil.validateAccessToken(token);
        if (principal == null) {
            throw BusinessException.notLoggedIn();
        }
        return principal;
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
