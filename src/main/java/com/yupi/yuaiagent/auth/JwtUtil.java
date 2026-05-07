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

/**
 * JWT 工具类（密钥从配置文件读取，避免硬编码）
 */
@Component
@Slf4j
public class JwtUtil {

    // 从配置文件读取，生产环境通过环境变量 JWT_SECRET 注入
    @Value("${jwt.secret:yu-ai-agent-default-dev-secret-key-please-change-in-prod}")
    private String secretStr;

    // Token 有效期：7天
    private static final long EXPIRE_MS = 7L * 24 * 60 * 60 * 1000;

    private byte[] getSecret() {
        return secretStr.getBytes();
    }

    /**
     * 生成 Token
     */
    public String generateToken(String userId, String username) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("exp", DateUtil.offsetMillisecond(new Date(), (int) EXPIRE_MS).getTime());
        return JWTUtil.createToken(payload, getSecret());
    }

    /**
     * 验证 Token 并返回 userId，无效则返回 null
     */
    public String validateToken(String token) {
        try {
            if (!JWTUtil.verify(token, getSecret())) {
                return null;
            }
            JWT jwt = JWTUtil.parseToken(token);
            Long exp = (Long) jwt.getPayload("exp");
            if (exp != null && exp < System.currentTimeMillis()) {
                return null;
            }
            return (String) jwt.getPayload("userId");
        } catch (Exception e) {
            log.warn("Token 验证失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中获取用户名
     */
    public String getUsername(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            return (String) jwt.getPayload("username");
        } catch (Exception e) {
            return null;
        }
    }
}
