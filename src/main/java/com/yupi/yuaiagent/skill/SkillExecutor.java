package com.yupi.yuaiagent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 技能执行器 - 负责调用 LLM 执行技能
 * 
 * 核心职责：
 * 1. 根据技能定义构建 Prompt
 * 2. 调用 LLM 生成回答
 * 3. 支持流式输出
 */
@Slf4j
@Component
public class SkillExecutor {
    
    private final ChatClient chatClient;
    private final SkillRegistry skillRegistry;
    
    public SkillExecutor(ChatModel chatModel, SkillRegistry skillRegistry) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.skillRegistry = skillRegistry;
    }
    
    /**
     * 执行技能（同步）
     * 
     * @param skillName 技能名称
     * @param userInput 用户输入
     * @param variables 模板变量（可选）
     * @return 助手回答
     */
    public String execute(String skillName, String userInput, Map<String, String> variables) {
        SkillDefinition skill = skillRegistry.getByName(skillName);
        if (skill == null) {
            return "未找到技能: " + skillName;
        }
        
        List<Message> messages = buildMessages(skill, userInput, variables);
        
        try {
            return chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("执行技能 {} 失败", skillName, e);
            return "技能执行失败: " + e.getMessage();
        }
    }
    
    /**
     * 执行技能（流式）
     */
    public Flux<String> executeStream(String skillName, String userInput, Map<String, String> variables) {
        SkillDefinition skill = skillRegistry.getByName(skillName);
        if (skill == null) {
            return Flux.just("未找到技能: " + skillName);
        }
        
        List<Message> messages = buildMessages(skill, userInput, variables);
        
        return chatClient.prompt()
                .messages(messages)
                .stream()
                .content();
    }
    
    /**
     * 智能执行 - 自动匹配技能并执行
     * 
     * @param userMessage 用户消息
     * @param chatId 会话 ID（用于上下文）
     * @return 助手回答，如果没有匹配技能返回 null
     */
    public String executeSmart(String userMessage, String chatId) {
        List<SkillDefinition> matchedSkills = skillRegistry.findByIntent(userMessage);
        
        if (matchedSkills.isEmpty()) {
            return null; // 没有匹配的技能
        }
        
        // 使用第一个匹配的技能（置信度最高）
        SkillDefinition skill = matchedSkills.get(0);
        log.info("智能匹配技能: {} (用户输入: {})", skill.getName(), userMessage);
        
        return execute(skill.getName(), userMessage, Map.of());
    }
    
    /**
     * 构建消息列表
     */
    private List<Message> buildMessages(SkillDefinition skill, String userInput, Map<String, String> variables) {
        List<Message> messages = new ArrayList<>();
        
        // 系统提示词
        String systemPrompt = skill.buildFullSystemPrompt();
        messages.add(new SystemMessage(systemPrompt));
        
        // 用户提示词（可能包含模板渲染）
        String userPrompt = userInput;
        if (variables != null && !variables.isEmpty() && skill.getUserPromptTemplate() != null) {
            userPrompt = skill.renderUserPrompt(variables);
            // 如果用户输入不为空，追加到模板后面
            if (userInput != null && !userInput.isEmpty()) {
                userPrompt += "\n\n" + userInput;
            }
        }
        messages.add(new UserMessage(userPrompt));
        
        return messages;
    }
}
