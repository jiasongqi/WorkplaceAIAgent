package com.yupi.yuaiagent.quality;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Quality Guard Agent — reviews other agents' output for accuracy, completeness,
 * hallucination risk, and safety.
 * <p>
 * Three modes:
 * <ul>
 *   <li>REVIEW: single-pass review, returns scores + issues</li>
 *   <li>RED_TEAM: adversarial review, maximizes issue detection</li>
 *   <li>AUTO: handled by QualityModeResolver before reaching here</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Component
public class QualityGuardAgent {

    private static final String REVIEW_SYSTEM_PROMPT = """
        你是一个专业的 AI 回答质量审核员。你的职责是审查其他 AI Agent 的输出质量。
        
        审查维度（每项 0-100 分）：
        1. accuracyScore — 事实是否正确，数据是否准确
        2. completenessScore — 是否遗漏关键信息
        3. logicScore — 推理是否合理，结论是否成立
        4. hallucinationScore — 是否包含无依据的编造（越高越安全）
        5. riskScore — 是否涉及敏感领域（越高风险越大）
        
        风险等级：
        - LOW: 日常建议，无风险
        - MEDIUM: 职业建议，需要用户自行判断
        - HIGH: 涉及财务/法律，建议咨询专业人士
        - CRITICAL: 可能造成严重后果，建议阻断
        
        输出格式（严格 JSON，不要输出其他内容）：
        {
          "accuracyScore": 85,
          "completenessScore": 70,
          "logicScore": 90,
          "hallucinationScore": 95,
          "riskScore": 15,
          "overallScore": 85,
          "riskLevel": "MEDIUM",
          "issues": ["问题1", "问题2"],
          "suggestions": ["建议1"],
          "summary": "一句话总结"
        }
        """;

    private static final String RED_TEAM_SYSTEM_PROMPT = """
        你现在扮演红队角色。你的目标是尽可能找出回答中的问题。
        
        攻击策略：
        1. 事实核查：每个数据是否有依据
        2. 逻辑漏洞：推理链条是否有断裂
        3. 隐含假设：是否做了未声明的假设
        4. 边界情况：极端情况下是否成立
        5. 合规风险：是否违反法规或伦理
        6. 隐私风险：是否泄露或解析个人信息
        
        你需要像一个严格的审查员一样，尽可能挑刺。宁可误报，不可漏报。
        
        输出格式（严格 JSON，不要输出其他内容）：
        {
          "accuracyScore": 60,
          "completenessScore": 50,
          "logicScore": 70,
          "hallucinationScore": 80,
          "riskScore": 40,
          "overallScore": 65,
          "riskLevel": "HIGH",
          "issues": ["攻击点1", "攻击点2"],
          "suggestions": ["整改建议1"],
          "summary": "一句话总结"
        }
        """;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public QualityGuardAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Reviews an agent's answer in REVIEW mode (single pass).
     */
    public QualityReview review(String userQuestion, String agentAnswer, String chatId) {
        return doReview(userQuestion, agentAnswer, chatId, QualityMode.REVIEW, REVIEW_SYSTEM_PROMPT);
    }

    /**
     * Reviews an agent's answer in RED_TEAM mode (adversarial).
     */
    public QualityReview redTeamReview(String userQuestion, String agentAnswer, String chatId) {
        return doReview(userQuestion, agentAnswer, chatId, QualityMode.RED_TEAM, RED_TEAM_SYSTEM_PROMPT);
    }

    private QualityReview doReview(String userQuestion, String agentAnswer, String chatId,
                                    QualityMode mode, String systemPrompt) {
        String userPrompt = """
            用户问题：
            %s
            
            AI 回答：
            %s
            
            请审查以上回答的质量，输出 JSON。
            """.formatted(userQuestion, agentAnswer);

        try {
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            )));

            String raw = response.getResult().getOutput().getText();
            // Extract JSON from response (handle markdown code blocks)
            String json = extractJson(raw);

            QualityReview review = objectMapper.readValue(json, QualityReview.class);
            review.setReviewId(IdUtil.fastSimpleUUID());
            review.setChatId(chatId);
            review.setMode(mode);
            review.setCreatedAt(LocalDateTime.now());

            // Calculate overallScore if not set
            if (review.getOverallScore() == 0) {
                review.setOverallScore(calculateOverall(review));
            }

            log.info("[QualityGuard] mode={}, overall={}, risk={}, issues={}",
                    mode, review.getOverallScore(), review.getRiskLevel(),
                    review.getIssues().size());

            return review;

        } catch (Exception e) {
            log.error("[QualityGuard] review failed, returning safe default", e);
            return createSafeDefault(chatId, mode);
        }
    }

    private int calculateOverall(QualityReview r) {
        // Weighted average: accuracy 30%, completeness 20%, logic 20%, hallucination 30%
        return (int) (r.getAccuracyScore() * 0.3
                + r.getCompletenessScore() * 0.2
                + r.getLogicScore() * 0.2
                + r.getHallucinationScore() * 0.3);
    }

    private QualityReview createSafeDefault(String chatId, QualityMode mode) {
        QualityReview review = new QualityReview();
        review.setReviewId(IdUtil.fastSimpleUUID());
        review.setChatId(chatId);
        review.setMode(mode);
        review.setOverallScore(70);
        review.setRiskLevel(RiskLevel.MEDIUM);
        review.setSummary("审查过程异常，使用安全默认值");
        review.setCreatedAt(LocalDateTime.now());
        return review;
    }

    private String extractJson(String raw) {
        // Handle markdown code blocks: ```json ... ```
        if (raw.contains("```")) {
            int start = raw.indexOf("```");
            int jsonStart = raw.indexOf("\n", start);
            int end = raw.indexOf("```", start + 3);
            if (jsonStart >= 0 && end > jsonStart) {
                return raw.substring(jsonStart + 1, end).trim();
            }
        }
        // Try to find JSON object directly
        int braceStart = raw.indexOf('{');
        int braceEnd = raw.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return raw.substring(braceStart, braceEnd + 1);
        }
        return raw;
    }
}
