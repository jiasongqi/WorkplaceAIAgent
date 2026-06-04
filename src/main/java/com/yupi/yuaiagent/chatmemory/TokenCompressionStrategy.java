package com.yupi.yuaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Token 阈值的压缩策略
 * 当对话 Token 数量超过阈值时触发压缩
 * 
 * @author jsq
 */
@Slf4j
@Component
public class TokenCompressionStrategy implements CompressionStrategy {

    /**
     * Token 阈值，默认 4000
     */
    @Value("${chat.memory.compression.token-threshold:4000}")
    private int tokenThreshold;

    /**
     * 平均每个字符的 Token 数（中文约 1.5，英文约 0.25）
     * 这里使用保守估计 1.0
     */
    private static final double AVG_TOKENS_PER_CHAR = 1.0;

    @Override
    public boolean shouldCompress(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }

        int totalTokens = estimateTokens(messages);
        boolean shouldCompress = totalTokens >= tokenThreshold;

        if (shouldCompress) {
            log.info("Token 压缩策略触发：当前 Token 数 {} >= 阈值 {}", totalTokens, tokenThreshold);
        }

        return shouldCompress;
    }

    @Override
    public String getStrategyName() {
        return "TokenBasedCompression";
    }

    @Override
    public String getDescription() {
        return String.format("当对话 Token 数超过 %d 时触发压缩", tokenThreshold);
    }

    /**
     * 估算消息列表的 Token 数量
     * 简单实现：基于字符数估算
     */
    private int estimateTokens(List<Message> messages) {
        int totalChars = 0;
        for (Message message : messages) {
            if (message.getText() != null) {
                totalChars += message.getText().length();
            }
        }
        return (int) (totalChars * AVG_TOKENS_PER_CHAR);
    }

    /**
     * 获取单条消息的估算 Token 数
     */
    public int estimateMessageTokens(Message message) {
        if (message.getText() == null) {
            return 0;
        }
        return (int) (message.getText().length() * AVG_TOKENS_PER_CHAR);
    }

    /**
     * 获取 Token 阈值
     */
    public int getTokenThreshold() {
        return tokenThreshold;
    }

    /**
     * 设置 Token 阈值
     */
    public void setTokenThreshold(int tokenThreshold) {
        this.tokenThreshold = tokenThreshold;
    }
}
