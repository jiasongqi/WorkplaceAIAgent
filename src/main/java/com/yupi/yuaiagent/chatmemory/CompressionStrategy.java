package com.yupi.yuaiagent.chatmemory;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 压缩策略接口
 * 定义何时触发对话记忆压缩
 * 
 * @author jsq
 */
public interface CompressionStrategy {

    /**
     * 检查是否需要压缩
     * 
     * @param messages 当前对话消息列表
     * @return 如果需要压缩返回 true
     */
    boolean shouldCompress(List<Message> messages);

    /**
     * 获取策略名称
     * 
     * @return 策略名称
     */
    String getStrategyName();

    /**
     * 获取策略描述
     * 
     * @return 策略描述
     */
    String getDescription();

    /**
     * 压缩策略类型
     */
    enum StrategyType {
        TOKEN_BASED("基于 Token 阈值"),
        TURN_BASED("基于对话轮数"),
        HYBRID("混合策略");

        private final String description;

        StrategyType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
