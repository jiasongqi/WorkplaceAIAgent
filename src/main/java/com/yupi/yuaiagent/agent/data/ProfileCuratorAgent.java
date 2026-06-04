package com.yupi.yuaiagent.agent.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactScope;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
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
import java.util.List;

/**
 * 用户画像整理员 Agent（P2 扩展数据员工）。
 * <p>
 * 将分散在对话历史中的画像线索（沟通偏好、关注领域、已知背景、历史诉求等）整理为一份
 * 结构化的用户画像交付物，并放入共享货架。加工逻辑封装在 {@link #doProduce(ProductionContext)} 中，
 * 放货流程（producer / status=READY）由父类 {@link DataEmployeeAgent#produce(ProductionContext)} 模板方法统一处理。
 * <ul>
 *     <li>从对话历史读取分散的画像线索作为整理输入（Req 15.2）</li>
 *     <li>输入为空或无法获取时返回描述性错误，不产出交付物（Req 8.6）</li>
 *     <li>产出 type 为 {@code USER_PROFILE_SUMMARY} 的画像摘要，content 为合法的结构化 JSON</li>
 *     <li>作为跨会话画像交付物，主动将 scope 设为 {@link ArtifactScope#USER_PROFILE}（Req 15.5），
 *         父类模板方法仅在 scope 为 null 时才默认设为 TASK，故此处设置会被保留</li>
 *     <li>因 scope=USER_PROFILE，父类放货时校验要求 userId 必须存在，故 artifact 的 userId 取自
 *         {@link ProductionContext#userId()}（Req 15.5）</li>
 *     <li>producer={@link #PRODUCER} 与 status=READY 由父类统一设置（Req 15.4 / 8.5）</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
public class ProfileCuratorAgent extends DataEmployeeAgent {

    /**
     * 数据员工标识名
     */
    private static final String PRODUCER = "画像整理员";

    /**
     * 交付物类型
     */
    private static final String ARTIFACT_TYPE = "USER_PROFILE_SUMMARY";

    /**
     * 交付物标题
     */
    private static final String ARTIFACT_TITLE = "用户画像摘要";

    /**
     * 默认对话记忆所属的 agent 类型（context 未指定时使用）
     */
    private static final String DEFAULT_MEMORY_AGENT_TYPE = "general";

    /**
     * 画像整理系统提示词：明确角色与整理维度，结构化输出格式由 Spring AI {@code .entity(...)} 自动附加。
     */
    private static final String CURATE_SYSTEM_PROMPT = """
            你是一名用户画像整理专家。请基于提供的对话历史，将分散的画像线索整理为一份结构化的用户画像摘要，包含：
            1. summary：对该用户画像的整体概述（必填，一段简洁的概括性文字）；
            2. communicationPreferences：沟通与语气偏好列表（如 偏好简洁、偏好正式语气 等），无依据时可留空；
            3. focusAreas：用户关注的领域 / 话题列表（如 薪资谈判、职业转型 等），无依据时可留空；
            4. knownBackground：已知的用户背景信息列表（如 工作年限、所在行业 等），无依据时可留空；
            5. historicalNeeds：用户历史诉求列表（如 想加薪、想跳槽 等），无依据时可留空。
            请仅依据对话中有明确依据的信息进行整理，没有依据的维度请留空，不要编造对话中不存在的信息。
            """;

    /**
     * 画像整理用户提示词模板，{input} 占位符将被替换为实际对话历史文本。
     */
    private static final String CURATE_USER_PROMPT = """
            请基于以下对话历史整理出结构化的用户画像摘要：

            ===== 对话历史开始 =====
            {input}
            ===== 对话历史结束 =====
            """;

    /**
     * 解析失败时的兜底合法画像摘要 JSON，保证 content 始终为合法 JSON。
     */
    private static final String FALLBACK_SUMMARY_JSON =
            "{\"summary\":\"画像整理失败，未能解析出结构化结果\",\"communicationPreferences\":[],"
                    + "\"focusAreas\":[],\"knownBackground\":[],\"historicalNeeds\":[]}";

    private final ChatClient chatClient;
    private final ChatMemoryManager chatMemoryManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProfileCuratorAgent(ChatModel dashscopeChatModel, ChatMemoryManager chatMemoryManager,
                               ArtifactShelf artifactShelf) {
        super(artifactShelf);
        this.chatMemoryManager = chatMemoryManager;
        this.chatClient = ChatClient.builder(dashscopeChatModel)
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
            return ProductionResult.fail("画像整理上下文为空，无法整理");
        }
        // 1. 从对话历史读取分散的画像线索（Req 15.2）
        String input = resolveConversationInput(context);
        // 2. 输入为空或无法获取：返回描述性错误且不产出交付物（Req 8.6）
        if (!StringUtils.hasText(input)) {
            return ProductionResult.fail("画像线索为空或无法获取，无法生成用户画像摘要");
        }
        // 3. 调用整理提示词产出结构化画像摘要，并兜底为合法 JSON
        String summaryJson = curateToJson(input);
        // 4. 组装交付物：主动设 scope=USER_PROFILE 且 userId 取自 context（Req 15.5）；
        //    producer / status=READY 由父类模板方法统一设置
        Artifact artifact = Artifact.builder()
                .userId(context.userId())
                .chatId(context.chatId())
                .type(ARTIFACT_TYPE)
                .title(ARTIFACT_TITLE)
                .content(summaryJson)
                .scope(ArtifactScope.USER_PROFILE)
                .build();
        return ProductionResult.ok(artifact);
    }

    /**
     * 读取指定 chatId 的对话历史作为画像整理输入（Req 15.2）。
     *
     * @return 对话历史文本；无法获取时返回 null 或空串
     */
    private String resolveConversationInput(ProductionContext context) {
        String chatId = context.chatId();
        if (!StringUtils.hasText(chatId)) {
            log.warn("画像整理缺少 chatId，无法读取对话历史");
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
     * 调用 LLM 整理出结构化画像摘要，并序列化为合法 JSON 字符串。
     * <p>
     * 使用 Spring AI 的 {@code .entity(ProfileSummary.class)} 直接获得结构化对象，
     * 再序列化为 JSON 承载到 {@code Artifact.content}。当 LLM 调用或解析失败时，
     * 回退到兜底合法 JSON，确保 content 始终为合法 JSON。
     */
    private String curateToJson(String input) {
        ProfileSummary summary;
        try {
            summary = chatClient.prompt()
                    .system(CURATE_SYSTEM_PROMPT)
                    .user(CURATE_USER_PROMPT.replace("{input}", input))
                    .call()
                    .entity(ProfileSummary.class);
        } catch (Exception e) {
            log.error("画像整理 LLM 调用或结构化解析失败，回退兜底摘要", e);
            return FALLBACK_SUMMARY_JSON;
        }
        return toJson(normalize(summary));
    }

    /**
     * 归一化画像摘要：保证 summary 字段非空，各列表字段不为 null。
     */
    private ProfileSummary normalize(ProfileSummary summary) {
        if (summary == null) {
            return ProfileSummary.builder()
                    .summary("未能从对话历史中整理出有效画像信息")
                    .communicationPreferences(new ArrayList<>())
                    .focusAreas(new ArrayList<>())
                    .knownBackground(new ArrayList<>())
                    .historicalNeeds(new ArrayList<>())
                    .build();
        }
        if (!StringUtils.hasText(summary.getSummary())) {
            summary.setSummary("未能从对话历史中提炼出画像概述");
        }
        if (summary.getCommunicationPreferences() == null) {
            summary.setCommunicationPreferences(new ArrayList<>());
        }
        if (summary.getFocusAreas() == null) {
            summary.setFocusAreas(new ArrayList<>());
        }
        if (summary.getKnownBackground() == null) {
            summary.setKnownBackground(new ArrayList<>());
        }
        if (summary.getHistoricalNeeds() == null) {
            summary.setHistoricalNeeds(new ArrayList<>());
        }
        return summary;
    }

    /**
     * 将画像摘要序列化为 JSON；序列化异常时回退兜底合法 JSON。
     */
    private String toJson(ProfileSummary summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            log.error("用户画像摘要序列化为 JSON 失败，回退兜底摘要", e);
            return FALLBACK_SUMMARY_JSON;
        }
    }

    /**
     * 用户画像摘要结构化内容
     * <p>
     * 画像整理员产出的结构化摘要，序列化为 JSON 后作为 {@code Artifact.content} 承载。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileSummary {

        /**
         * 画像整体概述
         */
        private String summary;

        /**
         * 沟通与语气偏好
         */
        private List<String> communicationPreferences;

        /**
         * 关注领域 / 话题
         */
        private List<String> focusAreas;

        /**
         * 已知背景信息
         */
        private List<String> knownBackground;

        /**
         * 历史诉求
         */
        private List<String> historicalNeeds;
    }
}
