package com.yupi.yuaiagent.auth;

import com.yupi.yuaiagent.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enforces per-role daily chat / token quotas to prevent LLM budget burn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQuotaService {

    private final DailyQuotaStore dailyQuotaStore;
    private final AuthProperties authProperties;
    private final UserAccountStore userAccountStore;

    /**
     * Check remaining quota and consume one chat request.
     */
    public void checkAndConsumeChat(String userId, UserRole role) {
        AuthProperties.RoleQuota limits = authProperties.limitsFor(role);
        LocalDate today = LocalDate.now();
        DailyQuotaStore.DailyQuotaRecord record = dailyQuotaStore.getOrCreate(userId, today);

        if (record.getChatCount() >= limits.getDailyChats()) {
            log.warn("Daily chat quota exceeded userId={} role={} used={}", userId, role, record.getChatCount());
            throw BusinessException.tooManyRequests(
                    "今日对话次数已达上限（" + limits.getDailyChats() + "），请注册登录或明天再试");
        }
        if (record.getTokenUsed() >= limits.getDailyTokens()) {
            throw BusinessException.tooManyRequests(
                    "今日 Token 配额已用尽，请注册登录提升额度或明天再试");
        }

        record.setChatCount(record.getChatCount() + 1);
        dailyQuotaStore.save(record);
    }

    public void addTokenUsage(String userId, int tokens) {
        if (tokens <= 0) {
            return;
        }
        DailyQuotaStore.DailyQuotaRecord record = dailyQuotaStore.getOrCreate(userId, LocalDate.now());
        record.setTokenUsed(record.getTokenUsed() + tokens);
        dailyQuotaStore.save(record);
    }

    public Map<String, Object> snapshot(String userId, UserRole role) {
        AuthProperties.RoleQuota limits = authProperties.limitsFor(role);
        DailyQuotaStore.DailyQuotaRecord record = dailyQuotaStore.getOrCreate(userId, LocalDate.now());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", role.name());
        map.put("day", record.getDay().toString());
        map.put("chatUsed", record.getChatCount());
        map.put("chatLimit", limits.getDailyChats());
        map.put("chatRemaining", Math.max(0, limits.getDailyChats() - record.getChatCount()));
        map.put("tokenUsed", record.getTokenUsed());
        map.put("tokenLimit", limits.getDailyTokens());
        map.put("tokenRemaining", Math.max(0, limits.getDailyTokens() - record.getTokenUsed()));
        userAccountStore.findByUserId(userId).ifPresent(a -> map.put("username", a.getUsername()));
        return map;
    }
}
