package com.yupi.yuaiagent.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测用例
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalCase {
    private String caseId;
    private String input;
    private String expectedOutput;
    /** Expected AgentIntent name for ROUTING cases, e.g. RESUME / NEGOTIATION */
    private String expectedIntent;
    private String category;
    /** EXACT_MATCH / SEMANTIC_SIMILARITY / LLM_JUDGE / ROUTING / KEYWORD_OVERLAP */
    private String scoringRule;
    /** 0.0 ~ 1.0 */
    private double passThreshold;
}
