package com.yupi.yuaiagent.budget;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Token 预算分级管理器 — 核心改进三
 *
 * 核心思路：保留 Think（推理过程），压缩 Observation（搜索/工具结果）
 * 因为"思考轨迹"比"原始搜索结果"对推理更有价值。
 *
 * 三档策略：
 * - Normal (< 65%): 搜索结果保留 3000 字符，无任何截断
 * - Compact (65% ~ 85%): 搜索结果截断至 1500 字符，只保留关键段落
 * - Compress (> 85%): 用 LLM 压缩历史 Observation，摘要化
 *
 * 关键取舍：Think（AssistantMessage）绝对不动，只压缩 Observation（ToolResponseMessage）
 */
@Slf4j
@Component
public class TokenBudgetManager {

    private static final double COMPACT_THRESHOLD = 0.65;   // 65% 开始瘦身
    private static final double COMPRESS_THRESHOLD = 0.85;  // 85% 强力压缩

    // Normal 模式：Observation 最大保留字符数
    private static final int OBSERVATION_MAX_NORMAL = 3000;
    // Compact 模式：Observation 截断字符数
    private static final int OBSERVATION_MAX_COMPACT = 1500;

    private static final double CHARS_PER_TOKEN = 2.5;

    private final long maxTokens; // 模型上下文窗口 Token 预算

    @Autowired(required = false)
    private ChatModel chatModel; // 用于 Compress 模式下调用 LLM 做摘要

    public TokenBudgetManager() {
        this(8000); // 默认 8000 tokens（对应 application.yml 的 max-prompt-tokens）
    }

    public TokenBudgetManager(long maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * 每次 think() 前调用 — 根据当前 Token 用量决定压缩策略。
     * 只压缩 Observation（ToolResponseMessage），绝不动 Think（AssistantMessage）。
     *
     * @param messageList the agent's message list (mutable)
     */
    public void checkBudget(List<Message> messageList) {
        try {
            long currentTokens = estimateTokens(messageList);
            double ratio = (double) currentTokens / maxTokens;

            if (ratio >= COMPRESS_THRESHOLD) {
                // Compress 模式：用 LLM 摘要化 Observation
                compressObservations(messageList);
                log.info("[TokenBudgetManager] Compress mode triggered ({} tokens, {}%)",
                        currentTokens, Math.round(ratio * 100));
            } else if (ratio >= COMPACT_THRESHOLD) {
                // Compact 模式：截断 Observation 至 1500 字符
                compactObservations(messageList);
                log.info("[TokenBudgetManager] Compact mode triggered ({} tokens, {}%)",
                        currentTokens, Math.round(ratio * 100));
            }
            // Normal 模式：不操作（保留 3000 字符是在 act() 写入时控制的）
        } catch (Exception e) {
            log.warn("[TokenBudgetManager] budget check failed, skipping: {}", e.getMessage());
        }
    }

    /**
     * Compact 模式：将所有 Observation 截断至 1500 字符，只保留开头的关键段落。
     * Think（AssistantMessage）和 System 消息不动。
     */
    private void compactObservations(List<Message> messageList) {
        for (int i = 0; i < messageList.size(); i++) {
            Message msg = messageList.get(i);
            if (isObservation(msg)) {
                String content = msg.getText();
                if (content != null && content.length() > OBSERVATION_MAX_COMPACT) {
                    String truncated = content.substring(0, OBSERVATION_MAX_COMPACT) + "\n...(已截断，保留关键段落)";
                    messageList.set(i, rebuildMessage(msg, truncated));
                }
            }
        }
    }

    /**
     * Compress 模式：用 LLM 将 Observation 摘要化为 1-2 句话。
     * 保留最近 2 条 Observation 不压缩（可能还需要引用）。
     * Think（AssistantMessage）绝对不动。
     */
    private void compressObservations(List<Message> messageList) {
        // 找出所有 Observation 的索引
        List<Integer> observationIndices = new ArrayList<>();
        for (int i = 0; i < messageList.size(); i++) {
            if (isObservation(messageList.get(i))) {
                observationIndices.add(i);
            }
        }

        // 保留最近 2 条 Observation 不压缩
        int preserveCount = 2;
        if (observationIndices.size() <= preserveCount) return;

        List<Integer> toCompress = observationIndices.subList(0, observationIndices.size() - preserveCount);

        for (int idx : toCompress) {
            Message msg = messageList.get(idx);
            String content = msg.getText();
            if (content == null || content.length() < 200) continue; // 太短的不需要压缩

            String summary = summarizeWithLLM(content);
            messageList.set(idx, rebuildMessage(msg, "[摘要] " + summary));
        }

        long afterTokens = estimateTokens(messageList);
        log.info("[TokenBudgetManager] compressed observations, current tokens: {}", afterTokens);
    }

    /**
     * 用 LLM 将内容摘要化。如果 LLM 不可用，fallback 到字符截断。
     */
    private String summarizeWithLLM(String content) {
        if (chatModel != null) {
            try {
                String prompt = "请用1-2句话总结以下搜索结果的核心信息，只保留最关键的事实：\n\n" + content;
                var response = chatModel.call(new Prompt(prompt));
                String summary = response.getResult().getOutput().getText();
                if (summary != null && !summary.isBlank()) {
                    return summary;
                }
            } catch (Exception e) {
                log.warn("[TokenBudgetManager] LLM summarization failed, using fallback: {}", e.getMessage());
            }
        }
        // Fallback：截取前 200 字符
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    /**
     * 判断消息是否为 Observation（工具返回的搜索/执行结果）。
     * ToolResponseMessage 是 Spring AI 的工具返回消息类型。
     * 带有 "[Guard]" 前缀的 UserMessage 是系统引导消息，不算 Observation。
     */
    private boolean isObservation(Message msg) {
        if (msg instanceof ToolResponseMessage) {
            return true;
        }
        // 有些工具结果可能以 UserMessage 形式存在（如手动追加的搜索结果）
        if (msg instanceof UserMessage) {
            String text = msg.getText();
            if (text != null && text.startsWith("工具 ") && text.contains("返回的结果：")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 重建消息（保持类型），替换内容。
     */
    private Message rebuildMessage(Message original, String newContent) {
        if (original instanceof ToolResponseMessage) {
            // ToolResponseMessage 不太好直接替换内容，用 UserMessage 包装摘要
            return new UserMessage(newContent);
        }
        if (original instanceof UserMessage) {
            return new UserMessage(newContent);
        }
        if (original instanceof SystemMessage) {
            return new SystemMessage(newContent);
        }
        if (original instanceof AssistantMessage) {
            return new AssistantMessage(newContent);
        }
        return new UserMessage(newContent);
    }

    /**
     * 估算 Token 数（近似：1 token ≈ 2.5 字符）
     */
    long estimateTokens(List<Message> messages) {
        return messages.stream()
                .map(Message::getText)
                .filter(t -> t != null)
                .mapToLong(t -> Math.round(t.length() / CHARS_PER_TOKEN))
                .sum();
    }

    /**
     * 对新写入的 Observation 内容做 Normal 模式截断（3000 字符上限）。
     * 在 ToolCallAgent.act() 写入结果时调用。
     */
    public String truncateForNormal(String observationContent) {
        if (observationContent == null) return null;
        if (observationContent.length() <= OBSERVATION_MAX_NORMAL) {
            return observationContent;
        }
        return observationContent.substring(0, OBSERVATION_MAX_NORMAL) + "\n...(结果过长，已截断至3000字符)";
    }
}
