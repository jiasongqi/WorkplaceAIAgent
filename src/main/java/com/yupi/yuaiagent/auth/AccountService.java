package com.yupi.yuaiagent.auth;

import com.yupi.yuaiagent.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Register / login / refresh / logout — file-backed accounts for demo, ready for JDBC later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserAccountStore userAccountStore;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtUtil jwtUtil;
    private final AuthProperties authProperties;
    private final UserQuotaService userQuotaService;

    public Map<String, Object> register(String username, String password) {
        validateUsername(username);
        validatePassword(password);
        if ("游客".equals(username) || "admin".equalsIgnoreCase(username)) {
            throw BusinessException.badRequest("该用户名不可注册");
        }
        if (userAccountStore.existsUsername(username)) {
            throw BusinessException.badRequest("用户名已存在");
        }
        String userId = UUID.randomUUID().toString();
        UserAccount account = userAccountStore.create(userId, username, password, UserRole.USER);
        return issueTokenPair(account);
    }

    /**
     * Login with username/password. Legacy guest/admin shared passwords still supported when configured.
     */
    public Map<String, Object> login(String username, String password, String existingUserId) {
        if (!StringUtils.hasText(username)) {
            username = "游客";
        }

        // Registered account path
        var registered = userAccountStore.findByUsername(username);
        if (registered.isPresent()) {
            UserAccount account = registered.get();
            if (!"ACTIVE".equals(account.getStatus())) {
                throw BusinessException.forbidden();
            }
            if (!UserAccountStore.matches(password, account.getPasswordHash())) {
                throw BusinessException.notLoggedIn("用户名或密码错误");
            }
            return issueTokenPair(account);
        }

        // Guest path (disabled by default)
        if ("游客".equals(username)) {
            if (!authProperties.isGuestEnabled()) {
                throw BusinessException.notLoggedIn("游客登录已关闭，请注册账号");
            }
            if (authProperties.isGuestPasswordRequired()) {
                if (!StringUtils.hasText(password)
                        || !authProperties.getGuestPassword().equals(password)) {
                    throw BusinessException.notLoggedIn("游客密码错误或未提供");
                }
            } else if (StringUtils.hasText(password)
                    && !authProperties.getGuestPassword().equals(password)) {
                throw BusinessException.notLoggedIn("密码错误");
            }
            String userId = StringUtils.hasText(existingUserId) ? existingUserId : UUID.randomUUID().toString();
            UserAccount guest = userAccountStore.findByUserId(userId).orElseGet(() -> {
                UserAccount a = new UserAccount();
                a.setUserId(userId);
                a.setUsername("游客");
                a.setPasswordHash(UserAccountStore.hashPassword(authProperties.getGuestPassword()));
                a.setRole(UserRole.GUEST);
                a.setStatus("ACTIVE");
                a.setCreatedAt(Instant.now());
                a.setUpdatedAt(Instant.now());
                return userAccountStore.save(a);
            });
            guest.setRole(UserRole.GUEST);
            return issueTokenPair(guest);
        }

        // Legacy admin shared password (bootstrap)
        if ("admin".equalsIgnoreCase(username)) {
            if (!StringUtils.hasText(password) || !authProperties.getAdminPassword().equals(password)) {
                throw BusinessException.notLoggedIn("密码错误");
            }
            String userId = StringUtils.hasText(existingUserId) ? existingUserId : "admin-" + UUID.randomUUID();
            UserAccount admin = userAccountStore.findByUsername("admin").orElseGet(() ->
                    userAccountStore.create(userId, "admin", password, UserRole.ADMIN));
            admin.setRole(UserRole.ADMIN);
            userAccountStore.save(admin);
            return issueTokenPair(admin);
        }

        throw BusinessException.notLoggedIn("用户不存在，请先注册");
    }

    public Map<String, Object> refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw BusinessException.notLoggedIn("缺少 refreshToken");
        }
        // Prefer opaque server-side token; also accept refresh JWT for transition
        var record = refreshTokenStore.findValid(refreshToken);
        String userId;
        String username;
        UserRole role;

        if (record.isPresent()) {
            userId = record.get().getUserId();
            UserAccount account = userAccountStore.findByUserId(userId)
                    .orElseThrow(BusinessException::notLoggedIn);
            username = account.getUsername();
            role = account.getRole();
            refreshTokenStore.revoke(refreshToken);
        } else {
            AuthPrincipal fromJwt = jwtUtil.validateRefreshJwt(refreshToken);
            if (fromJwt == null) {
                throw BusinessException.notLoggedIn("refreshToken 无效或已过期");
            }
            userId = fromJwt.userId();
            username = fromJwt.username();
            role = fromJwt.role();
            refreshTokenStore.revoke(refreshToken);
        }

        UserAccount account = userAccountStore.findByUserId(userId).orElseGet(() -> {
            UserAccount a = new UserAccount();
            a.setUserId(userId);
            a.setUsername(username);
            a.setRole(role);
            a.setStatus("ACTIVE");
            a.setPasswordHash(UserAccountStore.hashPassword(UUID.randomUUID().toString()));
            a.setCreatedAt(Instant.now());
            a.setUpdatedAt(Instant.now());
            return userAccountStore.save(a);
        });
        return issueTokenPair(account);
    }

    public void logout(String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenStore.revoke(refreshToken);
        }
    }

    public Map<String, Object> me(AuthPrincipal principal) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", principal.userId());
        map.put("username", principal.username());
        map.put("role", principal.role().name());
        map.put("quota", userQuotaService.snapshot(principal.userId(), principal.role()));
        return map;
    }

    private Map<String, Object> issueTokenPair(UserAccount account) {
        String access = jwtUtil.generateAccessToken(account.getUserId(), account.getUsername(), account.getRole());
        Instant refreshExp = Instant.now().plusMillis(jwtUtil.getRefreshExpireMs());
        String opaqueRefresh = refreshTokenStore.issue(account.getUserId(), refreshExp);
        // Also embed a refresh JWT for clients that only store one string (optional dual)
        String refreshJwt = jwtUtil.generateRefreshJwt(account.getUserId(), account.getUsername(), account.getRole());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", access); // backward compat
        body.put("accessToken", access);
        body.put("refreshToken", opaqueRefresh);
        body.put("refreshJwt", refreshJwt);
        body.put("userId", account.getUserId());
        body.put("username", account.getUsername());
        body.put("role", account.getRole().name());
        body.put("expiresIn", jwtUtil.getAccessExpireMs() / 1000);
        return body;
    }

    private void validateUsername(String username) {
        if (!StringUtils.hasText(username) || username.length() < 2 || username.length() > 32) {
            throw BusinessException.badRequest("用户名长度需在 2-32 之间");
        }
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password) || password.length() < 6) {
            throw BusinessException.badRequest("密码至少 6 位");
        }
    }
}
