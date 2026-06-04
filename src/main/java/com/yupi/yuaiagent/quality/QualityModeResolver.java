package com.yupi.yuaiagent.quality;

import com.yupi.yuaiagent.agent.AgentIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Resolves the appropriate QualityMode for a given user message and intent.
 * <p>
 * Resolution strategy:
 * <ul>
 *   <li>Manual override (user specified mode) → use directly</li>
 *   <li>Career decision intents → REVIEW</li>
 *   <li>Other → LLM-based risk classification → OFF / REVIEW / RED_TEAM</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Service
public class QualityModeResolver {

    private static final Set<AgentIntent> REVIEW_INTENTS = Set.of(
            AgentIntent.RESUME, AgentIntent.NEGOTIATION, AgentIntent.ESCAPE
    );

    private static final String RISK_CLASSIFIER_PROMPT = """
        判断以下用户问题的风险等级。只输出一个 JSON，不要输出其他内容。
        
        LOW: 日常闲聊、一般建议、学习知识
        MEDIUM: 职业规划、简历优化、面试准备
        HIGH: 财务投资建议、法律咨询、医疗健康
        CRITICAL: 涉及个人隐私解析、可能造成严重财务/法律后果
        
        输出格式：
        {"level":"MEDIUM","reason":"职业规划类问题"}
        
        问题：
        %s
        """;

    private final ChatModel chatModel;

    public QualityModeResolver(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Resolves quality mode. Returns the effective mode to use.
     */
    public QualityMode resolve(String userMessage, AgentIntent intent, QualityMode requested) {
        // Manual override takes priority
        if (requested != null && requested != QualityMode.AUTO) {
            return requested;
        }

        // Career decision intents → REVIEW
        if (REVIEW_INTENTS.contains(intent)) {
            return QualityMode.REVIEW;
        }

        // LLM-based risk classification
        try {
            RiskAssessment risk = classifyRisk(userMessage);
            return switch (risk.level()) {
                case "LOW" -> QualityMode.OFF;
                case "MEDIUM" -> QualityMode.REVIEW;
                case "HIGH", "CRITICAL" -> QualityMode.RED_TEAM;
                default -> QualityMode.OFF;
            };
        } catch (Exception e) {
            log.warn("[QualityMode] risk classification failed, defaulting to OFF: {}", e.getMessage());
            return QualityMode.OFF;
        }
    }

    private RiskAssessment classifyRisk(String message) {
        String prompt = RISK_CLASSIFIER_PROMPT.formatted(message);
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是一个风险分类器。只输出 JSON，不要输出其他内容。"),
                new UserMessage(prompt)
        )));

        String raw = response.getResult().getOutput().getText();
        // Extract JSON
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            raw = raw.substring(start, end + 1);
        }

        // Simple parse: extract level
        String level = "LOW";
        if (raw.contains("\"CRITICAL\"")) level = "CRITICAL";
        else if (raw.contains("\"HIGH\"")) level = "HIGH";
        else if (raw.contains("\"MEDIUM\"")) level = "MEDIUM";

        return new RiskAssessment(level, "");
    }

    record RiskAssessment(String level, String reason) {}
}
