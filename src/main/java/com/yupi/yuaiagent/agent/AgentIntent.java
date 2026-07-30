package com.yupi.yuaiagent.agent;

/**
 * Agent 意图枚举
 * 用于意图识别结果的标准化处理
 * 
 * @author jsq
 */
public enum AgentIntent {
    
    RESUME("简历优化专家", "求职、简历优化、面试技巧、offer选择"),
    NEGOTIATION("薪资谈判专家", "薪资谈判、涨薪、薪酬分析"),
    ESCAPE("离职规划专家", "离职、辞职、劳动纠纷、工作交接"),
    CONSULTATION("预约咨询专家", "预约咨询、预约专家、咨询预约"),
    DATA_QUERY("数据查询顾问", "数据查询、指标查看、报表、KPI"),
    DIGITAL_EMPLOYEE("数字员工", "创建数字员工、委托专属员工、调整员工人设"),
    GENERAL("职场通用顾问", "其他职场问题");
    
    private final String agentName;
    private final String description;
    
    AgentIntent(String agentName, String description) {
        this.agentName = agentName;
        this.description = description;
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 从原始意图字符串解析枚举
     * 支持模糊匹配（处理模型输出带标点或前缀的情况）
     * 
     * @param rawIntent 原始意图字符串
     * @return 对应的枚举值，未匹配则返回 GENERAL
     */
    public static AgentIntent fromRawIntent(String rawIntent) {
        if (rawIntent == null || rawIntent.isBlank()) {
            return GENERAL;
        }
        
        String normalized = rawIntent.trim().toUpperCase();
        
        // 精确匹配优先
        for (AgentIntent intent : values()) {
            if (intent.name().equals(normalized)) {
                return intent;
            }
        }
        
        // 模糊匹配（处理 "RESUME。" 或 "1. RESUME" 等情况）
        for (AgentIntent intent : values()) {
            if (normalized.contains(intent.name())) {
                return intent;
            }
        }
        
        // 中文关键词模糊匹配（处理模型直接输出中文意图的情况）
        // 预约咨询关键词 → CONSULTATION（Req 1.3）
        if (containsAny(rawIntent, "预约", "咨询")) {
            return CONSULTATION;
        }

        if (containsAny(rawIntent, "数字员工", "专属员工", "创建员工")) {
            return DIGITAL_EMPLOYEE;
        }
        
        return GENERAL;
    }
    
    /**
     * 判断文本是否包含任意一个关键词
     *
     * @param text     待检查文本
     * @param keywords 关键词列表
     * @return 包含任意关键词返回 true，否则返回 false
     */
    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert NLU Pipeline reranked intents to a list of AgentIntent.
     * Used by OrchestratorAgent for multi-intent serial execution (V1 群聊模式).
     *
     * <p>Filters out UNKNOWN and deduplicates. Returns at least 1 intent (GENERAL fallback).
     *
     * @param nluIntents list of (intentName, score) from NluPipeline
     * @return ordered list of AgentIntent (highest score first)
     */
    public static java.util.List<AgentIntent> fromMultiIntent(
            java.util.List<com.yupi.yuaiagent.nlu.UnifiedNluExtractor.IntentScore> nluIntents) {
        java.util.List<AgentIntent> result = new java.util.ArrayList<>();
        java.util.Set<AgentIntent> seen = new java.util.HashSet<>();

        for (var score : nluIntents) {
            try {
                AgentIntent intent = com.yupi.yuaiagent.nlu.NluIntent.valueOf(score.intent()).toAgentIntent();
                if (intent != GENERAL && seen.add(intent)) {
                    result.add(intent);
                }
            } catch (IllegalArgumentException ignored) {
                // UNKNOWN or unmapped → skip
            }
        }

        // At least one intent
        if (result.isEmpty()) {
            result.add(GENERAL);
        }

        return result;
    }
}
