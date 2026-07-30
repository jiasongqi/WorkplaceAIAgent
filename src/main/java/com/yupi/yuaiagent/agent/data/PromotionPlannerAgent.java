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
 * 晋升路径规划数据员工（扩展数据员工，P2）。
 * <p>
 * 基于用户的对话历史或上传文档，借助 LLM 产出结构化的"晋升路径规划"交付物并放入共享货架。
 * 加工逻辑封装在 {@link #doProduce(ProductionContext)} 中，放货流程（producer / status=READY / scope）
 * 由父类 {@link DataEmployeeAgent#produce(ProductionContext)} 模板方法统一处理。
 * <ul>
 *     <li>支持两种 {@link AnalysisSource}：CONVERSATION（对话历史）与 UPLOADED_DOCUMENT（上传文档）</li>
 *     <li>输入为空或无法获取时返回描述性错误，不产出交付物（{@link ProductionResult#fail(String)}）</li>
 *     <li>产出 type 为 {@code PROMOTION_PLAN} 的规划，content 为合法结构化 JSON（阶段目标、能力差距、行动项等）</li>
 *     <li>即便 LLM 输出无法解析为结构化规划，也兜底保证 content 为合法 JSON</li>
 * </ul>
 *
 * @author jsq
 * @see DataEmployeeAgent
 */
@Slf4j
public class PromotionPlannerAgent extends DataEmployeeAgent {

    /**
     * 数据员工标识名
     */
    private static final String PRODUCER = "晋升规划师";

    /**
     * 交付物类型
     */
    private static final String ARTIFACT_TYPE = "PROMOTION_PLAN";

    /**
     * 交付物标题
     */
    private static final String ARTIFACT_TITLE = "晋升路径规划";

    /**
     * 默认对话记忆所属的 agent 类型（context 未指定时使用）
     */
    private static final String DEFAULT_MEMORY_AGENT_TYPE = "general";

    /**
     * 晋升规划师系统提示词：明确角色与输出维度，结构化输出格式由 Spring AI {@code .entity(...)} 自动附加。
     */
    private static final String PLANNING_SYSTEM_PROMPT = """
            你是一名资深的职业发展与晋升规划顾问。请基于用户提供的输入数据，为其制定一份清晰、可执行的晋升路径规划，包含：
            1. summary：对用户当前处境与晋升目标的整体研判（必填，一段简洁的概括性文字）；
            2. targetRole：建议聚焦的晋升目标岗位/职级（如可从输入推断，否则给出合理建议）；
            3. stageGoals：分阶段的晋升目标列表（必填，按由近及远排序，每条为一个阶段性目标）；
            4. capabilityGaps：当前能力与目标岗位之间的能力差距列表（必填，每条为一项具体差距）；
            5. actionItems：为弥补差距、达成阶段目标而需要执行的具体行动项列表（必填，每条为一条可落地的行动）。
            请仅依据输入数据进行规划，不要编造数据中不存在的信息；信息不足时给出稳健、通用的职业发展建议。
            """;

    /**
     * 晋升规划师用户提示词模板，{input} 占位符将被替换为实际输入数据。
     */
    private static final String PLANNING_USER_PROMPT = """
            请基于以下输入数据，为用户产出一份结构化的晋升路径规划：

            ===== 输入数据开始 =====
            {input}
            ===== 输入数据结束 =====
            """;

    /**
     * 解析失败时的兜底合法规划 JSON，保证 content 始终为合法 JSON。
     */
    private static final String FALLBACK_PLAN_JSON =
            "{\"summary\":\"晋升路径规划生成失败，未能解析出结构化结果\",\"targetRole\":null,"
                    + "\"stageGoals\":[],\"capabilityGaps\":[],\"actionItems\":[]}";

    private final ChatClient chatClient;
    private final ChatMemoryManager chatMemoryManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PromotionPlannerAgent(ChatModel dashscopeChatModel, ChatMemoryManager chatMemoryManager,
                                 ArtifactShelf artifactShelf) {
        super(artifactShelf);
        this.chatMemoryManager = chatMemoryManager;
        this.chatClient = buildChatClient(dashscopeChatModel);
    }

    public PromotionPlannerAgent(ChatModel dashscopeChatModel, ChatMemoryManager chatMemoryManager,
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
            return ProductionResult.fail("晋升规划上下文为空，无法规划");
        }
        // 1. 按来源解析输入（对话历史 / 上传文档）
        String input = resolveInput(context);
        // 2. 输入为空或无法获取：返回描述性错误且不产出交付物
        if (!StringUtils.hasText(input)) {
            return ProductionResult.fail("规划输入为空或无法获取，无法生成晋升路径规划");
        }
        // 3. 调用规划提示词产出结构化规划，并兜底为合法 JSON
        String planJson = planToJson(input);
        // 4. 组装交付物（producer / status=READY / scope=TASK 由父类模板方法统一设置）
        Artifact artifact = Artifact.builder()
                .userId(context.userId())
                .chatId(context.chatId())
                .type(ARTIFACT_TYPE)
                .title(ARTIFACT_TITLE)
                .content(planJson)
                .scope(ArtifactScope.TASK)
                .build();
        return ProductionResult.ok(artifact);
    }

    /**
     * 按 {@link AnalysisSource} 解析规划输入。
     * <ul>
     *     <li>CONVERSATION：通过 {@link ChatMemoryManager} 读取 chatId 对应的对话历史并格式化为文本</li>
     *     <li>UPLOADED_DOCUMENT：直接读取上下文中的上传文档内容</li>
     * </ul>
     *
     * @return 规划输入文本；无法获取时返回 null 或空串
     */
    private String resolveInput(ProductionContext context) {
        AnalysisSource source = context.source();
        if (source == null) {
            log.warn("晋升规划来源为空，无法解析输入");
            return null;
        }
        return switch (source) {
            case CONVERSATION -> resolveConversationInput(context);
            case UPLOADED_DOCUMENT -> context.documentContent();
        };
    }

    /**
     * 读取指定 chatId 的对话历史作为规划输入。
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
     * 调用 LLM 产出结构化晋升规划，并序列化为合法 JSON 字符串。
     * <p>
     * 使用 Spring AI 的 {@code .entity(PromotionPlan.class)} 直接获得结构化对象，
     * 再序列化为 JSON 承载到 {@code Artifact.content}。当 LLM 调用或解析失败时，
     * 回退到兜底合法 JSON，确保 content 始终为合法 JSON。
     */
    private String planToJson(String input) {
        PromotionPlan plan;
        try {
            plan = chatClient.prompt()
                    .system(PLANNING_SYSTEM_PROMPT)
                    .user(PLANNING_USER_PROMPT.replace("{input}", input))
                    .call()
                    .entity(PromotionPlan.class);
        } catch (Exception e) {
            log.error("晋升规划 LLM 调用或结构化解析失败，回退兜底规划", e);
            return FALLBACK_PLAN_JSON;
        }
        return toJson(normalize(plan));
    }

    /**
     * 归一化规划：保证 summary 非空，列表字段不为 null。
     */
    private PromotionPlan normalize(PromotionPlan plan) {
        if (plan == null) {
            return PromotionPlan.builder()
                    .summary("未能从输入数据中解析出有效晋升规划")
                    .stageGoals(new ArrayList<>())
                    .capabilityGaps(new ArrayList<>())
                    .actionItems(new ArrayList<>())
                    .build();
        }
        if (!StringUtils.hasText(plan.getSummary())) {
            plan.setSummary("未能从输入数据中提炼出晋升规划摘要");
        }
        if (plan.getStageGoals() == null) {
            plan.setStageGoals(new ArrayList<>());
        }
        if (plan.getCapabilityGaps() == null) {
            plan.setCapabilityGaps(new ArrayList<>());
        }
        if (plan.getActionItems() == null) {
            plan.setActionItems(new ArrayList<>());
        }
        return plan;
    }

    /**
     * 将规划序列化为 JSON；序列化异常时回退兜底合法 JSON。
     */
    private String toJson(PromotionPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception e) {
            log.error("晋升路径规划序列化为 JSON 失败，回退兜底规划", e);
            return FALLBACK_PLAN_JSON;
        }
    }

    /**
     * 晋升路径规划结构化内容
     * <p>
     * 晋升规划师产出的结构化规划，序列化为 JSON 后作为 {@code Artifact.content} 承载。
     * 作为内嵌结构定义在本类中，避免新增独立的文件。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionPlan {

        /**
         * 整体研判摘要
         */
        private String summary;

        /**
         * 建议聚焦的晋升目标岗位/职级（可选）
         */
        private String targetRole;

        /**
         * 分阶段晋升目标
         */
        private List<String> stageGoals;

        /**
         * 当前能力与目标岗位之间的能力差距
         */
        private List<String> capabilityGaps;

        /**
         * 为达成目标需执行的具体行动项
         */
        private List<String> actionItems;
    }
}
