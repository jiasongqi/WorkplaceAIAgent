package com.yupi.yuaiagent.memory.summary;

import java.time.Instant;
import java.util.List;

/**
 * 对话摘要要点清单（L3 轻量化摘要层数据模型）。
 *
 * <p>每次对话结束或消息达到阈值时，由 LLM 提取生成。
 * 仅保留提炼的要点，不存储原始对话文本。
 *
 * <p>满足 Requirements 4.2 / 4.5：
 * 提取 topics（讨论话题）、decisions（决策）、actionItems（行动事项）、
 * unresolvedQuestions（未解决问题），不含原始文本。
 *
 * @param conversationId     会话 ID
 * @param createdAt          生成时间
 * @param topics             讨论的话题列表
 * @param decisions          做出的决策列表
 * @param actionItems        行动事项列表
 * @param unresolvedQuestions 未解决的问题列表
 */
public record SummaryChecklist(
        String conversationId,
        Instant createdAt,
        List<String> topics,
        List<String> decisions,
        List<String> actionItems,
        List<String> unresolvedQuestions
) {}
