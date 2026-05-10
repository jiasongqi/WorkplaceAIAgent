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
        
        return GENERAL;
    }
}
