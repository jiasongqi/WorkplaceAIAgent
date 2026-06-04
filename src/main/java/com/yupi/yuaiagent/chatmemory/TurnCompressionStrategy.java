package com.yupi.yuaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于对话轮数的压缩策略
 * 当对话轮数超过阈值时触发压缩
 * 
 * @author jsq
 */
@Slf4j
@Component
public class TurnCompressionStrategy implements CompressionStrategy {

    /**
     * 对话轮数阈值，默认 20 轮
     */
    @Value("${chat.memory.compression.turn-threshold:20}")
    private int turnThreshold;

    /**
     * 保留最近 N 轮对话的完整内容，默认 5 轮
     */
    @Value("${chat.memory.compression.recent-turns:5}")
    private int recentTurns;

    @Override
    public boolean shouldCompress(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }

        int turnCount = countTurns(messages);
        boolean shouldCompress = turnCount >= turnThreshold;

        if (shouldCompress) {
            log.info("轮数压缩策略触发：当前对话轮数 {} >= 阈值 {}", turnCount, turnThreshold);
        }

        return shouldCompress;
    }

    @Override
    public String getStrategyName() {
        return "TurnBasedCompression";
    }

    @Override
    public String getDescription() {
        return String.format("当对话轮数超过 %d 轮时触发压缩，保留最近 %d 轮", turnThreshold, recentTurns);
    }

    /**
     * 计算对话轮数
     * 一轮 = 一个用户消息 + 一个助手回复
     */
    private int countTurns(List<Message> messages) {
        int userMessageCount = 0;
        for (Message message : messages) {
            if (message instanceof UserMessage) {
                userMessageCount++;
            }
        }
        return userMessageCount;
    }

    /**
     * 获取需要保留的最近消息
     * 
     * @param messages 所有消息
     * @return 最近 N 轮的消息
     */
    public List<Message> getRecentMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        int recentMessageCount = recentTurns * 2; // 每轮 2 条消息
        if (messages.size() <= recentMessageCount) {
            return messages;
        }

        return messages.subList(messages.size() - recentMessageCount, messages.size());
    }

    /**
     * 获取需要压缩的旧消息
     * 
     * @param messages 所有消息
     * @return 需要压缩的旧消息
     */
    public List<Message> getOldMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        int recentMessageCount = recentTurns * 2;
        if (messages.size() <= recentMessageCount) {
            return List.of();
        }

        return messages.subList(0, messages.size() - recentMessageCount);
    }

    /**
     * 获取对话轮数阈值
     */
    public int getTurnThreshold() {
        return turnThreshold;
    }

    /**
     * 设置对话轮数阈值
     */
    public void setTurnThreshold(int turnThreshold) {
        this.turnThreshold = turnThreshold;
    }

    /**
     * 获取保留的最近轮数
     */
    public int getRecentTurns() {
        return recentTurns;
    }

    /**
     * 设置保留的最近轮数
     */
    public void setRecentTurns(int recentTurns) {
        this.recentTurns = recentTurns;
    }
}
