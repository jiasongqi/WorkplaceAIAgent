package com.yupi.yuaiagent.agent.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactScope;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
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
 * 数据分析师 Agent（第一期落地的数据员工）。
 * <p>
 * 分析用户的对话历史或上传文档，产出结构化 JSON 数据分析报告交付物并放入共享货架。
 * 加工逻辑封装在 {@link #doProduce(ProductionContext)} 中，放货流程（producer / status=READY / scope）
 * 由父类 {@link DataEmployeeAgent#produce(ProductionContext)} 模板方法统一处理。
 * <ul>
 *     <li>支持两种 {@link AnalysisSource}：CONVERSATION（对话历史）与 UPLOADED_DOCUMENT（上传文档）（Req 8.1/8.2/8.3）</li>
 *     <li>输入为空或无法获取时返回描述性错误，不产出交付物（Req 8.6）</li>
 *     <li>产出 type 为 {@code DATA_ANALYSIS_REPORT} 的报告，content 为合法的结构化 JSON（Req 8.4/8.7）</li>
 *     <li>即便 LLM 输出无法解析为结构化报告，也兜底保证 content 为合法 JSON（Req 8.7）</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
public class DataAnalystAgent extends DataEmployeeAgent {

    /**
     * 数据员工标识名
     */
    private static final String PRODUCER = "数据分析师";

    /**
     * 交付物类型
     */
    private static final String ARTIFACT_TYPE = "DATA_ANALYSIS_REPORT";

    /**
     * 交付物标题
     */
    private static final String ARTIFACT_TITLE = "数据分析报告";

    /**
     * 默认对话记忆所属的 agent 类型（context 未指定时使用）
     */
    private static final String DEFAULT_MEMORY_AGENT_TYPE = "general";

    /**
     * 分析师系统提示词：明确角色与输出维度，结构化输出格式由 Spring AI {@code .entity(...)} 自动附加。
     */
    private static final String ANALYSIS_SYSTEM_PROMPT = """
            你是一名严谨、客观的数据分析师。请基于用户提供的输入数据进行分析，并产出一份结构化分析报告，包含：
            1. summary：对输入数据的整体分析摘要（必填，一段简洁的概括性文字）；
            2. keyFindings：从数据中提炼出的关键发现列表（必填，每条为一句独立的发现）；
            3. metrics：可量化的关键指标键值对（如 对话轮数、主要话题 等），无法量化时可留空；
            4. recommendations：基于分析给出的可执行建议列表，无建议时可留空。
            请仅依据输入数据进行分析，不要编造数据中不存在的信息。
            """;

    /**
     * 分析师用户提示词模板，{input} 占位符将被替换为实际输入数据。
     */
    private static final String ANALYSIS_USER_PROMPT = """
            请分析以下输入数据并产出结构化分析报告：

            ===== 输入数据开始 =====
            {input}
            ===== 输入数据结束 =====
            """;

    /**
     * 解析失败时的兜底合法报告 JSON，保证 content 始终为合法 JSON（Req 8.7）。
     */
    private static final String FALLBACK_REPORT_JSON =
            "{\"summary\":\"分析报告生成失败，未能解析出结构化结果\",\"keyFindings\":[],\"metrics\":{},\"recommendations\":[]}";

    private final ChatClient chatClient;
    private final ChatMemoryManager chatMemoryManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataAnalystAgent(ChatModel dashscopeChatModel, ChatMemoryManager chatMemoryManager,
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
            return ProductionResult.fail("数据分析上下文为空，无法分析");
        }
        // 1. 按来源解析分析输入（Req 8.2 / 8.3）
        String input = resolveInput(context);
        // 2. 输入为空或无法获取：返回描述性错误且不产出交付物（Req 8.6）
        if (!StringUtils.hasText(input)) {
            return ProductionResult.fail("分析输入为空或无法获取，无法生成数据分析报告");
        }
        // 3. 调用分析提示词产出结构化报告，并兜底为合法 JSON（Req 8.4 / 8.7）
        String reportJson = analyzeToJson(input);
        // 4. 组装交付物（producer / status=READY 由父类模板方法统一设置）
        Artifact artifact = Artifact.builder()
                .userId(context.userId())
                .chatId(context.chatId())
                .type(ARTIFACT_TYPE)
                .title(ARTIFACT_TITLE)
                .content(reportJson)
                .scope(ArtifactScope.TASK)
                .build();
        return ProductionResult.ok(artifact);
    }

    /**
     * 按 {@link AnalysisSource} 解析分析输入。
     * <ul>
     *     <li>CONVERSATION：通过 {@link ChatMemoryManager} 读取 chatId 对应的对话历史并格式化为文本</li>
     *     <li>UPLOADED_DOCUMENT：直接读取上下文中的上传文档内容</li>
     * </ul>
     *
     * @return 分析输入文本；无法获取时返回 null 或空串
     */
    private String resolveInput(ProductionContext context) {
        AnalysisSource source = context.source();
        if (source == null) {
            log.warn("数据分析来源为空，无法解析输入");
            return null;
        }
        return switch (source) {
            case CONVERSATION -> resolveConversationInput(context);
            case UPLOADED_DOCUMENT -> context.documentContent();
        };
    }

    /**
     * 读取指定 chatId 的对话历史作为分析输入（Req 8.2）。
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
     * 调用 LLM 产出结构化分析报告，并序列化为合法 JSON 字符串。
     * <p>
     * 使用 Spring AI 的 {@code .entity(AnalysisReport.class)} 直接获得结构化对象，
     * 再序列化为 JSON 承载到 {@code Artifact.content}。当 LLM 调用或解析失败时，
     * 回退到兜底合法 JSON，确保 content 始终为合法 JSON（Req 8.7）。
     */
    private String analyzeToJson(String input) {
        AnalysisReport report;
        try {
            report = chatClient.prompt()
                    .system(ANALYSIS_SYSTEM_PROMPT)
                    .user(ANALYSIS_USER_PROMPT.replace("{input}", input))
                    .call()
                    .entity(AnalysisReport.class);
        } catch (Exception e) {
            log.error("数据分析 LLM 调用或结构化解析失败，回退兜底报告", e);
            return FALLBACK_REPORT_JSON;
        }
        return toJson(normalize(report));
    }

    /**
     * 归一化报告：保证 summary 与 keyFindings 字段非空（Req 8.7），列表字段不为 null。
     */
    private AnalysisReport normalize(AnalysisReport report) {
        if (report == null) {
            return AnalysisReport.builder()
                    .summary("未能从输入数据中解析出有效分析结果")
                    .keyFindings(new ArrayList<>())
                    .recommendations(new ArrayList<>())
                    .build();
        }
        if (!StringUtils.hasText(report.getSummary())) {
            report.setSummary("未能从输入数据中提炼出分析摘要");
        }
        if (report.getKeyFindings() == null) {
            report.setKeyFindings(new ArrayList<>());
        }
        if (report.getRecommendations() == null) {
            report.setRecommendations(new ArrayList<>());
        }
        return report;
    }

    /**
     * 将报告序列化为 JSON；序列化异常时回退兜底合法 JSON（Req 8.7）。
     */
    private String toJson(AnalysisReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            log.error("数据分析报告序列化为 JSON 失败，回退兜底报告", e);
            return FALLBACK_REPORT_JSON;
        }
    }
}
