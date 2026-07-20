package com.yupi.yuaiagent.budget;

import com.yupi.yuaiagent.repository.entity.TokenUsageEntity;
import com.yupi.yuaiagent.repository.jpa.TokenUsageJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tracks per-workflow token usage and enforces budgets — JPA persistence.
 *
 * @author jsq
 */
@Slf4j
@Component
public class TokenUsageTracker {

    private final TokenUsageJpaRepository jpaRepo;

    public TokenUsageTracker(TokenUsageJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    /**
     * Record usage after an Agent call completes.
     */
    public void recordUsage(String workflowId, TokenUsage usage) {
        TokenUsageEntity entity = new TokenUsageEntity();
        entity.setWorkflowId(workflowId);
        entity.setInputTokens((int) usage.actualPromptTokens());
        entity.setOutputTokens((int) usage.actualCompletionTokens());
        entity.setTotalTokens((int) usage.totalTokens());
        entity.setCostUsd(BigDecimal.ZERO);
        jpaRepo.save(entity);
    }

    /**
     * Check if the workflow has enough budget remaining for the estimated tokens.
     */
    public boolean canExecute(String workflowId, TokenBudget budget, long estimatedTokens) {
        TokenUsage used = getUsage(workflowId);
        return budget.canExecute(estimatedTokens, used.totalTokens());
    }

    /**
     * Get total usage for a workflow.
     */
    public TokenUsage getUsage(String workflowId) {
        List<TokenUsageEntity> entities = jpaRepo.findByWorkflowId(workflowId);
        if (entities.isEmpty()) {
            return TokenUsage.ZERO;
        }
        int totalInput = entities.stream().mapToInt(e -> e.getInputTokens() != null ? e.getInputTokens() : 0).sum();
        int totalOutput = entities.stream().mapToInt(e -> e.getOutputTokens() != null ? e.getOutputTokens() : 0).sum();
        return new TokenUsage(0, totalInput, totalOutput);
    }

    /**
     * Reset usage for a workflow (new request).
     */
    public void reset(String workflowId) {
        List<TokenUsageEntity> entities = jpaRepo.findByWorkflowId(workflowId);
        jpaRepo.deleteAll(entities);
    }
}
