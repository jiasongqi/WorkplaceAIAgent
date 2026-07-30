package com.yupi.yuaiagent.agent.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.artifact.ArtifactPublisher;
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
 * 岗位辅导数据员工 Agent（P2 扩展数据员工之一）。
 * <p>
 * 基于用户的对话历史或上传文档，调用 LLM 产出"岗位辅导建议"交付物并放入共享货架。
 * 加工逻辑封装在 {@link #doProduce(ProductionContext)} 中，放货流程（producer / status=READY / scope）
 * 由父类 {@link DataEmployeeAgent#produce(ProductionContext)} 模板方法统一处理。
 * <ul>
 *     <li>支持两种 {@link AnalysisSource}：CONVERSATION（对话历史）与 UPLOADED_DOCUMENT（上传文档）</li>
 *     <li>输入为空或无法获取时返回描述性错误，不产出交付物</li>
 *     <li>产出 type 为 {@code CAREER_COACH_ADVICE} 的辅导建议，content 为合法的结构化 JSON</li>
 *     <li>即便 LLM 输出无法解析为结构化建议，也兜底保证 content 为合法 JSON</li>
 * </ul>
 * 与 {@link DataAnalystAgent} 同款实现范式（ChatClient 构建、resolveInput、JSON 兜底）。
 *
 * @author jsq
 */
@Slf4j
public class CareerCoachAgent extends DataEmployeeAgent {

    /**
     * 数据员工标识名
     */
    private static final String PRODUCER = "岗位辅导员";

    /**
     * 交付物类型
     */
    private static final String ARTIFACT_TYPE = "CAREER_COACH_ADVICE";

    /**
     * 交付物标题
     */
    private static final String ARTIFACT_TITLE = "岗位辅导建议";

    /**
     * 默认对话记忆所属的 agent 类型（context 未指定时使用）
     */
    private static final String DEFAULT_MEMORY_AGENT_TYPE = "general";

    /**
     * 岗位辅导系统提示词：明确角色与输出维度，结构化输出格式由 Spring AI {@code .entity(...)} 自动附加。
     */
    private static final String COACH_SYSTEM_PROMPT = """
            你是一名经验丰富、务实的职业岗位辅导员。请基于用户提供的输入内容，给出有针对性的岗位辅导建议，包含：
            1. summary：对用户当前职业状况与诉求的整体研判摘要（必填，一段简洁的概括性文字）；
            2. suggestions：针对性的岗位辅导建议列表（必填，每条为一句可理解、可落地的建议）；
            3. actionItems：建议用户接下来执行的具体行动项列表，无明确行动项时可留空；
            4. skillRecommendations：建议用户补强或提升的关键技能列表，无明确技能建议时可留空。
            请仅依据输入内容进行辅导，不要编造输入中不存在的信息，建议应具体、可执行、贴合用户实际情况。
            """;

    /**
     * 岗位辅导用户提示词模板，{input} 占位符将被替换为实际输入内容。
     */
    private static final String COACH_USER_PROMPT = """
            请基于以下输入内容产出结构化岗位辅导建议：

            ===== 输入内容开始 =====
            {input}
            ===== 输入内容结束 =====
            """;

    /**
     * 解析失败时的兜底合法建议 JSON，保证 content 始终为合法 JSON。
     */
    private static final String FALLBACK_ADVICE_JSON =
            "{\"summary\":\"岗位辅导建议生成失败，未能解析出结构化结果\",\"suggestions\":[],\"actionItems\":[],\"skillRecommendations\":[]}";

    private final ChatClient chatClient;
    private final ChatMemoryManager chatMemoryManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CareerCoachAgent(ChatModel dashscopeChatModel, ChatMemoryManager chatMemoryManager,
                            ArtifactShelf artifactShelf) {
        super(artifactShelf);
        this.chatMemoryManager = chatMemoryManager;
        this.chatClient = buildChatClient(dashscopeChatModel);
    }

    public CareerCoachAgent(ChatModel dashscopeChatModel, ChatMemoryManager chatMemoryManager,
                            ArtifactPublisher artifactPublisher) {
        super(artifactPublisher);
        this.chatMemoryManager = chatMemoryManager;
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
            return ProductionResult.fail("岗位辅导上下文为空，无法生成辅导建议");
        }
        // 1. 按来源解析辅导输入
        String input = resolveInput(context);
        // 2. 输入为空或无法获取：返回描述性错误且不产出交付物
        if (!StringUtils.hasText(input)) {
            return ProductionResult.fail("辅导输入为空或无法获取，无法生成岗位辅导建议");
        }
        // 3. 调用辅导提示词产出结构化建议，并兜底为合法 JSON
        String adviceJson = coachToJson(input);
        // 4. 组装交付物（producer / status=READY / scope 由父类模板方法统一设置）
        Artifact artifact = Artifact.builder()
                .userId(context.userId())
                .chatId(context.chatId())
                .type(ARTIFACT_TYPE)
                .title(ARTIFACT_TITLE)
                .content(adviceJson)
                .scope(ArtifactScope.TASK)
                .build();
        return ProductionResult.ok(artifact);
    }

    /**
     * 按 {@link AnalysisSource} 解析辅导输入。
     * <ul>
     *     <li>CONVERSATION：通过 {@link ChatMemoryManager} 读取 chatId 对应的对话历史并格式化为文本</li>
     *     <li>UPLOADED_DOCUMENT：直接读取上下文中的上传文档内容</li>
     * </ul>
     *
     * @return 辅导输入文本；无法获取时返回 null 或空串
     */
    private String resolveInput(ProductionContext context) {
        AnalysisSource source = context.source();
        if (source == null) {
            log.warn("岗位辅导来源为空，无法解析输入");
            return null;
        }
        return switch (source) {
            case CONVERSATION -> resolveConversationInput(context);
            case UPLOADED_DOCUMENT -> context.documentContent();
        };
    }

    /**
     * 读取指定 chatId 的对话历史作为辅导输入。
     */
    private String resolveConversationInput(ProductionContext context) {
        String chatId = context.chatId();
        if (!StringUtils.hasText(chatId)) {
            log.warn("CONVERSATION 来源缺少 chatId，无法读取对话历史");
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
     * 调用 LLM 产出结构化岗位辅导建议，并序列化为合法 JSON 字符串。
     * <p>
     * 使用 Spring AI 的 {@code .entity(CareerCoachAdvice.class)} 直接获得结构化对象，
     * 再序列化为 JSON 承载到 {@code Artifact.content}。当 LLM 调用或解析失败时，
     * 回退到兜底合法 JSON，确保 content 始终为合法 JSON。
     */
    private String coachToJson(String input) {
        CareerCoachAdvice advice;
        try {
            advice = chatClient.prompt()
                    .system(COACH_SYSTEM_PROMPT)
                    .user(COACH_USER_PROMPT.replace("{input}", input))
                    .call()
                    .entity(CareerCoachAdvice.class);
        } catch (Exception e) {
            log.error("岗位辅导 LLM 调用或结构化解析失败，回退兜底建议", e);
            return FALLBACK_ADVICE_JSON;
        }
        return toJson(normalize(advice));
    }

    /**
     * 归一化建议：保证 summary 与 suggestions 字段非空，列表字段不为 null。
     */
    private CareerCoachAdvice normalize(CareerCoachAdvice advice) {
        if (advice == null) {
            return CareerCoachAdvice.builder()
                    .summary("未能从输入内容中解析出有效的岗位辅导建议")
                    .suggestions(new ArrayList<>())
                    .actionItems(new ArrayList<>())
                    .skillRecommendations(new ArrayList<>())
                    .build();
        }
        if (!StringUtils.hasText(advice.getSummary())) {
            advice.setSummary("未能从输入内容中提炼出辅导研判摘要");
        }
        if (advice.getSuggestions() == null) {
            advice.setSuggestions(new ArrayList<>());
        }
        if (advice.getActionItems() == null) {
            advice.setActionItems(new ArrayList<>());
        }
        if (advice.getSkillRecommendations() == null) {
            advice.setSkillRecommendations(new ArrayList<>());
        }
        return advice;
    }

    /**
     * 将建议序列化为 JSON；序列化异常时回退兜底合法 JSON。
     */
    private String toJson(CareerCoachAdvice advice) {
        try {
            return objectMapper.writeValueAsString(advice);
        } catch (Exception e) {
            log.error("岗位辅导建议序列化为 JSON 失败，回退兜底建议", e);
            return FALLBACK_ADVICE_JSON;
        }
    }

    /**
     * 岗位辅导建议结构化内容。
     * <p>
     * 岗位辅导员产出的结构化建议，序列化为 JSON 后作为 {@code Artifact.content} 承载。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CareerCoachAdvice {

        /**
         * 整体研判摘要
         */
        private String summary;

        /**
         * 针对性岗位辅导建议
         */
        private List<String> suggestions;

        /**
         * 建议执行的具体行动项（可选）
         */
        private List<String> actionItems;

        /**
         * 建议补强或提升的关键技能（可选）
         */
        private List<String> skillRecommendations;
    }
}
