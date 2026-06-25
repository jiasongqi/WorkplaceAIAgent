package com.yupi.yuaiagent.memory.summary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.memory.TokenBudgetAllocator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Summary Layer (L3) — 轻量化对话摘要层。
 *
 * <p>将对话提炼为结构化的要点清单（{@link SummaryChecklist}），
 * 每个用户最多保留 K 条最近的清单，超出时按 FIFO 淘汰最旧的。
 *
 * <p>持久化策略：每个 userId 对应一个 JSON 文件，存储在 {@code ./tmp/memory/summaries/} 目录下。
 *
 * <p>满足 Requirements 4.1~4.7。
 */
@Slf4j
@Component
public class SummaryLayer {

    private static final String SUMMARY_GENERATION_PROMPT = """
            请从以下对话中提取要点清单，输出 JSON 格式：
            {"topics":["..."],"decisions":["..."],"actionItems":["..."],"unresolvedQuestions":["..."]}
            
            规则：
            1. 每个字段为字符串数组，包含简洁的要点描述
            2. 如果某个字段没有相关内容，使用空数组 []
            3. 每条要点不超过20个字
            4. 只输出 JSON，不要其他文字
            
            对话内容：
            %s
            """;

    private final ChatClient chatClient;
    private final TokenBudgetAllocator tokenBudgetAllocator;
    private final Path storageDir;
    private final ObjectMapper objectMapper;

    private final int maxChecklists;
    private final int triggerThreshold;

    /** 内存索引：userId → 清单列表（按时间正序，新的在末尾） */
    private final ConcurrentHashMap<String, List<SummaryChecklist>> summaryIndex = new ConcurrentHashMap<>();

    /** 每个 userId 的读写锁 */
    private final ConcurrentHashMap<String, ReadWriteLock> locks = new ConcurrentHashMap<>();

    @Autowired
    public SummaryLayer(
            @Qualifier("dashscopeChatModel") ChatModel chatModel,
            TokenBudgetAllocator tokenBudgetAllocator,
            @Value("${memory.layers.summary.storage-dir:./tmp/memory/summaries}") String storageDir,
            @Value("${memory.layers.summary.max-checklists:5}") int maxChecklists,
            @Value("${memory.layers.summary.trigger-threshold:10}") int triggerThreshold) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.tokenBudgetAllocator = tokenBudgetAllocator;
        this.storageDir = Path.of(storageDir);
        this.maxChecklists = maxChecklists;
        this.triggerThreshold = triggerThreshold;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 用于测试的构造函数（不依赖 Spring 注入）。
     */
    SummaryLayer(ChatClient chatClient,
                 TokenBudgetAllocator tokenBudgetAllocator,
                 String storageDir,
                 int maxChecklists,
                 int triggerThreshold) {
        this.chatClient = chatClient;
        this.tokenBudgetAllocator = tokenBudgetAllocator;
        this.storageDir = Path.of(storageDir);
        this.maxChecklists = maxChecklists;
        this.triggerThreshold = triggerThreshold;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 初始化：创建存储目录，加载已有摘要文件到内存。
     */
    @PostConstruct
    void init() {
        try {
            Files.createDirectories(storageDir);
            loadExistingFiles();
            log.info("SummaryLayer 初始化完成，存储目录: {}, 已加载 {} 个用户摘要, maxChecklists={}, triggerThreshold={}",
                    storageDir.toAbsolutePath(), summaryIndex.size(), maxChecklists, triggerThreshold);
        } catch (IOException e) {
            log.error("SummaryLayer 初始化失败，无法创建存储目录: {}", storageDir, e);
        }
    }

    /**
     * 判断是否应触发摘要生成。
     *
     * <p>满足 Req 4.1：消息数超过阈值时触发。
     *
     * @param messageCount 当前对话消息数
     * @return 是否应触发摘要生成
     */
    public boolean shouldTrigger(int messageCount) {
        return messageCount >= triggerThreshold;
    }

    /**
     * 使用 LLM 生成摘要清单并存储。
     *
     * <p>满足 Req 4.1/4.2/4.3/4.4/4.5/4.7：
     * 调用 LLM 提取要点 → 存储为 SummaryChecklist → FIFO 淘汰超出的 → 持久化到 JSON。
     *
     * <p>如果 LLM 调用失败，记录错误日志并创建一个最小化的降级清单（从首尾消息提取 topic），
     * 绝不向外传播异常。
     *
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @param messages       对话消息列表
     */
    public void generateAndStore(String userId, String conversationId, List<Message> messages) {
        if (userId == null || conversationId == null || messages == null || messages.isEmpty()) {
            return;
        }

        SummaryChecklist checklist;
        try {
            checklist = generateChecklistFromLLM(conversationId, messages);
        } catch (Exception e) {
            log.error("用户 {} 会话 {} 摘要生成失败，使用降级策略", userId, conversationId, e);
            checklist = createFallbackChecklist(conversationId, messages);
        }

        store(userId, checklist);
    }

    /**
     * 获取用户最近的摘要，格式化为适合上下文注入的文本，遵循 token 预算。
     *
     * <p>满足 Req 4.6：格式化为简洁的 bullet list，不超过 500 tokens（或指定 tokenBudget）。
     *
     * @param userId      用户 ID
     * @param tokenBudget 最大 token 数
     * @return 格式化后的摘要文本；无摘要时返回空字符串
     */
    public String getRecentSummaries(String userId, int tokenBudget) {
        List<SummaryChecklist> checklists = getChecklists(userId);
        if (checklists.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【近期对话摘要】\n");

        // 从最新到最旧遍历
        for (int i = checklists.size() - 1; i >= 0; i--) {
            SummaryChecklist c = checklists.get(i);
            sb.append(formatChecklist(c));
        }

        String formatted = sb.toString();
        return tokenBudgetAllocator.truncateToTokens(formatted, tokenBudget);
    }

    /**
     * 获取指定用户的所有摘要清单（按时间正序）。
     *
     * @param userId 用户 ID
     * @return 清单列表，不存在时返回空列表
     */
    public List<SummaryChecklist> getChecklists(String userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        ReadWriteLock lock = getLock(userId);
        lock.readLock().lock();
        try {
            List<SummaryChecklist> list = summaryIndex.getOrDefault(userId, Collections.emptyList());
            return new ArrayList<>(list);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 存储一条摘要清单，执行 FIFO 淘汰策略。
     *
     * <p>满足 Req 4.3/4.4：最多保留 K 条，超出时淘汰最旧的。
     *
     * <p>此方法可由 ExtractionPipeline 直接调用，跳过 LLM 生成步骤（因提取管道已完成 LLM 调用）。
     *
     * @param userId    用户 ID
     * @param checklist 摘要清单
     */
    public void store(String userId, SummaryChecklist checklist) {
        if (userId == null || checklist == null) {
            return;
        }

        ReadWriteLock lock = getLock(userId);
        lock.writeLock().lock();
        try {
            List<SummaryChecklist> list = summaryIndex.computeIfAbsent(userId, k -> new ArrayList<>());
            list.add(checklist);

            // FIFO 淘汰：保留最新的 maxChecklists 条
            while (list.size() > maxChecklists) {
                SummaryChecklist removed = list.remove(0);
                log.debug("用户 {} FIFO 淘汰摘要: conversationId={}", userId, removed.conversationId());
            }

            persistToFile(userId, list);
            log.info("用户 {} 摘要已存储, conversationId={}, 当前 {} 条",
                    userId, checklist.conversationId(), list.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========== 内部方法 ==========

    /**
     * 调用 LLM 生成结构化的摘要清单。
     */
    private SummaryChecklist generateChecklistFromLLM(String conversationId, List<Message> messages) {
        String conversationText = buildConversationText(messages);
        String prompt = String.format(SUMMARY_GENERATION_PROMPT, conversationText);

        String llmOutput = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return parseChecklistFromJson(conversationId, llmOutput);
    }

    /**
     * 解析 LLM 返回的 JSON 为 SummaryChecklist。
     */
    private SummaryChecklist parseChecklistFromJson(String conversationId, String jsonOutput) {
        if (jsonOutput == null || jsonOutput.isBlank()) {
            return createEmptyChecklist(conversationId);
        }

        try {
            // 尝试提取 JSON 部分（LLM 可能在 JSON 前后添加额外文本）
            String json = extractJson(jsonOutput);
            Map<String, List<String>> parsed = objectMapper.readValue(
                    json, new TypeReference<Map<String, List<String>>>() {});

            return new SummaryChecklist(
                    conversationId,
                    Instant.now(),
                    parsed.getOrDefault("topics", Collections.emptyList()),
                    parsed.getOrDefault("decisions", Collections.emptyList()),
                    parsed.getOrDefault("actionItems", Collections.emptyList()),
                    parsed.getOrDefault("unresolvedQuestions", Collections.emptyList())
            );
        } catch (Exception e) {
            log.warn("解析摘要 JSON 失败，conversationId={}, 原始输出: {}", conversationId, jsonOutput, e);
            return createEmptyChecklist(conversationId);
        }
    }

    /**
     * 从可能包含额外文本的字符串中提取 JSON 对象。
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * 创建降级版摘要清单：从消息首尾提取 topic，不包含原始文本。
     */
    private SummaryChecklist createFallbackChecklist(String conversationId, List<Message> messages) {
        List<String> topics = new ArrayList<>();

        // 从第一条用户消息和最后一条用户消息中提取简短 topic
        for (Message msg : messages) {
            if (msg instanceof UserMessage) {
                String text = msg.getText();
                if (text != null && !text.isBlank()) {
                    topics.add(truncateText(text, 20));
                    break;
                }
            }
        }
        // 从最后一条消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage) {
                String text = msg.getText();
                if (text != null && !text.isBlank()) {
                    String topic = truncateText(text, 20);
                    if (!topics.contains(topic)) {
                        topics.add(topic);
                    }
                    break;
                }
            }
        }

        return new SummaryChecklist(
                conversationId,
                Instant.now(),
                topics,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private SummaryChecklist createEmptyChecklist(String conversationId) {
        return new SummaryChecklist(
                conversationId,
                Instant.now(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    /**
     * 截断文本到指定长度（用于 fallback topic 提取，不存储原始文本）。
     */
    private String truncateText(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }

    /**
     * 构建对话文本（用于 LLM 提示词）。
     */
    private String buildConversationText(List<Message> messages) {
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

    private String getRoleName(Message message) {
        if (message instanceof UserMessage) {
            return "用户";
        } else if (message instanceof AssistantMessage) {
            return "助手";
        }
        return "系统";
    }

    /**
     * 格式化单条摘要为 bullet list。
     */
    private String formatChecklist(SummaryChecklist c) {
        StringBuilder sb = new StringBuilder();
        if (!c.topics().isEmpty()) {
            sb.append("  话题: ").append(String.join("、", c.topics())).append("\n");
        }
        if (!c.decisions().isEmpty()) {
            sb.append("  决策: ").append(String.join("、", c.decisions())).append("\n");
        }
        if (!c.actionItems().isEmpty()) {
            sb.append("  待办: ").append(String.join("、", c.actionItems())).append("\n");
        }
        if (!c.unresolvedQuestions().isEmpty()) {
            sb.append("  待解决: ").append(String.join("、", c.unresolvedQuestions())).append("\n");
        }
        return sb.toString();
    }

    private ReadWriteLock getLock(String userId) {
        return locks.computeIfAbsent(userId, k -> new ReentrantReadWriteLock());
    }

    private void persistToFile(String userId, List<SummaryChecklist> checklists) {
        Path filePath = storageDir.resolve(userId + ".json");
        try {
            objectMapper.writeValue(filePath.toFile(), checklists);
        } catch (IOException e) {
            log.error("用户 {} 摘要持久化失败: {}", userId, filePath, e);
        }
    }

    private void loadExistingFiles() {
        if (!Files.exists(storageDir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(storageDir)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadFile);
        } catch (IOException e) {
            log.error("加载摘要存储文件失败", e);
        }
    }

    private void loadFile(Path filePath) {
        try {
            String fileName = filePath.getFileName().toString();
            String userId = fileName.substring(0, fileName.length() - ".json".length());
            List<SummaryChecklist> checklists = objectMapper.readValue(
                    filePath.toFile(),
                    new TypeReference<List<SummaryChecklist>>() {}
            );
            summaryIndex.put(userId, new ArrayList<>(checklists));
            log.debug("已加载用户 {} 的 {} 条摘要", userId, checklists.size());
        } catch (IOException e) {
            log.error("加载摘要文件失败: {}", filePath, e);
        }
    }
}
