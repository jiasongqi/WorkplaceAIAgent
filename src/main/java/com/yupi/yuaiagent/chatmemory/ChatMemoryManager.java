package com.yupi.yuaiagent.chatmemory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatMemory 统一管理器
 * 解决各 Agent 重复创建 FileBasedChatMemory 的问题
 * 
 * @author jsq
 */
@Component
public class ChatMemoryManager {

    private final Map<String, ChatMemory> agentMemories = new ConcurrentHashMap<>();
    
    private final String baseDir;
    
    public ChatMemoryManager() {
        this.baseDir = System.getProperty("user.dir") + "/tmp/chat-memory";
    }
    
    /**
     * 获取指定 Agent 类型的 ChatMemory
     * 如果不存在则自动创建
     * 
     * @param agentType Agent 类型（如 "resume", "negotiation", "escape", "general"）
     * @return ChatMemory 实例
     */
    public ChatMemory getMemory(String agentType) {
        return agentMemories.computeIfAbsent(agentType, type -> {
            String dir = baseDir + "/" + type;
            return new FileBasedChatMemory(dir);
        });
    }
    
    /**
     * 清除指定 Agent 类型的所有会话记忆
     * 
     * @param agentType Agent 类型
     */
    public void clearAgentMemory(String agentType) {
        agentMemories.remove(agentType);
    }
    
    /**
     * 清除所有 Agent 的会话记忆
     */
    public void clearAll() {
        agentMemories.clear();
    }
}
