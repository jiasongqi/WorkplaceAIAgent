package com.yupi.yuaiagent.agent.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * 压缩后的对话记忆
 * 将历史对话压缩为关键信息摘要，保留用户关键需求、已确认的信息、
 * 未解决的问题、重要决策和约定事项等关键信息
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompressedMemory {

    /**
     * 会话 ID
     */
    private String chatId;

    /**
     * Agent 类型
     */
    private String agentType;

    /**
     * 压缩摘要内容
     */
    private String summary;

    /**
     * 用户关键需求
     */
    private String keyNeeds;

    /**
     * 已确认的信息
     */
    private String confirmedInfo;

    /**
     * 未解决的问题
     */
    private String unresolvedIssues;

    /**
     * 重要决策
     */
    private String decisions;

    /**
     * 约定事项
     */
    private String agreements;

    /**
     * 原始消息数量
     */
    private int originalMessageCount;

    /**
     * 压缩时间
     */
    private Instant compressedAt;

    /**
     * 压缩版本（同一会话可多次压缩）
     */
    private int version;
}
