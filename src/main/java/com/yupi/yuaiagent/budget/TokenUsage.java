package com.yupi.yuaiagent.budget;

/**
 * Actual token usage record — from API response or estimation.
 */
public record TokenUsage(
    long estimatedPromptTokens,
    long actualPromptTokens,
    long actualCompletionTokens
) {
    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0);

    public long totalTokens() {
        return actualPromptTokens + actualCompletionTokens;
    }

    /** Estimation error rate (for calibrating the estimation model). */
    public double estimationError() {
        if (estimatedPromptTokens == 0) return 0;
        return Math.abs(actualPromptTokens - estimatedPromptTokens)
             / (double) estimatedPromptTokens;
    }

    public TokenUsage add(TokenUsage other) {
        return new TokenUsage(
            this.estimatedPromptTokens + other.estimatedPromptTokens,
            this.actualPromptTokens + other.actualPromptTokens,
            this.actualCompletionTokens + other.actualCompletionTokens);
    }
}
