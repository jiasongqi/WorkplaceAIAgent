package com.yupi.yuaiagent.agent.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.artifact.ArtifactPublisher;
import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactScope;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.profile.model.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 学习资源推荐员 Agent（P3 数据员工）。
 * <p>
 * 根据用户画像中的关注领域推荐学习资源，产出"学习资源推荐"交付物并放入共享货架。
 * 加工逻辑封装在 {@link #doProduce(ProductionContext)} 中，放货流程（producer / status=READY / scope）
 * 由父类 {@link DataEmployeeAgent#produce(ProductionContext)} 模板方法统一处理。
 * <ul>
 *     <li>优先读取 userId 的 {@link UserProfile} 关注领域（focusAreas）作为推荐依据（Req 16.2）</li>
 *     <li>画像存在且关注领域非空时，基于关注领域调用 LLM 产出学习资源推荐（Req 16.2/16.3）</li>
 *     <li>画像不存在或关注领域为空时，回退到基于本次对话上下文（chatId 对话历史）生成推荐（Req 16.4）</li>
 *     <li>基于画像的尝试以 try-catch 包裹，失败时优雅回退到对话上下文推荐（Req 16.5）</li>
 *     <li>产出 type 为 {@code LEARNING_RESOURCE_RECOMMENDATION} 的推荐，content 为合法的结构化 JSON（Req 16.3）</li>
 *     <li>即便 LLM 输出无法解析为结构化推荐，也兜底保证 content 为合法 JSON</li>
 * </ul>
 * 与 {@link DataAnalystAgent} / {@link CareerCoachAgent} 同款实现范式（ChatClient 构建、resolveConversationInput、JSON 兜底）。
 *
 * @author jsq
 */
@Slf4j
public class LearningResourceRecommenderAgent extends DataEmployeeAgent {

    /**
     * 数据员工标识名
     */
    private static final String PRODUCER = "学习资源推荐员";

    /**
     * 交付物类型
     */
    private static final String ARTIFACT_TYPE = "LEARNING_RESOURCE_RECOMMENDATION";

    /**
     * 交付物标题
     */
    private static final String ARTIFACT_TITLE = "学习资源推荐";

    /**
     * 默认对话记忆所属的 agent 类型（context 未指定时使用）
     */
    private static final String DEFAULT_MEMORY_AGENT_TYPE = "general";

    /**
     * 学习资源推荐系统提示词：明确角色与输出维度，结构化输出格式由 Spring AI {@code .entity(...)} 自动附加。
     */
    private static final String RECOMMEND_SYSTEM_PROMPT = """
            你是一名专业、务实的学习资源推荐员。请基于提供的推荐依据，为用户推荐有针对性的学习资源，包含：
            1. summary：对用户学习需求与本次推荐思路的整体说明（必填，一段简洁的概括性文字）；
            2. focusAreas：本次推荐所覆盖的关注领域 / 学习方向列表（必填，每条为一个领域名称）；
            3. recommendations：具体的学习资源推荐列表（必填），每条包含：
               - area：该资源对应的关注领域 / 学习方向；
               - title：资源名称（如 课程、书籍、文档、实践项目等的名称）；
               - resourceType：资源类型（如 在线课程、书籍、官方文档、博客、实践项目 等）；
               - reason：推荐该资源的理由（贴合用户需求，说明能解决什么问题）。
            请仅依据推荐依据进行推荐，不要编造与依据无关的信息；推荐应具体、可执行、贴合用户实际方向。
            """;

    /**
     * 基于画像关注领域的用户提示词模板，{input} 占位符将被替换为关注领域文本。
     */
    private static final String PROFILE_USER_PROMPT = """
            请基于以下用户关注领域推荐学习资源：

            ===== 用户关注领域开始 =====
            {input}
            ===== 用户关注领域结束 =====
            """;

    /**
     * 基于对话上下文的用户提示词模板，{input} 占位符将被替换为对话历史文本。
     */
    private static final String CONVERSATION_USER_PROMPT = """
            用户暂无可用的关注领域画像，请基于以下对话上下文推断其学习需求并推荐学习资源：

            ===== 对话上下文开始 =====
            {input}
            ===== 对话上下文结束 =====
            """;

    /**
     * 解析失败时的兜底合法推荐 JSON，保证 content 始终为合法 JSON。
     */
    private static final String FALLBACK_RECOMMENDATION_JSON =
            "{\"summary\":\"学习资源推荐生成失败，未能解析出结构化结果\",\"focusAreas\":[],\"recommendations\":[]}";

    private final ChatClient chatClient;
    private final ChatMemoryManager chatMemoryManager;
    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LearningResourceRecommenderAgent(ChatModel dashscopeChatModel,
                                            ChatMemoryManager chatMemoryManager,
                                            UserProfileService userProfileService,
                                            ArtifactShelf artifactShelf) {
        super(artifactShelf);
        this.chatMemoryManager = chatMemoryManager;
        this.userProfileService = userProfileService;
        this.chatClient = buildChatClient(dashscopeChatModel);
    }

    public LearningResourceRecommenderAgent(ChatModel dashscopeChatModel,
                                            ChatMemoryManager chatMemoryManager,
                                            UserProfileService userProfileService,
                                            ArtifactPublisher artifactPublisher) {
        super(artifactPublisher);
        this.chatMemoryManager = chatMemoryManager;
        this.userProfileService = userProfileService;
        this.chatClient = buildChatClient(dashscopeChatModel);
    }

    private ChatClient buildChatClient(ChatModel dashscopeChatModel) {
        return ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
    }

    @Override
    public String producerName() {
        return PRODUCER;
    }

    @Override
    protected ProductionResult doProduce(ProductionContext context) {
        if (context == null) {
            return ProductionResult.fail("学习资源推荐上下文为空，无法推荐");
        }
        String recommendationJson = null;
        // 1. 优先基于用户画像关注领域推荐（Req 16.2）；该尝试以 try-catch 包裹，
        //    任何失败都优雅回退到对话上下文推荐（Req 16.5）
        try {
            List<String> focusAreas = resolveFocusAreas(context.userId());
            if (!CollectionUtils.isEmpty(focusAreas)) {
                recommendationJson = recommendByFocusAreas(focusAreas);
            }
        } catch (Exception e) {
            log.error("基于用户画像关注领域推荐失败，回退到对话上下文推荐", e);
            recommendationJson = null;
        }
        // 2. 画像不存在 / 关注领域为空 / 基于画像尝试失败：回退到基于本次对话上下文推荐（Req 16.4/16.5）
        if (!StringUtils.hasText(recommendationJson)) {
            String conversation = resolveConversationInput(context);
            if (!StringUtils.hasText(conversation)) {
                return ProductionResult.fail("用户关注领域为空且无可用对话上下文，无法生成学习资源推荐");
            }
            recommendationJson = recommendByConversation(conversation);
        }
        // 3. 组装交付物（producer / status=READY / scope=TASK 由父类模板方法统一设置）
        Artifact artifact = Artifact.builder()
                .userId(context.userId())
                .chatId(context.chatId())
                .type(ARTIFACT_TYPE)
                .title(ARTIFACT_TITLE)
                .content(recommendationJson)
                .scope(ArtifactScope.TASK)
                .build();
        return ProductionResult.ok(artifact);
    }

    /**
     * 读取指定 userId 画像中的关注领域作为推荐依据（Req 16.2）。
     *
     * @return 关注领域列表；画像不存在或 userId 为空时返回空列表
     */
    private List<String> resolveFocusAreas(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Collections.emptyList();
        }
        return userProfileService.get(userId)
                .map(UserProfile::getFocusAreas)
                .orElseGet(Collections::emptyList);
    }

    /**
     * 基于画像关注领域调用 LLM 产出结构化学习资源推荐 JSON（Req 16.2）。
     */
    private String recommendByFocusAreas(List<String> focusAreas) {
        String input = String.join("、", focusAreas);
        return recommendToJson(PROFILE_USER_PROMPT.replace("{input}", input));
    }

    /**
     * 基于本次对话上下文调用 LLM 产出结构化学习资源推荐 JSON（Req 16.4）。
     */
    private String recommendByConversation(String conversation) {
        return recommendToJson(CONVERSATION_USER_PROMPT.replace("{input}", conversation));
    }

    /**
     * 读取指定 chatId 的对话历史作为对话上下文推荐输入（Req 16.4）。
     *
     * @return 对话历史文本；无法获取时返回 null 或空串
     */
    private String resolveConversationInput(ProductionContext context) {
        String chatId = context.chatId();
        if (!StringUtils.hasText(chatId)) {
            log.warn("学习资源推荐缺少 chatId，无法读取对话历史作为回退依据");
            return null;
        }
        String agentType = StringUtils.hasText(context.memoryAgentType())
                ? context.memoryAgentType()
                : DEFAULT_MEMORY_AGENT_TYPE;
        ChatMemory memory = chatMemoryManager.getMemory(agentType);
        List<Message> messages = memory.get(chatId);
        return formatMessages(messages);
    }

    /**
     * 将对话消息列表格式化为带角色前缀的纯文本。
     *
     * @return 格式化后的对话文本；无有效内容时返回空字符串
     */
    private String formatMessages(List<Message> messages) {
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
     * 调用 LLM 产出结构化学习资源推荐，并序列化为合法 JSON 字符串。
     * <p>
     * 使用 Spring AI 的 {@code .entity(LearningRecommendation.class)} 直接获得结构化对象，
     * 再序列化为 JSON 承载到 {@code Artifact.content}。当 LLM 调用或解析失败时，
     * 回退到兜底合法 JSON，确保 content 始终为合法 JSON。
     */
    private String recommendToJson(String userPrompt) {
        LearningRecommendation recommendation;
        try {
            recommendation = chatClient.prompt()
                    .system(RECOMMEND_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .entity(LearningRecommendation.class);
        } catch (Exception e) {
            log.error("学习资源推荐 LLM 调用或结构化解析失败，回退兜底推荐", e);
            return FALLBACK_RECOMMENDATION_JSON;
        }
        return toJson(normalize(recommendation));
    }

    /**
     * 归一化推荐：保证 summary 字段非空，列表字段不为 null。
     */
    private LearningRecommendation normalize(LearningRecommendation recommendation) {
        if (recommendation == null) {
            return LearningRecommendation.builder()
                    .summary("未能从推荐依据中生成有效的学习资源推荐")
                    .focusAreas(new ArrayList<>())
                    .recommendations(new ArrayList<>())
                    .build();
        }
        if (!StringUtils.hasText(recommendation.getSummary())) {
            recommendation.setSummary("未能从推荐依据中提炼出学习需求说明");
        }
        if (recommendation.getFocusAreas() == null) {
            recommendation.setFocusAreas(new ArrayList<>());
        }
        if (recommendation.getRecommendations() == null) {
            recommendation.setRecommendations(new ArrayList<>());
        }
        return recommendation;
    }

    /**
     * 将推荐序列化为 JSON；序列化异常时回退兜底合法 JSON。
     */
    private String toJson(LearningRecommendation recommendation) {
        try {
            return objectMapper.writeValueAsString(recommendation);
        } catch (Exception e) {
            log.error("学习资源推荐序列化为 JSON 失败，回退兜底推荐", e);
            return FALLBACK_RECOMMENDATION_JSON;
        }
    }

    /**
     * 学习资源推荐结构化内容。
     * <p>
     * 学习资源推荐员产出的结构化推荐，序列化为 JSON 后作为 {@code Artifact.content} 承载。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningRecommendation {

        /**
         * 学习需求与推荐思路整体说明
         */
        private String summary;

        /**
         * 本次推荐覆盖的关注领域 / 学习方向
         */
        private List<String> focusAreas;

        /**
         * 具体学习资源推荐列表
         */
        private List<ResourceItem> recommendations;
    }

    /**
     * 单条学习资源推荐项。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceItem {

        /**
         * 对应的关注领域 / 学习方向
         */
        private String area;

        /**
         * 资源名称
         */
        private String title;

        /**
         * 资源类型（如 在线课程、书籍、官方文档 等）
         */
        private String resourceType;

        /**
         * 推荐理由
         */
        private String reason;
    }
}
