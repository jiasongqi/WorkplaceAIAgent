package com.yupi.yuaiagent.agent.loop;

import com.yupi.yuaiagent.auth.UserQuotaService;
import com.yupi.yuaiagent.auth.UserRole;
import lombok.Getter;

/**
 * Per-run token budget (Ch4 §2.3): step loop + daily quota fuse.
 */
@Getter
public class LoopRunBudget {

    private final int maxRunTokens;
    /** Snapshot of daily remaining at run start; {@code -1} = not enforced. */
    private final int dailyRemainingSnapshot;

    private int tokensUsed;
    private boolean exhausted;
    private String exhaustReason = "";

    public LoopRunBudget(int maxRunTokens, int dailyRemainingSnapshot) {
        this.maxRunTokens = Math.max(0, maxRunTokens);
        this.dailyRemainingSnapshot = dailyRemainingSnapshot;
    }

    public static LoopRunBudget create(UserQuotaService quotaService, String userId,
                                       UserRole role, int maxRunTokens) {
        int dailyRemaining = -1;
        if (quotaService != null && userId != null && !userId.isBlank()) {
            dailyRemaining = quotaService.remainingDailyTokens(userId, role);
        }
        return new LoopRunBudget(maxRunTokens, dailyRemaining);
    }

    public void record(int tokens) {
        if (tokens <= 0 || exhausted) {
            return;
        }
        tokensUsed += tokens;
        evaluate();
    }

    private void evaluate() {
        if (maxRunTokens > 0 && tokensUsed >= maxRunTokens) {
            exhausted = true;
            exhaustReason = "单次运行 Token 上限 " + maxRunTokens + " 已用尽（已用 " + tokensUsed + "）";
            return;
        }
        if (dailyRemainingSnapshot >= 0 && tokensUsed >= dailyRemainingSnapshot) {
            exhausted = true;
            exhaustReason = "今日 Token 配额即将用尽（本 run 已用 " + tokensUsed
                    + "，启动时剩余约 " + dailyRemainingSnapshot + "）";
        }
    }

    public String budgetReasonForWrapUp() {
        if (exhaustReason != null && !exhaustReason.isBlank()) {
            return exhaustReason;
        }
        return "Token 预算已用尽";
    }
}
