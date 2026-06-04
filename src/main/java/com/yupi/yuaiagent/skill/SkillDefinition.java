package com.yupi.yuaiagent.skill;

import lombok.Data;
import java.util.List;

/**
 * 技能定义 - 参考 Hermes Agent 的 SKILL.md 结构
 * 使用 YAML 格式定义技能，支持动态加载
 */
@Data
public class SkillDefinition {
    
    /**
     * 技能名称（小写 + 连字符，如 interview-prep）
     */
    private String name;
    
    /**
     * 技能描述（触发条件 + 行为说明）
     */
    private String description;
    
    /**
     * 技能版本
     */
    private String version = "1.0.0";
    
    /**
     * 技能作者
     */
    private String author = "Yu AI Agent";
    
    /**
     * 标签（用于分类和搜索）
     */
    private List<String> tags;
    
    /**
     * 系统提示词 - 定义 Agent 角色和行为
     */
    private String systemPrompt;
    
    /**
     * 用户提示词模板 - 支持 {{variable}} 占位符
     */
    private String userPromptTemplate;
    
    /**
     * 需要收集的用户信息字段
     */
    private List<InputField> requiredInputs;
    
    /**
     * 输出格式说明
     */
    private String outputFormat;
    
    /**
     * 示例对话（Few-shot）
     */
    private List<Example> examples;
    
    @Data
    public static class InputField {
        private String name;
        private String description;
        private String type = "text"; // text, number, select
        private List<String> options; // type=select 时使用
        private boolean required = true;
    }
    
    @Data
    public static class Example {
        private String user;
        private String assistant;
    }
    
    /**
     * 生成完整的系统提示词（含输出格式和示例）
     */
    public String buildFullSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt);
        
        if (outputFormat != null && !outputFormat.isEmpty()) {
            sb.append("\n\n## 输出格式\n").append(outputFormat);
        }
        
        if (examples != null && !examples.isEmpty()) {
            sb.append("\n\n## 示例\n");
            for (Example ex : examples) {
                sb.append("用户: ").append(ex.getUser()).append("\n");
                sb.append("助手: ").append(ex.getAssistant()).append("\n\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 渲染用户提示词模板
     */
    public String renderUserPrompt(java.util.Map<String, String> variables) {
        String result = userPromptTemplate;
        for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
