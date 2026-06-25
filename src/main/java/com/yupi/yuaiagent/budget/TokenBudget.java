package com.yupi.yuaiagent.budget;

/**
 * Token budget definition — per-workflow cap.
 */
public record TokenBudget(
    long maxPromptTokens,
    long maxCompletionTokens,
    long maxTotalTokens
) {
    public boolean canExecute(long estimatedTokens, long usedTokens) {
        return estimatedTokens <= (maxTotalTokens - usedTokens);
    }
}
