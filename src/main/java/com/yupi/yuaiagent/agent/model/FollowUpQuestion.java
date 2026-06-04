package com.yupi.yuaiagent.agent.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 追问问题实体
 * 用于引导用户补充信息
 * 
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpQuestion {

    /**
     * 问题字段名（如 name, contact, appointmentTime）
     */
    private String fieldName;

    /**
     * 问题显示名称（如 "您的姓名"、"联系方式"）
     */
    private String displayName;

    /**
     * 问题内容
     */
    private String question;

    /**
     * 是否为核心信息
     */
    private boolean core;

    /**
     * 是否已收集
     */
    private boolean collected;

    /**
     * 用户的回答
     */
    private String answer;

    /**
     * 验证规则（正则表达式）
     */
    private String validationRegex;

    /**
     * 验证失败提示
     */
    private String validationMessage;

    /**
     * 问题优先级（数字越小优先级越高）
     */
    private int priority;

    /**
     * 创建核心信息追问问题
     */
    public static FollowUpQuestion createCoreQuestion(String fieldName, String displayName, 
                                                       String question, String validationRegex, 
                                                       String validationMessage, int priority) {
        return FollowUpQuestion.builder()
                .fieldName(fieldName)
                .displayName(displayName)
                .question(question)
                .core(true)
                .collected(false)
                .validationRegex(validationRegex)
                .validationMessage(validationMessage)
                .priority(priority)
                .build();
    }

    /**
     * 创建非核心信息追问问题
     */
    public static FollowUpQuestion createOptionalQuestion(String fieldName, String displayName, 
                                                           String question, int priority) {
        return FollowUpQuestion.builder()
                .fieldName(fieldName)
                .displayName(displayName)
                .question(question)
                .core(false)
                .collected(false)
                .priority(priority)
                .build();
    }

    /**
     * 标记为已收集
     */
    public void markCollected(String answer) {
        this.collected = true;
        this.answer = answer;
    }

    /**
     * 验证答案格式
     */
    public boolean validateAnswer(String answer) {
        if (validationRegex == null || validationRegex.isEmpty()) {
            return true;
        }
        return answer.matches(validationRegex);
    }
}
