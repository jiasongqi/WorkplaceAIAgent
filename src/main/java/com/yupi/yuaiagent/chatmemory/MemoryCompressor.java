package com.yupi.yuaiagent.chatmemory;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.agent.model.CompressedMemory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆压缩器
 * 调用 LLM 将历史对话压缩为结构化的关键信息摘要。
 *
 * <p>摘要包含五要素（对应 Requirements 4.6）：
 * <ol>
 *     <li>关键需求（keyNeeds）：用户的核心需求和目标</li>
 *     <li>已确认信息（confirmedInfo）：双方已达成共识的事实（姓名、联系方式、时间等）</li>
 *     <li>未解决问题（unresolvedIssues）：仍待处理的问题</li>
 *     <li>重要决策（decisions）：已经做出的决定</li>
 *     <li>约定事项（agreements）：双方约定的事项</li>
 * </ol>
 *
 * <p>压缩时保留最近 N 轮完整对话（N 可通过
 * {@code chat.memory.compression.recent-turns} 配置，默认 5 轮），
 * 并将结构化摘要作为系统消息（{@link SystemMessage}）加入上下文，
 * 满足 Requirements 3.1 / 3.2 / 3.4 / 4.6。
 *
 * @author jsq
 */
@Slf4j
@Component
public class MemoryCompressor {

    private final ChatClient chatClient;

    /**
     * 保留最近 N 轮完整对话，默认 5 轮。
     * 与 {@link TurnCompressionStrategy#getRecentTurns()} 使用同一配置项，保持一致。
     */
    @Value("${chat.memory.compression.recent-turns:5}")
    private int recentTurns = 5;

    /** 五要素结构化摘要的分段标签（顺序固定，便于解析） */
    private static final String TAG_KEY_NEEDS = "【关键需求】";
    private static final String TAG_CONFIRMED_INFO = "【已确认信息】";
    private static final String TAG_UNRESOLVED_ISSUES = "【未解决问题】";
    private static final String TAG_DECISIONS = "【重要决策】";
    private static final String TAG_AGREEMENTS = "【约定事项】";

    private static final String[] SECTION_TAGS = {
            TAG_KEY_NEEDS, TAG_CONFIRMED_INFO, TAG_UNRESOLVED_ISSUES, TAG_DECISIONS, TAG_AGREEMENTS
    };

    /**
     * 结构化压缩提示词。
     * 要求 LLM 严格按五个标签分段输出，便于稳定解析为 {@link CompressedMemory} 的各字段。
     */
    private static final String COMPRESSION_PROMPT = """
            请分析以下对话历史，提取并整理为结构化摘要。
            必须包含以下五个部分，每个部分以指定标签开头且标签独占一行，
            在标签下一行写对应内容；若某部分没有内容，请填写"无"。
            请使用简洁中文，不要输出标签以外的额外说明或前后缀。

            【关键需求】
            （用户的核心需求和目标）

            【已确认信息】
            （双方已达成共识的事实，如姓名、联系方式、预约时间等）

            【未解决问题】
            （仍待处理的问题）

            【重要决策】
            （已经做出的决定）

            【约定事项】
            （双方约定的事项）

            对话历史：
            {history}
            """;

    public MemoryCompressor(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        log.info("MemoryCompressor 初始化完成，保留最近 {} 轮完整对话", recentTurns);
    }

    /**
     * 压缩对话历史，生成五要素结构化摘要并封装为系统消息。
     *
     * <p>该方法供 {@link FileBasedChatMemory} 在压缩旧消息时调用，
     * 入参通常已是"待压缩的旧消息"，返回的系统消息会被加入上下文。
     *
     * @param messages 需要压缩的消息列表（旧消息）
     * @return 压缩后的摘要系统消息
     */
    public Message compress(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new SystemMessage("无历史对话记录。");
        }

        try {
            CompressedMemory memory = summarize(messages, null, null);
            return buildSummaryMessage(memory);
        } catch (Exception e) {
            log.error("对话记忆压缩失败", e);
            // 压缩失败时返回降级的简单摘要，保证对话连续性
            return createSimpleSummary(messages);
        }
    }

    /**
     * 压缩对话并保留最近 N 轮完整对话。
     *
     * <p>实现 Requirements 3.2 / 3.4：将较早的对话压缩为结构化摘要系统消息，
     * 与最近 N 轮（默认 5）完整对话拼接后返回，作为新的上下文。
     * 当消息总量不足以触发压缩（不超过保留窗口）时，原样返回。
     *
     * @param messages 全量对话消息
     * @return 压缩后的消息列表：[摘要系统消息, 最近 N 轮完整对话...]
     */
    public List<Message> compressWithRetention(List<Message> messages) {
        return compressWithRetention(messages, recentTurns);
    }

    /**
     * 压缩对话并保留最近 retainTurns 轮完整对话。
     *
     * @param messages    全量对话消息
     * @param retainTurns 保留的最近轮数（每轮约 2 条消息）
     * @return 压缩后的消息列表：[摘要系统消息, 最近 retainTurns 轮完整对话...]
     */
    public List<Message> compressWithRetention(List<Message> messages, int retainTurns) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        int retainCount = Math.max(0, retainTurns) * 2;
        // 不足以压缩：保留窗口已覆盖全部消息，原样返回
        if (messages.size() <= retainCount) {
            return new ArrayList<>(messages);
        }

        int splitIndex = messages.size() - retainCount;
        List<Message> oldMessages = new ArrayList<>(messages.subList(0, splitIndex));
        List<Message> recentMessages = new ArrayList<>(messages.subList(splitIndex, messages.size()));

        Message summaryMessage = compress(oldMessages);

        List<Message> result = new ArrayList<>();
        result.add(summaryMessage);
        result.addAll(recentMessages);

        log.info("压缩并保留最近 {} 轮：压缩消息 {} 条，保留消息 {} 条",
                retainTurns, oldMessages.size(), recentMessages.size());
        return result;
    }

    /**
     * 压缩对话并返回结构化的 {@link CompressedMemory}（含五要素字段）。
     *
     * @param messages  需要压缩的消息列表
     * @param chatId    会话 ID（可为 null）
     * @param agentType Agent 类型（可为 null）
     * @return 结构化压缩记忆
     */
    public CompressedMemory compressToMemory(List<Message> messages, String chatId, String agentType) {
        if (messages == null || messages.isEmpty()) {
            return CompressedMemory.builder()
                    .chatId(chatId)
                    .agentType(agentType)
                    .summary("无历史对话记录。")
                    .keyNeeds("无")
                    .confirmedInfo("无")
                    .unresolvedIssues("无")
                    .decisions("无")
                    .agreements("无")
                    .originalMessageCount(0)
                    .compressedAt(Instant.now())
                    .version(1)
                    .build();
        }
        return summarize(messages, chatId, agentType);
    }

    /**
     * 调用 LLM 生成结构化摘要并解析为 {@link CompressedMemory}。
     */
    private CompressedMemory summarize(List<Message> messages, String chatId, String agentType) {
        String historyText = buildHistoryText(messages);

        String llmOutput = chatClient.prompt()
                .user(COMPRESSION_PROMPT.replace("{history}", historyText))
                .call()
                .content();

        Map<String, String> sections = splitSections(llmOutput == null ? "" : llmOutput);

        CompressedMemory memory = CompressedMemory.builder()
                .chatId(chatId)
                .agentType(agentType)
                .summary(llmOutput == null ? "" : llmOutput.trim())
                .keyNeeds(sections.get(TAG_KEY_NEEDS))
                .confirmedInfo(sections.get(TAG_CONFIRMED_INFO))
                .unresolvedIssues(sections.get(TAG_UNRESOLVED_ISSUES))
                .decisions(sections.get(TAG_DECISIONS))
                .agreements(sections.get(TAG_AGREEMENTS))
                .originalMessageCount(messages.size())
                .compressedAt(Instant.now())
                .version(1)
                .build();

        log.info("对话记忆压缩完成，原始消息数：{}，摘要长度：{}",
                messages.size(), memory.getSummary().length());
        return memory;
    }

    /**
     * 将结构化压缩记忆封装为可加入上下文的系统消息。
     */
    private Message buildSummaryMessage(CompressedMemory memory) {
        String compressedMessage = String.format("""
                [对话记忆压缩摘要 - %s]

                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                """,
                LocalDateTime.now(),
                TAG_KEY_NEEDS, safe(memory.getKeyNeeds()),
                TAG_CONFIRMED_INFO, safe(memory.getConfirmedInfo()),
                TAG_UNRESOLVED_ISSUES, safe(memory.getUnresolvedIssues()),
                TAG_DECISIONS, safe(memory.getDecisions()),
                TAG_AGREEMENTS, safe(memory.getAgreements()));
        return new SystemMessage(compressedMessage);
    }

    /**
     * 解析 LLM 输出，按五个标签切分各部分内容。
     * 标签可乱序出现；缺失的标签对应内容为 "无"。
     */
    private Map<String, String> splitSections(String text) {
        Map<String, String> result = new LinkedHashMap<>();

        int[] positions = new int[SECTION_TAGS.length];
        for (int i = 0; i < SECTION_TAGS.length; i++) {
            positions[i] = text.indexOf(SECTION_TAGS[i]);
        }

        for (int i = 0; i < SECTION_TAGS.length; i++) {
            int pos = positions[i];
            if (pos < 0) {
                result.put(SECTION_TAGS[i], "无");
                continue;
            }
            int contentStart = pos + SECTION_TAGS[i].length();
            int contentEnd = text.length();
            // 找到位于当前标签之后、最靠前的下一个标签作为内容终点
            for (int positionOfOther : positions) {
                if (positionOfOther > pos && positionOfOther < contentEnd) {
                    contentEnd = positionOfOther;
                }
            }
            String content = text.substring(contentStart, contentEnd).trim();
            result.put(SECTION_TAGS[i], content.isEmpty() ? "无" : content);
        }
        return result;
    }

    private String safe(String value) {
        return (value == null || value.isBlank()) ? "无" : value.trim();
    }

    /**
     * 构建对话历史文本
     */
    private String buildHistoryText(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            String role = getRoleName(message);
            String content = message.getText();
            if (content != null && !content.isEmpty()) {
                sb.append(role).append("：").append(content).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取角色名称
     */
    private String getRoleName(Message message) {
        if (message instanceof UserMessage) {
            return "用户";
        } else if (message instanceof AssistantMessage) {
            return "助手";
        } else if (message instanceof SystemMessage) {
            return "系统";
        } else {
            return "未知";
        }
    }

    /**
     * 创建简单摘要（LLM 压缩失败时的降级方案），仍保留五要素结构以维持上下文一致性。
     */
    private Message createSimpleSummary(List<Message> messages) {
        int userMessageCount = 0;
        int assistantMessageCount = 0;
        List<String> keyPoints = new ArrayList<>();

        for (Message message : messages) {
            if (message instanceof UserMessage) {
                userMessageCount++;
                String content = message.getText();
                if (content != null && content.length() > 10) {
                    keyPoints.add(content.substring(0, Math.min(50, content.length())) + "...");
                }
            } else if (message instanceof AssistantMessage) {
                assistantMessageCount++;
            }
        }

        String keyNeeds = keyPoints.isEmpty()
                ? "无"
                : String.join("；", keyPoints.subList(0, Math.min(5, keyPoints.size())));

        CompressedMemory fallback = CompressedMemory.builder()
                .summary(String.format("对话统计：用户消息 %d 条，助手回复 %d 条（LLM 摘要失败，降级摘要）",
                        userMessageCount, assistantMessageCount))
                .keyNeeds(keyNeeds)
                .confirmedInfo("无")
                .unresolvedIssues("无")
                .decisions("无")
                .agreements("无")
                .originalMessageCount(messages.size())
                .compressedAt(Instant.now())
                .version(1)
                .build();

        return buildSummaryMessage(fallback);
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

    /**
     * 压缩结果类
     */
    @Data
    public static class CompressionResult {
        /**
         * 压缩后的消息
         */
        private Message compressedMessage;

        /**
         * 压缩的原始消息数
         */
        private int originalMessageCount;

        /**
         * 压缩时间
         */
        private LocalDateTime compressionTime;

        /**
         * 压缩策略
         */
        private String strategy;

        /**
         * 是否成功
         */
        private boolean success;

        /**
         * 错误信息（如果失败）
         */
        private String errorMessage;
    }
}
