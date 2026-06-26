package com.yupi.yuaiagent.agent;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Dynamic System Prompt Provider — switches prompt templates based on agent intent.
 *
 * <p>Instead of a single static system prompt per agent, this provider selects
 * the most appropriate prompt variant based on the detected intent and context.</p>
 *
 * @author jsq
 */
@Component
public class DynamicPromptProvider {

    /** Base prompts per agent type */
    private static final Map<String, String> BASE_PROMPTS = Map.of(
        "RESUME", "你是一位资深的职场简历优化专家和求职顾问，拥有10年以上的招聘和职业规划经验。",
        "NEGOTIATION", "你是一位资深的薪资谈判顾问，精通市场薪资调研、薪酬结构分析和谈判策略。",
        "ESCAPE", "你是一位专业的离职规划顾问，熟悉劳动法规、离职流程和职业过渡策略。",
        "GENERAL", "你是一位温暖的职场智囊，擅长人际关系、压力管理和职业规划。"
    );

    /** Intent-specific prompt extensions */
    private static final Map<String, String> INTENT_EXTENSIONS = Map.of(
        "RESUME_OPTIMIZE", "请重点帮助优化简历结构和内容表达，使成果可量化、有说服力。",
        "INTERVIEW_PREP", "请提供面试准备指导，包括STAR法则回答、常见问题应对和行为面试技巧。",
        "JOB_CHANGE", "请从跳槽决策、岗位匹配度、行业趋势等维度给出全面建议。",
        "SALARY_ANALYZE", "请进行市场薪资分析，对比同行业同岗位的薪资水平。",
        "SALARY_NEGOTIATION", "请提供具体的谈薪策略和话术，帮助争取最优薪资方案。",
        "LEAVE_PLAN", "请帮助制定离职计划，包括离职信撰写、交接清单和劳动权益保障。",
        "CAREER_GENERAL", "请从职业发展、人际关系、工作生活平衡等角度给出温暖实用的建议。"
    );

    /**
     * Get system prompt for a given agent type and intent.
     *
     * @param agentType agent type (RESUME, NEGOTIATION, ESCAPE, GENERAL)
     * @param intent    specific intent (nullable — uses base prompt if null)
     * @param userName  user name for personalization (nullable)
     * @return the assembled system prompt
     */
    public String getPrompt(String agentType, String intent, String userName) {
        StringBuilder sb = new StringBuilder();

        // Base prompt
        String base = BASE_PROMPTS.getOrDefault(agentType, BASE_PROMPTS.get("GENERAL"));
        sb.append(base);

        // Intent-specific extension
        if (intent != null && INTENT_EXTENSIONS.containsKey(intent)) {
            sb.append("\n\n").append(INTENT_EXTENSIONS.get(intent));
        }

        // Personalization
        if (userName != null && !userName.isBlank()) {
            sb.append("\n\n当前用户：").append(userName);
        }

        // Output instructions
        sb.append("\n\n请基于知识库中的相关文档，给出专业、具体、可落地的建议。");

        return sb.toString();
    }

    /**
     * Check if an agent type is supported.
     */
    public boolean supports(String agentType) {
        return BASE_PROMPTS.containsKey(agentType);
    }
}
