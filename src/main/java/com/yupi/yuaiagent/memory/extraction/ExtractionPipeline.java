package com.yupi.yuaiagent.memory.extraction;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.memory.experience.ExperienceDocument;
import com.yupi.yuaiagent.memory.experience.ExperienceStoreLayer;
import com.yupi.yuaiagent.memory.fact.FactCategory;
import com.yupi.yuaiagent.memory.fact.FactEntry;
import com.yupi.yuaiagent.memory.fact.FactStoreLayer;
import com.yupi.yuaiagent.memory.summary.SummaryChecklist;
import com.yupi.yuaiagent.memory.summary.SummaryLayer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Extraction Pipeline — 异步后处理管道。
 *
 * <p>在每轮对话完成后异步运行，通过 LLM 分类并提取：
 * <ul>
 *   <li>结构化事实 → {@link FactStoreLayer} (L2)</li>
 *   <li>对话摘要 → {@link SummaryLayer} (L3)</li>
 *   <li>经验案例 → {@link ExperienceStoreLayer} (L4)</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>永不阻塞调用者</li>
 *   <li>所有异常内部捕获并记录日志，绝不向外传播</li>
 *   <li>使用独立线程池 {@code memoryExtractionExecutor}，避免影响主流程</li>
 *   <li>单次 LLM 调用同时提取事实、摘要和经验（Single-pass extraction）</li>
 * </ul>
 */
@Slf4j
@Component
public class ExtractionPipeline {

    /**
     * 提取系统提示词 — 指导 LLM 在一次调用中同时分类并提取三类信息。
     */
    private static final String EXTRACTION_SYSTEM_PROMPT = """
            你是一名信息提取专家。请基于下面提供的用户与助手的对话内容，在一次分析中同时提取以下三类信息。
            仅依据对话中有明确证据的信息进行提取，没有依据的部分留空（列表返回空数组），不要编造。

            ## 第一类：结构化事实（facts）
            提取用户明确提到的个人事实信息，每条事实包含：
            - key：事实的键名（如 "姓名"、"行业"、"预算"、"目标薪资"）
            - value：事实的具体值
            - category：事实类别，仅能取以下值之一：
              - identity（身份信息：姓名、年龄、所在城市等）
              - career（职业信息：行业、岗位、公司、工作年限等）
              - preferences（偏好设定：沟通风格、语言、格式偏好等）
              - goals（目标计划：短期/长期职业目标、发展方向等）
              - constraints（约束条件：薪资底线、地域限制、时间约束等）

            ## 第二类：对话摘要（summary）
            提取本次对话的要点清单：
            - topics：讨论的主题列表
            - decisions：达成的决定或结论
            - actionItems：需要后续执行的行动项
            - unresolvedQuestions：尚未解决的问题

            ## 第三类：经验案例（experiences）
            提取用户提到的值得记录的经验或案例，每条包含：
            - content：经验的叙述描述（一两句话概括）
            - outcome：结果分类，仅能取以下值之一：
              - success（成功经验）
              - failure（失败教训）
              - insight（重要洞察）

            请严格按照结构化 JSON 格式输出结果。
            """;

    private final Executor memoryExtractionExecutor;
    private final FactStoreLayer factStoreLayer;
    private final SummaryLayer summaryLayer;
    private final ExperienceStoreLayer experienceStoreLayer;
    private final ChatClient chatClient;

    public ExtractionPipeline(
            @Qualifier("memoryExtractionExecutor") Executor memoryExtractionExecutor,
            FactStoreLayer factStoreLayer,
            SummaryLayer summaryLayer,
            ExperienceStoreLayer experienceStoreLayer,
            ChatModel dashscopeChatModel) {
        this.memoryExtractionExecutor = memoryExtractionExecutor;
        this.factStoreLayer = factStoreLayer;
        this.summaryLayer = summaryLayer;
        this.experienceStoreLayer = experienceStoreLayer;
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
    }

    /**
     * 异步处理对话消息，提取并路由到相应的记忆层。
     *
     * <p>提交到专用线程池执行，立即返回，不阻塞调用者。
     * 任何异常都在内部捕获并记录日志。
     *
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @param agentType      智能体类型
     * @param messages       当前对话消息列表
     */
    public void processAsync(String userId, String conversationId, String agentType, List<Message> messages) {
        CompletableFuture.runAsync(() -> {
            try {
                processMessages(userId, conversationId, agentType, messages);
            } catch (Exception e) {
                log.error("Extraction pipeline error for userId={}, conversationId={}: {}",
                        userId, conversationId, e.getMessage(), e);
            }
        }, memoryExtractionExecutor);
    }

    /**
     * 内部处理逻辑：调用 LLM 进行单次分类提取。
     *
     * <p>流程：
     * <ol>
     *   <li>格式化对话消息为纯文本</li>
     *   <li>调用 LLM 获取结构化提取结果</li>
     *   <li>记录提取结果日志（路由逻辑在 Task 7.3 中实现）</li>
     * </ol>
     *
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @param agentType      智能体类型
     * @param messages       当前对话消息列表
     */
    void processMessages(String userId, String conversationId, String agentType, List<Message> messages) {
        log.info("Extraction processing started for userId={}, conversationId={}, agentType={}, messageCount={}",
                userId, conversationId, agentType, messages != null ? messages.size() : 0);

        String conversationText = formatConversation(messages);
        if (!StringUtils.hasText(conversationText)) {
            log.info("对话内容为空，跳过记忆提取: userId={}, conversationId={}", userId, conversationId);
            return;
        }

        try {
            ExtractionResult result = chatClient.prompt()
                    .system(EXTRACTION_SYSTEM_PROMPT)
                    .user("以下是本次对话内容，请提取信息：\n\n" + conversationText)
                    .call()
                    .entity(ExtractionResult.class);

            if (result == null) {
                log.warn("LLM 提取返回 null: userId={}, conversationId={}", userId, conversationId);
                return;
            }

            logExtractionResult(userId, conversationId, result);

            // Route facts → FactStoreLayer (L2)
            routeFactsToStore(userId, conversationId, result);

            // Route summary → SummaryLayer (L3)
            routeSummaryToStore(userId, conversationId, result);

            // Route experiences → ExperienceStoreLayer (L4)
            routeExperiencesToStore(userId, conversationId, agentType, result);

        } catch (Exception e) {
            log.error("LLM 记忆提取失败: userId={}, conversationId={}, error={}",
                    userId, conversationId, e.getMessage(), e);
        }
    }

    /**
     * 路由提取的事实到 FactStoreLayer。
     *
     * <p>将每条 {@link ExtractedFact} 转换为 {@link FactEntry}，通过 {@link FactCategory#fromString(String)}
     * 映射类别字符串。无效类别的事实将被跳过。
     */
    private void routeFactsToStore(String userId, String conversationId, ExtractionResult result) {
        if (result.facts() == null || result.facts().isEmpty()) {
            return;
        }
        try {
            Instant now = Instant.now();
            for (ExtractedFact fact : result.facts()) {
                try {
                    if (fact.key() == null || fact.key().isBlank() || fact.value() == null || fact.value().isBlank()) {
                        log.debug("跳过无效事实（key 或 value 为空）: userId={}", userId);
                        continue;
                    }
                    FactCategory category = FactCategory.fromString(fact.category());
                    if (category == null) {
                        log.debug("跳过无效类别事实: userId={}, key={}, category={}",
                                userId, fact.key(), fact.category());
                        continue;
                    }
                    FactEntry entry = new FactEntry(
                            fact.key(),
                            fact.value(),
                            category,
                            conversationId,
                            now
                    );
                    factStoreLayer.upsert(userId, entry);
                } catch (Exception e) {
                    log.error("路由单条事实失败: userId={}, key={}, error={}",
                            userId, fact.key(), e.getMessage(), e);
                }
            }
            log.debug("事实路由完成: userId={}, conversationId={}", userId, conversationId);
        } catch (Exception e) {
            log.error("事实路由整体失败: userId={}, conversationId={}, error={}",
                    userId, conversationId, e.getMessage(), e);
        }
    }

    /**
     * 路由提取的摘要到 SummaryLayer。
     *
     * <p>如果摘要有实质内容（任一字段非空），创建 {@link SummaryChecklist} 并直接存储，
     * 跳过 LLM 生成步骤（因已在提取管道中完成）。
     */
    private void routeSummaryToStore(String userId, String conversationId, ExtractionResult result) {
        if (result.summary() == null) {
            return;
        }
        try {
            ExtractedSummary summary = result.summary();
            // 检查是否有实质内容
            boolean hasContent = hasItems(summary.topics())
                    || hasItems(summary.decisions())
                    || hasItems(summary.actionItems())
                    || hasItems(summary.unresolvedQuestions());

            if (!hasContent) {
                log.debug("摘要无实质内容，跳过存储: userId={}, conversationId={}", userId, conversationId);
                return;
            }

            SummaryChecklist checklist = new SummaryChecklist(
                    conversationId,
                    Instant.now(),
                    summary.topics() != null ? summary.topics() : Collections.emptyList(),
                    summary.decisions() != null ? summary.decisions() : Collections.emptyList(),
                    summary.actionItems() != null ? summary.actionItems() : Collections.emptyList(),
                    summary.unresolvedQuestions() != null ? summary.unresolvedQuestions() : Collections.emptyList()
            );

            summaryLayer.store(userId, checklist);
            log.debug("摘要路由完成: userId={}, conversationId={}", userId, conversationId);
        } catch (Exception e) {
            log.error("摘要路由失败: userId={}, conversationId={}, error={}",
                    userId, conversationId, e.getMessage(), e);
        }
    }

    /**
     * 路由提取的经验案例到 ExperienceStoreLayer。
     *
     * <p>将每条 {@link ExtractedExperience} 转换为 {@link ExperienceDocument}，
     * 生成唯一 ID 并设置用户和智能体信息。
     */
    private void routeExperiencesToStore(String userId, String conversationId, String agentType, ExtractionResult result) {
        if (result.experiences() == null || result.experiences().isEmpty()) {
            return;
        }
        try {
            Instant now = Instant.now();
            for (ExtractedExperience experience : result.experiences()) {
                try {
                    if (experience.content() == null || experience.content().isBlank()) {
                        log.debug("跳过无效经验（content 为空）: userId={}", userId);
                        continue;
                    }
                    ExperienceDocument document = new ExperienceDocument(
                            UUID.randomUUID().toString(),
                            userId,
                            agentType,
                            experience.content(),
                            experience.outcome() != null ? experience.outcome() : "insight",
                            now,
                            Map.of("sourceConversationId", conversationId)
                    );
                    experienceStoreLayer.store(document);
                } catch (Exception e) {
                    log.error("路由单条经验失败: userId={}, error={}",
                            userId, e.getMessage(), e);
                }
            }
            log.debug("经验路由完成: userId={}, conversationId={}", userId, conversationId);
        } catch (Exception e) {
            log.error("经验路由整体失败: userId={}, conversationId={}, error={}",
                    userId, conversationId, e.getMessage(), e);
        }
    }

    /**
     * 检查字符串列表是否包含有效元素。
     */
    private boolean hasItems(List<String> list) {
        return list != null && !list.isEmpty();
    }

    /**
     * 将对话消息列表格式化为带角色前缀的纯文本。
     *
     * @param messages 消息列表
     * @return 格式化后的对话文本；无有效内容时返回空字符串
     */
    private String formatConversation(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            String content = message.getText();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            sb.append(roleName(message)).append("：").append(content).append("\n");
        }
        return sb.toString();
    }

    /**
     * 根据消息类型返回中文角色名称。
     */
    private String roleName(Message message) {
        if (message instanceof UserMessage) {
            return "用户";
        } else if (message instanceof AssistantMessage) {
            return "助手";
        } else if (message instanceof SystemMessage) {
            return "系统";
        }
        return "未知";
    }

    /**
     * 记录提取结果日志，便于调试和验证。
     */
    private void logExtractionResult(String userId, String conversationId, ExtractionResult result) {
        int factCount = result.facts() != null ? result.facts().size() : 0;
        int experienceCount = result.experiences() != null ? result.experiences().size() : 0;
        boolean hasSummary = result.summary() != null;

        log.info("Extraction completed: userId={}, conversationId={}, facts={}, hasSummary={}, experiences={}",
                userId, conversationId, factCount, hasSummary, experienceCount);

        if (factCount > 0) {
            result.facts().forEach(fact ->
                    log.debug("  Extracted fact: key={}, value={}, category={}",
                            fact.key(), fact.value(), fact.category()));
        }
        if (hasSummary && result.summary().topics() != null) {
            log.debug("  Summary topics: {}", result.summary().topics());
        }
        if (experienceCount > 0) {
            result.experiences().forEach(exp ->
                    log.debug("  Extracted experience: outcome={}, content={}",
                            exp.outcome(), exp.content()));
        }
    }

    /**
     * 获取最近一次提取结果（供 Task 7.3 路由逻辑使用，包级可见）。
     * 此方法在当前实现中主要用于测试验证。
     */
    ExtractionResult getLastResult() {
        // Task 7.3 将在路由逻辑中直接使用 processMessages 内部的 result
        return null;
    }

    // ======================== 结构化输出记录 ========================

    /**
     * LLM 单次提取的完整结果，包含三类信息。
     *
     * @param facts       提取到的结构化事实列表
     * @param summary     对话摘要清单
     * @param experiences 提取到的经验案例列表
     */
    public record ExtractionResult(
            List<ExtractedFact> facts,
            ExtractedSummary summary,
            List<ExtractedExperience> experiences
    ) {}

    /**
     * 从对话中提取的单条结构化事实。
     *
     * @param key      事实键名（如 "姓名"、"行业"、"预算"）
     * @param value    事实值
     * @param category 事实类别（identity | career | preferences | goals | constraints）
     */
    public record ExtractedFact(
            String key,
            String value,
            String category
    ) {}

    /**
     * 对话摘要清单，包含讨论主题、决定、行动项和未解决问题。
     *
     * @param topics              讨论的主题列表
     * @param decisions           达成的决定或结论
     * @param actionItems         需要后续执行的行动项
     * @param unresolvedQuestions 尚未解决的问题
     */
    public record ExtractedSummary(
            List<String> topics,
            List<String> decisions,
            List<String> actionItems,
            List<String> unresolvedQuestions
    ) {}

    /**
     * 从对话中提取的经验案例。
     *
     * @param content 经验的叙述描述
     * @param outcome 结果分类（success | failure | insight）
     */
    public record ExtractedExperience(
            String content,
            String outcome
    ) {}
}
