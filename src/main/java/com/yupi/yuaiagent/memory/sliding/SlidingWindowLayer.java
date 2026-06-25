package com.yupi.yuaiagent.memory.sliding;

import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.memory.TokenBudgetAllocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * L1 滑动窗口层 — 当前会话最近 N 条完整消息。
 *
 * <p>职责：
 * <ul>
 *   <li>包装已有的 {@link ChatMemoryManager}，不引入新的持久化层（Req 2.3）</li>
 *   <li>按 maxMessages（默认 20）保留最近消息（Req 2.1 / 2.2）</li>
 *   <li>按 tokenBudget 裁剪最旧消息（Req 2.4）</li>
 *   <li>始终保持消息的插入顺序（Req 2.5）</li>
 * </ul>
 */
@Slf4j
@Component
public class SlidingWindowLayer {

    private final ChatMemoryManager chatMemoryManager;
    private final TokenBudgetAllocator tokenBudgetAllocator;
    private final int maxMessages;

    public SlidingWindowLayer(
            ChatMemoryManager chatMemoryManager,
            TokenBudgetAllocator tokenBudgetAllocator,
            @Value("${memory.layers.sliding-window.max-messages:20}") int maxMessages) {
        this.chatMemoryManager = chatMemoryManager;
        this.tokenBudgetAllocator = tokenBudgetAllocator;
        this.maxMessages = maxMessages;
    }

    /**
     * 获取最近消息，按 maxMessages 和 tokenBudget 双重约束裁剪。
     *
     * <p>算法：
     * <ol>
     *   <li>从 ChatMemoryManager 获取该会话的全部消息</li>
     *   <li>只保留最近 maxMessages 条</li>
     *   <li>估算 token，从最旧消息开始移除直到总量不超 tokenBudget</li>
     *   <li>返回按时间顺序排列的消息列表</li>
     * </ol>
     *
     * @param conversationId 会话 ID
     * @param agentType      Agent 类型（用于从 ChatMemoryManager 获取对应 ChatMemory）
     * @param tokenBudget    Token 预算上限
     * @return 裁剪后的消息列表（按时间顺序，即插入顺序）
     */
    public List<Message> getRecentMessages(String conversationId, String agentType, int tokenBudget) {
        if (conversationId == null || agentType == null) {
            return Collections.emptyList();
        }

        // 1. 从 ChatMemoryManager 获取消息
        ChatMemory chatMemory = chatMemoryManager.getMemory(agentType);
        List<Message> allMessages = chatMemory.get(conversationId);

        if (allMessages == null || allMessages.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 只保留最近 maxMessages 条（Req 2.1 / 2.2）
        List<Message> recent;
        if (allMessages.size() > maxMessages) {
            recent = new ArrayList<>(allMessages.subList(allMessages.size() - maxMessages, allMessages.size()));
        } else {
            recent = new ArrayList<>(allMessages);
        }

        // 3. 按 token 预算裁剪最旧消息（Req 2.4）
        recent = trimToTokenBudget(recent, tokenBudget);

        // 4. 返回按时间顺序排列的列表（Req 2.5）— 已经是插入顺序
        return Collections.unmodifiableList(recent);
    }

    /**
     * 将消息格式化为文本，用于 Context_Window 注入。
     *
     * @param conversationId 会话 ID
     * @param agentType      Agent 类型
     * @param tokenBudget    Token 预算上限
     * @return 格式化后的上下文文本
     */
    public String formatForContext(String conversationId, String agentType, int tokenBudget) {
        List<Message> messages = getRecentMessages(conversationId, agentType, tokenBudget);
        if (messages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            String role = message.getMessageType().name().toLowerCase();
            String text = message.getText();
            if (text != null && !text.isEmpty()) {
                sb.append(role).append(": ").append(text).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 从最旧消息开始逐条移除，直到总 token 不超过 tokenBudget。
     * 保持消息的相对顺序不变。
     */
    private List<Message> trimToTokenBudget(List<Message> messages, int tokenBudget) {
        if (tokenBudget <= 0) {
            return Collections.emptyList();
        }

        int totalTokens = estimateTotalTokens(messages);
        if (totalTokens <= tokenBudget) {
            return messages;
        }

        // 从最旧的消息（索引 0）开始移除
        List<Message> trimmed = new ArrayList<>(messages);
        while (!trimmed.isEmpty() && estimateTotalTokens(trimmed) > tokenBudget) {
            trimmed.remove(0);
        }
        return trimmed;
    }

    /**
     * 估算消息列表的总 token 数。
     */
    private int estimateTotalTokens(List<Message> messages) {
        int total = 0;
        for (Message message : messages) {
            String text = message.getText();
            if (text != null) {
                total += tokenBudgetAllocator.estimateTokens(text);
            }
        }
        return total;
    }
}
