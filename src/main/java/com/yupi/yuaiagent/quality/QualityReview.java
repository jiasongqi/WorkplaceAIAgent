package com.yupi.yuaiagent.quality;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Quality review result produced by QualityGuardAgent.
 *
 * @author jsq
 */
@Data
public class QualityReview {

    private String reviewId;
    private String chatId;
    private String userMessageId;
    private String agentMessageId;
    private QualityMode mode;

    // Scores (0-100)
    private int accuracyScore;
    private int completenessScore;
    private int logicScore;
    private int hallucinationScore;  // higher = safer
    private int riskScore;           // higher = riskier
    private int overallScore;

    // Details
    private RiskLevel riskLevel;
    private List<String> issues = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private String summary;

    // Red team mode
    private String revisedAnswer;
    private int roundCount;

    private LocalDateTime createdAt;
}
