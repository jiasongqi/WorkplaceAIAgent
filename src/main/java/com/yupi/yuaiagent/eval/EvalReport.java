package com.yupi.yuaiagent.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测报告
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalReport {
    private String reportId;
    private String suiteName;
    private String agentType;
    private String agentVersion;
    private double overallScore;
    private double passRate;
    private int totalCases;
    private int passedCases;
    private int failedCases;
    private boolean regression;
    private List<CaseResult> caseResults;
    private LocalDateTime executedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseResult {
        private String caseId;
        private String input;
        private String actualOutput;
        private double score;
        private boolean passed;
        private String feedback;
    }
}
