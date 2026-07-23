package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.agent.model.Appointment;
import com.yupi.yuaiagent.agent.model.CoreInformation;
import com.yupi.yuaiagent.agent.model.CoreInfoType;
import com.yupi.yuaiagent.calendar.CalendarEvent;
import com.yupi.yuaiagent.calendar.CalendarService;
import com.yupi.yuaiagent.calendar.CalendarServiceFactory;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.config.FollowUpTemplateConfig;
import com.yupi.yuaiagent.hitl.AgentRequestContext;
import com.yupi.yuaiagent.hitl.HumanApprovalService;
import com.yupi.yuaiagent.repository.AppointmentRepository;
import com.yupi.yuaiagent.validation.InfoValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预约咨询 Agent
 * 处理用户预约咨询请求，通过追问机制收集必要信息
 * 
 * 状态机流程：
 * INITIAL -> COLLECTING_INFO -> CONFIRMING -> CREATING_APPOINTMENT -> COMPLETED
 * 
 * @author jsq
 */
@Slf4j
public class ConsultationAgent {

    // 系统提示词
    private static final String SYSTEM_PROMPT = """
            你是一位专业的预约咨询助手。你的职责是：
            1. 帮助用户预约咨询服务
            2. 收集必要的预约信息（姓名、联系方式、预约时间）
            3. 确认预约信息并创建预约
            
            回答要求：
            - 使用 Markdown 格式，优先列表与 **粗体**，少用 ### 大标题
            - 若使用标题，# 后必须空一格（正确：### 标题；错误：###标题）
            - 信息分点列出，层次清晰
            - 关键信息（时间、联系方式）用 **粗体** 标注
            - 回复简洁专业，避免冗长
            """;

    // 意图识别提示词
    private static final String INTENT_DETECTION_PROMPT = """
            分析用户的消息，判断用户是否想要预约咨询服务。
            
            用户可能使用以下表达方式：
            - "我想预约咨询"
            - "预约专家"
            - "咨询预约"
            - "约个时间聊聊"
            - "想请教一下"
            - "预约个时间"
            
            用户消息：{message}
            
            请只回复 true 或 false，不要有其他内容。
            """;

    // 信息提取提示词
    private static final String INFO_EXTRACTION_PROMPT = """
            从用户的消息中提取预约相关信息。
            
            需要提取的信息：
            - name: 姓名
            - contact: 联系方式（手机号或邮箱）
            - appointmentTime: 预约时间（尽量转换为标准格式）
            - topic: 咨询主题（可选）
            - remark: 备注（可选）
            
            用户消息：{message}
            当前上下文：{context}
            
            请以 JSON 格式返回提取到的信息，只返回存在的字段，不要有其他内容。
            示例：{"name": "张三", "contact": "13800138000"}
            """;

    // 非核心信息智能追问提示词（由 AI 根据上下文生成，Req 5.4）
    private static final String SMART_FOLLOWUP_PROMPT = """
            用户正在预约咨询，核心信息（姓名、联系方式、预约时间）已收集完成。
            现在请你根据已有上下文，生成一句自然、友好的追问，引导用户补充
            「咨询主题」等非核心信息（如果用户没有特别想咨询的内容，可直接跳过）。
            
            已收集信息：{context}
            
            要求：
            - 只输出一句追问，不要解释、不要加引号、不要多余内容
            - 语气专业、亲切，并提示用户可以回复「跳过」或「没有」直接进入确认
            """;

    // 状态枚举
    public enum ConsultationState {
        INITIAL,           // 初始状态
        COLLECTING_INFO,   // 收集信息中
        CONFIRMING,        // 确认信息中
        CREATING_APPOINTMENT, // 创建预约中
        COMPLETED          // 完成
    }

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final FollowUpTemplateConfig templateConfig;
    private final InfoValidator infoValidator;
    private final CalendarServiceFactory calendarServiceFactory;
    private final AppointmentRepository appointmentRepository;
    /** Nullable — when absent, HITL gating for calendar creation is skipped (e.g. unit tests). */
    private final HumanApprovalService approvalService;
    
    // 会话状态存储（chatId -> 状态）
    private final Map<String, ConsultationState> sessionStates = new ConcurrentHashMap<>();
    // 会话信息存储（chatId -> CoreInformation）
    private final Map<String, CoreInformation> sessionInfos = new ConcurrentHashMap<>();
    // 当前追问字段存储（chatId -> fieldName），表示正在等待用户回答的字段
    private final Map<String, String> currentQuestionFields = new ConcurrentHashMap<>();
    // 已发起过 AI 智能追问的非核心字段（chatId -> 字段集合），避免重复追问导致死循环
    private final Map<String, Set<String>> optionalAskedFields = new ConcurrentHashMap<>();
    /** 待人工确认的审批单（chatId -> approvalId），支持聊天内二次确认 */
    private final Map<String, String> pendingApprovalIds = new ConcurrentHashMap<>();

    /**
     * 构造函数
     */
    public ConsultationAgent(ChatModel chatModel, ChatMemoryManager chatMemoryManager,
                             FollowUpTemplateConfig templateConfig, InfoValidator infoValidator,
                             CalendarServiceFactory calendarServiceFactory,
                             AppointmentRepository appointmentRepository) {
        this(chatModel, chatMemoryManager, templateConfig, infoValidator,
                calendarServiceFactory, appointmentRepository, null);
    }

    /**
     * 构造函数（支持 HITL 人工审批网关）
     */
    public ConsultationAgent(ChatModel chatModel, ChatMemoryManager chatMemoryManager,
                             FollowUpTemplateConfig templateConfig, InfoValidator infoValidator,
                             CalendarServiceFactory calendarServiceFactory,
                             AppointmentRepository appointmentRepository,
                             HumanApprovalService approvalService) {
        this.chatMemory = chatMemoryManager.getMemory("consultation");
        this.templateConfig = templateConfig;
        this.infoValidator = infoValidator;
        this.calendarServiceFactory = calendarServiceFactory;
        this.appointmentRepository = appointmentRepository;
        this.approvalService = approvalService;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        
        log.info("ConsultationAgent 初始化完成");
    }

    /**
     * Check if the given chat has an active (in-progress) consultation.
     * Used by OrchestratorAgent to lock routing during multi-turn collection.
     */
    public boolean hasActiveConsultation(String chatId) {
        ConsultationState state = sessionStates.get(chatId);
        return state != null && state != ConsultationState.COMPLETED;
    }

    /**
     * 检测是否为预约咨询意图
     */
    public boolean detectConsultationIntent(String message) {
        String result = chatClient.prompt()
                .user(INTENT_DETECTION_PROMPT.replace("{message}", message))
                .call()
                .content();
        
        boolean isConsultation = "true".equalsIgnoreCase(result.trim());
        log.info("预约意图检测：{} -> {}", message, isConsultation);
        return isConsultation;
    }

    /**
     * 同步对话
     */
    public String chat(String message, String chatId) {
        return chat(message, chatId, null);
    }

    /** 可预约服务目录（询问「有什么可约」时直接返回，不进入填表） */
    private static final String SERVICE_CATALOG = """
            ### 可以预约的咨询服务

            说明：目前是**一对一专家咨询**（约 30–60 分钟），不是录播课/培训班。

            WorkPilot 目前支持预约以下方向：

            1. **职业方向梳理** — 迷茫期定位、转行评估、发展路径
            2. **简历与求职咨询** — 简历优化、投递策略、面试准备
            3. **薪资谈判咨询** — 谈薪话术、报价区间、涨薪路径
            4. **离职规划咨询** — 辞职节奏、交接、竞业与补偿风险
            5. **通用职场咨询** — 晋升、沟通、团队协作等一对一答疑

            想预约的话，直接说方向 + 时间即可，例如：
            「预约职业方向梳理，明天下午 3 点」
            """;

    /**
     * 同步对话（支持画像注入）
     *
     * <p>说明：ConsultationAgent 采用状态机 + 模板驱动，面向用户的回复主要来自
     * {@link FollowUpTemplateConfig} 模板与状态流转，而非 LLM 自由生成；其内部仅有的
     * ChatClient 调用为意图识别（返回 true/false）与信息抽取（返回 JSON）等结构化任务，
     * 注入画像文本会污染这些结构化输出。因此本重载以最小侵入方式透传 profileInjection，
     * 不改变既有预约收集流程，保证向后兼容。</p>
     *
     * @param profileInjection 可选的用户画像提示片段（当前 Agent 的结构化流程不消费该参数）
     */
    public String chat(String message, String chatId, String profileInjection) {
        ConsultationState state = sessionStates.getOrDefault(chatId, ConsultationState.INITIAL);
        CoreInformation info = sessionInfos.computeIfAbsent(chatId, k -> new CoreInformation());
        
        log.info("ConsultationAgent 处理消息，会话：{}，状态：{}", chatId, state);

        // 取消预约：任意收集阶段可退出，避免路由长期锁定
        if (isCancelBooking(message) && state != ConsultationState.INITIAL && state != ConsultationState.COMPLETED) {
            clearSession(chatId);
            return "好的，已取消本次预约流程。之后想约随时说「我想预约咨询」即可。";
        }

        // 「有什么可以预约」：先介绍目录，并进入预约会话锁定，避免下一句「选3」被路由到通用顾问
        if (isServiceCatalogInquiry(message)) {
            String catalog = SERVICE_CATALOG.trim();
            if (state == ConsultationState.INITIAL || state == ConsultationState.COMPLETED) {
                sessionStates.put(chatId, ConsultationState.COLLECTING_INFO);
                currentQuestionFields.put(chatId, "topic");
                return catalog + "\n\n请回复**序号**（如「3」）或直接说出方向；也可以一并告诉我时间，例如：「选择3，明天下午两点」。";
            }
            String pending = currentQuestionFields.get(chatId);
            if (pending != null) {
                return catalog + "\n\n---\n若继续刚才的预约，请补充您的**" + renderFieldName(pending) + "**。";
            }
            currentQuestionFields.put(chatId, "topic");
            return catalog + "\n\n请回复序号选择方向，或直接说出想预约的内容。";
        }
        
        // 如果是初始状态：已由 Orchestrator 路由到本 Agent，禁止再串行打 3～4 次 LLM
        // （旧逻辑：detectIntent + 历史抽取 + 消息抽取 + 打招呼，合计约 15～25s）
        if (state == ConsultationState.INITIAL) {
            state = ConsultationState.COLLECTING_INFO;
            sessionStates.put(chatId, state);

            // 仅用本地规则抽取（姓名/手机/邮箱/时间），不调用 LLM
            extractInfoLocally(message, info);

            String missingHint = firstMissingCoreField(info);
            if (missingHint == null) {
                sessionStates.put(chatId, ConsultationState.CONFIRMING);
                return "已根据您的描述整理预约信息：\n\n" + renderConfirmation(info);
            }

            currentQuestionFields.put(chatId, missingHint);
            String topicHint = "";
            if (info.getTopic() != null && !info.getTopic().isBlank()) {
                topicHint = "（主题：**" + info.getTopic() + "**）";
            } else if (message != null && (message.contains("方向") || message.contains("简历")
                    || message.contains("薪资") || message.contains("离职"))) {
                // 轻量记下话题，后续确认页可用
                if (message.contains("方向") || message.contains("职业")) {
                    info.setTopic("职业方向梳理");
                }
                topicHint = info.getTopic() != null ? "（主题：**" + info.getTopic() + "**）" : "";
            }

            return "好的，开始为您预约一对一咨询" + topicHint + "。\n\n"
                    + renderCoreQuestion(missingHint);
        }
        
        // 根据状态处理
        return switch (state) {
            case COLLECTING_INFO -> handleCollectingInfo(message, chatId, info);
            case CONFIRMING -> handleConfirming(message, chatId, info);
            case CREATING_APPOINTMENT -> handleCreatingAppointment(message, chatId, info);
            case COMPLETED -> handleCompleted(message, chatId);
            default -> "抱歉，出现了未知状态。请重新开始预约。";
        };
    }

    /**
     * 流式对话
     */
    public Flux<String> chatStream(String message, String chatId) {
        return chatStream(message, chatId, null);
    }

    /**
     * 流式对话（支持画像注入）
     *
     * <p>透传 profileInjection 至 {@link #chat(String, String, String)}；ConsultationAgent
     * 的结构化流程不消费该参数，详见同名重载说明。</p>
     *
     * @param profileInjection 可选的用户画像提示片段（当前 Agent 的结构化流程不消费该参数）
     */
    public Flux<String> chatStream(String message, String chatId, String profileInjection) {
        // 简化实现：先同步处理，再转为 Flux
        String result = chat(message, chatId, profileInjection);
        return Flux.just(result);
    }

    /**
     * 处理信息收集阶段
     * <p>
     * 流程：先校验/保存用户对上一个追问的回答，再尝试从自由文本中抽取信息；
     * 核心信息缺失时使用模板追问（Req 5.1, 5.3），核心信息完整后由 AI 智能追问
     * 非核心信息（Req 5.4），全部就绪后进入确认阶段（Req 5.5）。
     */
    private String handleCollectingInfo(String message, String chatId, CoreInformation info) {
        // 获取当前追问的字段
        String currentField = currentQuestionFields.get(chatId);

        // 同一句里可能带「选择3 + 明天下午两点」：先本地抽时间/联系方式/序号主题
        extractInfoLocally(message, info);
        applyCatalogChoice(message, info);

        if (currentField != null) {
            // 用户在追问中途改口问「能约什么」已在 chat() 入口处理；
            // 明显不是在答当前槽位时，不要用「请提供具体时间」之类校验文案怼回去
            if (isOffTopicSlotReply(message, currentField)) {
                return "我这边先记下您在问预约相关说明。\n\n"
                        + SERVICE_CATALOG.trim()
                        + "\n\n---\n若继续预约，请直接回复您的**"
                        + renderFieldName(currentField) + "**。";
            }
            if ("topic".equals(currentField)) {
                if (!isSkipResponse(message) && (info.getTopic() == null || info.getTopic().isBlank())) {
                    info.setTopic(message.trim());
                }
                markOptionalAsked(chatId, currentField);
                currentQuestionFields.remove(chatId);
            } else if (isCoreField(currentField)) {
                // 核心字段：校验用户回答（Req 5.6）
                // 若本轮本地已抽到该字段（例如时间），则不再用整句做失败校验
                boolean alreadyFilled = switch (currentField) {
                    case "name" -> info.getName() != null && !info.getName().isBlank();
                    case "contact" -> info.getContact() != null && !info.getContact().isBlank();
                    case "appointmentTime" -> info.getAppointmentTime() != null;
                    default -> false;
                };
                if (!alreadyFilled) {
                    String validationMessage = validateAnswer(currentField, message);
                    if (validationMessage != null) {
                        return templateConfig.renderValidationFailed(validationMessage);
                    }
                    saveAnswer(currentField, message, info);
                }
                currentQuestionFields.remove(chatId);
            } else {
                // 其他非核心字段：允许用户跳过
                if (!isSkipResponse(message)) {
                    saveAnswer(currentField, message, info);
                }
                markOptionalAsked(chatId, currentField);
                currentQuestionFields.remove(chatId);
            }
        } else {
            // 无待回答字段，尝试从自由文本中抽取信息（本地不够再 LLM）
            extractInfoFromMessage(message, info);
        }

        // 优先收集缺失的核心信息（使用模板追问，Req 5.1, 5.3）
        String missingCoreField = firstMissingCoreField(info);
        if (missingCoreField != null) {
            currentQuestionFields.put(chatId, missingCoreField);
            String topicLine = (info.getTopic() != null && !info.getTopic().isBlank())
                    ? "已选方向：**" + info.getTopic() + "**。\n\n" : "";
            return topicLine + renderCoreQuestion(missingCoreField);
        }

        // 核心信息已完整，AI 智能追问非核心信息（Req 5.4）
        String optionalField = nextOptionalField(chatId, info);
        if (optionalField != null) {
            currentQuestionFields.put(chatId, optionalField);
            markOptionalAsked(chatId, optionalField);
            return generateSmartFollowUp(info, optionalField);
        }

        // 全部信息就绪，进入确认阶段（Req 5.5）
        sessionStates.put(chatId, ConsultationState.CONFIRMING);
        return renderConfirmation(info);
    }

    /**
     * 判断字段是否为核心信息字段
     */
    private boolean isCoreField(String fieldName) {
        return CoreInfoType.fromFieldName(fieldName) != null;
    }

    /** 用户在问「有什么可约 / 服务介绍」，而不是在提交预约槽位 */
    static boolean isServiceCatalogInquiry(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim();
        return m.matches("(?s).*(有什么|有哪些|哪些服务|能约什么|可以预约什么|先告诉我|介绍一下).*(预约|咨询|课程).*")
                || m.matches("(?s).*(预约|咨询).*(什么|哪些|哪些服务|能约什么|课程).*")
                || m.contains("可以预约什么")
                || m.contains("有什么可以预约")
                || m.contains("能预约什么")
                || m.contains("预约什么服务")
                || m.contains("可预约的课程")
                || m.contains("预约的课程");
    }

    static boolean isCancelBooking(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim();
        return m.contains("取消预约") || m.contains("不约了") || m.contains("取消这次")
                || "取消".equals(m) || "算了".equals(m) || "不用了".equals(m);
    }

    /**
     * 当前在等某个槽位时，用户回复明显不是在填该槽（例如仍在问介绍）。
     */
    private boolean isOffTopicSlotReply(String message, String currentField) {
        if (isServiceCatalogInquiry(message) || isCancelBooking(message)) {
            return true;
        }
            // 等时间时，整句在问「什么/哪些」且不含日期特征 → 不当作时间答案
            // 「选择3」是目录选项，不是跑题
            if (message != null && message.matches("(?s).*(?:选择|选)\\s*[1-5].*")) {
                return false;
            }
            if ("appointmentTime".equals(currentField)) {
                String m = message == null ? "" : message;
                boolean askingWhat = m.contains("什么") || m.contains("哪些") || m.contains("介绍");
                boolean looksLikeTime = m.matches(".*\\d.*") || m.contains("点") || m.contains("号")
                        || m.contains("明天") || m.contains("后天") || m.contains("周") || m.contains("月");
                return askingWhat && !looksLikeTime;
            }
            return false;
    }

    /**
     * Render field name to Chinese display name
     */
    private String renderFieldName(String fieldName) {
        CoreInfoType type = CoreInfoType.fromFieldName(fieldName);
        return type != null ? type.getDisplayName() : fieldName;
    }

    /**
     * 判断用户是否表达了「跳过」非核心追问的意图
     */
    private boolean isSkipResponse(String message) {
        if (message == null) {
            return true;
        }
        String trimmed = message.trim();
        return trimmed.isEmpty()
                || trimmed.contains("跳过")
                || trimmed.contains("没有")
                || trimmed.contains("不用")
                || trimmed.contains("无")
                || "skip".equalsIgnoreCase(trimmed)
                || "no".equalsIgnoreCase(trimmed);
    }

    /**
     * 返回第一个缺失的核心信息字段（按 name -> contact -> appointmentTime 顺序），无缺失返回 null
     */
    private String firstMissingCoreField(CoreInformation info) {
        List<String> missing = info.getMissingFields();
        return missing.isEmpty() ? null : missing.get(0);
    }

    /**
     * 渲染核心信息追问问题（使用模板，Req 5.3）
     */
    private String renderCoreQuestion(String fieldName) {
        FollowUpTemplateConfig.TemplateConfig template = templateConfig.getCoreTemplate(fieldName);
        if (template != null && template.getQuestion() != null) {
            return template.getQuestion();
        }
        // 模板缺失时的兜底追问
        CoreInfoType type = CoreInfoType.fromFieldName(fieldName);
        String displayName = type != null ? type.getDisplayName() : fieldName;
        return "请提供您的" + displayName + "。";
    }

    /**
     * 选择下一个需要 AI 智能追问的非核心字段，无则返回 null
     */
    private String nextOptionalField(String chatId, CoreInformation info) {
        Set<String> asked = optionalAskedFields.getOrDefault(chatId, Set.of());
        // 仅追问尚未收集且未追问过的「咨询主题」（topic）
        if ((info.getTopic() == null || info.getTopic().isEmpty()) && !asked.contains("topic")) {
            return "topic";
        }
        return null;
    }

    /**
     * 记录某个非核心字段已发起过追问，避免重复追问
     */
    private void markOptionalAsked(String chatId, String fieldName) {
        optionalAskedFields.computeIfAbsent(chatId, k -> ConcurrentHashMap.newKeySet()).add(fieldName);
    }

    /**
     * 由 AI 根据上下文智能生成非核心信息追问（Req 5.4）
     */
    private String generateSmartFollowUp(CoreInformation info, String fieldName) {
        try {
            String context = String.format("姓名=%s，联系方式=%s，预约时间=%s",
                    info.getName(), info.getContact(),
                    infoValidator.formatDateTime(info.getAppointmentTime()));
            String response = chatClient.prompt()
                    .user(SMART_FOLLOWUP_PROMPT.replace("{context}", context))
                    .call()
                    .content();
            if (response != null && !response.isBlank()) {
                return response.trim();
            }
        } catch (Exception e) {
            log.warn("AI 智能追问生成失败，回退到模板追问", e);
        }
        // AI 调用失败时回退到非核心模板
        FollowUpTemplateConfig.TemplateConfig template = templateConfig.getOptionalTemplate(fieldName);
        if (template != null && template.getQuestion() != null) {
            return template.getQuestion();
        }
        return "您还想补充咨询主题吗？如果没有，回复「跳过」即可。";
    }

    /**
     * 验证答案 — 先提取再验证
     */
    private String validateAnswer(String fieldName, String answer) {
        InfoValidator.ValidationResult result;
        
        switch (fieldName) {
            case "name":
                // 先尝试从自然语言中提取姓名
                String extractedName = infoValidator.extractName(answer);
                result = infoValidator.validateName(extractedName);
                break;
            case "contact":
                result = infoValidator.validateContact(answer);
                break;
            case "appointmentTime":
                result = infoValidator.validateAppointmentTime(answer);
                break;
            case "topic":
                result = infoValidator.validateTopic(answer);
                break;
            case "remark":
                result = infoValidator.validateRemark(answer);
                break;
            default:
                return null;
        }
        
        return result.isValid() ? null : result.getMessage();
    }

    /**
     * 保存答案
     */
    private void saveAnswer(String fieldName, String answer, CoreInformation info) {
        switch (fieldName) {
            case "name":
                info.setName(infoValidator.extractName(answer));
                break;
            case "contact":
                info.setContact(infoValidator.extractContact(answer));
                break;
            case "appointmentTime":
                LocalDateTime time = infoValidator.extractDateTime(answer.trim());
                if (time != null) {
                    info.setAppointmentTime(time);
                }
                break;
            case "topic":
                info.setTopic(answer.trim());
                break;
            case "remark":
                info.setRemark(answer.trim());
                break;
        }
    }

    /**
     * 渲染确认信息
     */
    private String renderConfirmation(CoreInformation info) {
        return templateConfig.renderConfirmation(
                info.getName(),
                info.getContact(),
                infoValidator.formatDateTime(info.getAppointmentTime()),
                info.getTopic(),
                info.getRemark()
        );
    }

    /**
     * 处理确认阶段 — 支持回答用户问题后再引导确认
     */
    private String handleConfirming(String message, String chatId, CoreInformation info) {
        String trimmed = message.trim();
        
        if (trimmed.contains("确认") || trimmed.contains("确定") || trimmed.contains("是") || trimmed.contains("yes")) {
            sessionStates.put(chatId, ConsultationState.CREATING_APPOINTMENT);
            return handleCreatingAppointment("confirmed", chatId, info);
        } else if (trimmed.contains("修改") || trimmed.contains("重新") || trimmed.contains("不") || trimmed.contains("no")) {
            sessionStates.put(chatId, ConsultationState.COLLECTING_INFO);
            sessionInfos.put(chatId, new CoreInformation());
            optionalAskedFields.remove(chatId);
            currentQuestionFields.remove(chatId);
            return "好的，请重新提供您的预约信息。请先告诉我您的姓名。";
        } else {
            // 用户问了其他问题，用 LLM 回答后引导确认
            try {
                // Build context with conversation history + current appointment info
                String contextInfo = String.format("已收集预约信息：姓名=%s, 联系方式=%s, 时间=%s, 主题=%s",
                        info.getName(), info.getContact(),
                        infoValidator.formatDateTime(info.getAppointmentTime()),
                        info.getTopic());
                // Get recent conversation history from ChatMemory
                String historyContext = "";
                try {
                    var history = chatMemory.get(chatId);
                    if (history != null && !history.isEmpty()) {
                        StringBuilder sb = new StringBuilder("\n\n近期对话：\n");
                        int from = Math.max(0, history.size() - 6);
                        for (int i = from; i < history.size(); i++) {
                            var msg = history.get(i);
                            String role = "user".equals(msg.getMessageType().getValue()) ? "用户" : "AI";
                            sb.append(role).append("：").append(msg.getText()).append("\n");
                        }
                        historyContext = sb.toString();
                    }
                } catch (Exception ignored) {}

                String aiAnswer = chatClient.prompt()
                        .system("你是一位专业的预约咨询助手。用户正在确认预约。" + contextInfo + historyContext + "\n请根据上下文简短回答用户的问题，然后引导用户回复「确认」或「修改」。回复要简洁。")
                        .user(message)
                        .call()
                        .content();
                return aiAnswer + "\n\n如无其他问题，请回复「确认」创建预约，或回复「修改」重新填写。";
            } catch (Exception e) {
                return "请回复「确认」创建预约，或回复「修改」重新填写信息。";
            }
        }
    }

    /**
     * 处理创建预约阶段
     */
    private String handleCreatingAppointment(String message, String chatId, CoreInformation info) {
        // HITL 网关：日历创建为高危副作用操作，需人工审批（Req: HITL calendar create）
        if (approvalService != null
                && approvalService.requiresApproval(HumanApprovalService.ActionType.CALENDAR_CREATE)) {
            String appointmentSummary = String.format(
                    "- **姓名**：%s\n- **联系方式**：%s\n- **时间**：%s\n- **主题**：%s",
                    info.getName(), info.getContact(),
                    infoValidator.formatDateTime(info.getAppointmentTime()),
                    info.getTopic() != null ? info.getTopic() : "一对一咨询");
            String payloadHint = String.format("姓名=%s, 联系方式=%s, 时间=%s, 主题=%s",
                    info.getName(), info.getContact(),
                    infoValidator.formatDateTime(info.getAppointmentTime()), info.getTopic());
            AgentRequestContext.Holder ctx = AgentRequestContext.get();
            String userId = ctx != null ? ctx.userId() : null;

            String approvalId = pendingApprovalIds.get(chatId);
            if (approvalId == null && ctx != null) {
                approvalId = ctx.approvalId();
            }

            // 聊天内二次确认：用户回复「确认创建」→ 批准并继续创建
            // 前端按钮可能已先 approve，此处对 APPROVED 幂等，避免「approval not pending」
            if (isHitlConfirmMessage(message) && approvalId != null) {
                try {
                    var existing = approvalService.get(approvalId);
                    if (existing.isEmpty()) {
                        return "确认单已失效，请再回复「确认」重新发起预约。";
                    }
                    var status = existing.get().getStatus();
                    if (status == HumanApprovalService.Status.PENDING) {
                        approvalService.approve(approvalId, userId);
                    } else if (status == HumanApprovalService.Status.APPROVED) {
                        // 已批准（例如点了前端按钮），继续往下 consume + 创建
                        log.info("HITL 已是 APPROVED，跳过重复 approve: {}", approvalId);
                    } else if (status == HumanApprovalService.Status.CONSUMED) {
                        // 理论上不应再到这里；若已消费则直接走创建会重复，提示用户
                        return "该预约确认已处理过。如需新预约，请说「我想预约咨询」。";
                    } else {
                        return "确认单状态为 " + status + "，无法继续。请回复「确认」重新发起，或「取消」放弃。";
                    }
                } catch (Exception e) {
                    log.warn("HITL 聊天确认失败: {}", e.getMessage());
                    return "确认失败：" + e.getMessage() + "\n\n请重新回复「确认」发起预约，或「取消」放弃。";
                }
            }

            // 取消
            if (isHitlCancelMessage(message) && approvalId != null) {
                try {
                    approvalService.reject(approvalId, userId);
                } catch (Exception ignored) {}
                pendingApprovalIds.remove(chatId);
                sessionStates.put(chatId, ConsultationState.CONFIRMING);
                return "已取消日历创建。如仍需预约，请再回复「确认」；或回复「修改」重新填写信息。";
            }

            boolean approved = approvalService.consumeIfApproved(
                    approvalId, HumanApprovalService.ActionType.CALENDAR_CREATE, payloadHint);
            if (!approved) {
                // 复用同会话未过期审批，避免每次「确认」都新开一张单
                var existing = approvalService.findPendingByChatId(chatId);
                HumanApprovalService.ApprovalRequest req = existing.orElseGet(() ->
                        approvalService.requestApproval(
                                userId, chatId, HumanApprovalService.ActionType.CALENDAR_CREATE,
                                appointmentSummary, payloadHint));
                pendingApprovalIds.put(chatId, req.getApprovalId());
                return approvalService.pendingMessage(req);
            }
            pendingApprovalIds.remove(chatId);
        }

        try {
            // 创建预约记录
            Appointment appointment = info.toAppointment(chatId, calendarServiceFactory.getCalendarService().getProvider());
            appointment.setAppointmentId(UUID.randomUUID().toString());
            
            // 调用日历服务创建事件
            try {
                CalendarService calendarService = calendarServiceFactory.getCalendarService();
                CalendarEvent event = calendarService.createEvent(appointment);
                appointment.setCalendarEventId(event.getEventId());
                appointment.setCalendarLink(event.getLink());
                appointment.setStatus(Appointment.AppointmentStatus.CONFIRMED);
                log.info("日历事件创建成功：{}", event.getEventId());
            } catch (CalendarService.CalendarException e) {
                log.error("创建日历事件失败", e);
                // 日历创建失败不影响预约记录保存，只是没有日历事件
                appointment.setStatus(Appointment.AppointmentStatus.PENDING);
            }
            
            // 持久化预约记录
            appointmentRepository.save(appointment);
            log.info("预约记录保存成功：{}", appointment.getAppointmentId());
            
            // 进入完成状态
            sessionStates.put(chatId, ConsultationState.COMPLETED);
            
            return templateConfig.renderSuccess(
                    appointment.getAppointmentId(),
                    info.getName(),
                    infoValidator.formatDateTime(info.getAppointmentTime()),
                    info.getContact()
            );
        } catch (Exception e) {
            log.error("创建预约失败", e);
            return templateConfig.renderFailure(e.getMessage());
        }
    }

    private boolean isHitlConfirmMessage(String message) {
        if (message == null) return false;
        String t = message.trim();
        return t.contains("确认创建") || t.contains("同意创建") || t.contains("批准")
                || "确认".equals(t) || "确定".equals(t) || "同意".equals(t);
    }

    private boolean isHitlCancelMessage(String message) {
        if (message == null) return false;
        String t = message.trim();
        return t.contains("取消") || t.contains("放弃") || t.contains("拒绝");
    }

    /**
     * 处理完成阶段
     */
    private String handleCompleted(String message, String chatId) {
        // 清理会话状态
        sessionStates.remove(chatId);
        sessionInfos.remove(chatId);
        optionalAskedFields.remove(chatId);
        currentQuestionFields.remove(chatId);
        
        return "您的预约已完成。如需新的预约，请告诉我。";
    }

    /**
     * 从消息中提取信息
     */
    private void extractInfoFromMessage(String message, CoreInformation info) {
        // 先本地规则，避免每轮追问都打 LLM
        extractInfoLocally(message, info);
        // 本地已抽到核心字段则不再调 LLM
        if (firstMissingCoreField(info) == null) {
            return;
        }
        // 消息看起来不像在填槽（无手机/邮箱/日期特征）时跳过 LLM
        if (!looksLikeSlotPayload(message)) {
            return;
        }
        try {
            String context = String.format("当前已收集：姓名=%s, 联系方式=%s, 时间=%s",
                    info.getName(), info.getContact(), info.getAppointmentTime());
            
            String result = chatClient.prompt()
                    .user(INFO_EXTRACTION_PROMPT
                            .replace("{message}", message)
                            .replace("{context}", context))
                    .call()
                    .content();
            
            // 解析 JSON 结果
            parseExtractedInfo(result, info);
        } catch (Exception e) {
            log.warn("信息提取失败", e);
        }
    }

    /** 本地规则抽取：手机/邮箱/时间/简短姓名，0 LLM */
    private void extractInfoLocally(String message, CoreInformation info) {
        if (message == null || message.isBlank()) {
            return;
        }
        String contact = infoValidator.extractContact(message);
        if (contact != null && (info.getContact() == null || info.getContact().isBlank())) {
            var vr = infoValidator.validateContact(contact);
            if (vr.isValid()) {
                info.setContact(contact);
            }
        }
        LocalDateTime time = infoValidator.extractDateTime(message);
        if (time != null && info.getAppointmentTime() == null) {
            info.setAppointmentTime(time);
        }
        // 仅当整句很短、像在报名字时才当姓名
        String trimmed = message.trim();
        if ((info.getName() == null || info.getName().isBlank())
                && trimmed.length() >= 2 && trimmed.length() <= 8
                && !trimmed.contains("预约") && !trimmed.contains("咨询")
                && !trimmed.matches(".*\\d.*")) {
            String name = infoValidator.extractName(trimmed);
            if (name != null) {
                var vr = infoValidator.validateName(name);
                if (vr.isValid()) {
                    info.setName(name);
                }
            }
        }
        if ((info.getTopic() == null || info.getTopic().isBlank())) {
            if (message.contains("职业方向") || message.contains("方向迷茫") || message.contains("不确定方向")) {
                info.setTopic("职业方向梳理");
            } else if (message.contains("简历")) {
                info.setTopic("简历与求职");
            } else if (message.contains("谈薪") || message.contains("薪资") || message.contains("涨薪")) {
                info.setTopic("薪资谈判");
            } else if (message.contains("离职") || message.contains("辞职")) {
                info.setTopic("离职规划");
            }
        }
        applyCatalogChoice(message, info);
    }

    /** 解析「选择3 / 选3 / 3」到目录主题 */
    private void applyCatalogChoice(String message, CoreInformation info) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (info.getTopic() != null && !info.getTopic().isBlank()) {
            // 已有主题时，仅当明确「选择N」才覆盖
            if (!message.matches("(?s).*(?:选择|选)\\s*[1-5].*")) {
                return;
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:选择|选)\\s*([1-5])|^\\s*([1-5])\\s*$")
                .matcher(message.trim());
        if (!m.find()) {
            return;
        }
        String n = m.group(1) != null ? m.group(1) : m.group(2);
        String topic = switch (n) {
            case "1" -> "职业方向梳理";
            case "2" -> "简历与求职咨询";
            case "3" -> "薪资谈判咨询";
            case "4" -> "离职规划咨询";
            case "5" -> "通用职场咨询";
            default -> null;
        };
        if (topic != null) {
            info.setTopic(topic);
        }
    }

    private boolean looksLikeSlotPayload(String message) {
        if (message == null) return false;
        return message.matches(".*\\d{5,}.*")
                || message.contains("@")
                || message.contains("点")
                || message.contains("号")
                || message.contains("明天")
                || message.contains("后天")
                || message.contains("周");
    }

    /**
     * 从对话历史中提取已有的预约信息
     * 遍历 ChatMemory 中的所有消息，提取姓名、联系方式等
     */
    private void extractInfoFromHistory(String chatId, CoreInformation info) {
        try {
            var messages = chatMemory.get(chatId);
            if (messages == null || messages.isEmpty()) return;

            // 拼接所有用户消息为一段文本
            StringBuilder historyText = new StringBuilder();
            for (var msg : messages) {
                if ("user".equals(msg.getMessageType().getValue())) {
                    historyText.append(msg.getText()).append("\n");
                }
            }
            if (historyText.isEmpty()) return;

            // 用 LLM 从历史中提取
            String context = String.format("当前已收集：姓名=%s, 联系方式=%s, 时间=%s",
                    info.getName(), info.getContact(), info.getAppointmentTime());
            String result = chatClient.prompt()
                    .user(INFO_EXTRACTION_PROMPT
                            .replace("{message}", historyText.toString())
                            .replace("{context}", context))
                    .call()
                    .content();
            parseExtractedInfo(result, info);
            log.info("从对话历史提取到：name={}, contact={}, time={}",
                    info.getName(), info.getContact(), info.getAppointmentTime());
        } catch (Exception e) {
            log.warn("从对话历史提取信息失败", e);
        }
    }

    /**
     * 解析提取到的信息
     */
    private void parseExtractedInfo(String json, CoreInformation info) {
        // 简单的 JSON 解析（实际项目中应使用 Jackson）
        if (json.contains("\"name\"")) {
            String name = extractJsonValue(json, "name");
            if (name != null && !name.isEmpty()) {
                // 验证姓名
                InfoValidator.ValidationResult result = infoValidator.validateName(name);
                if (result.isValid()) {
                    info.setName(name);
                }
            }
        }
        if (json.contains("\"contact\"")) {
            String contact = extractJsonValue(json, "contact");
            if (contact != null && !contact.isEmpty()) {
                // 验证联系方式
                InfoValidator.ValidationResult result = infoValidator.validateContact(contact);
                if (result.isValid()) {
                    info.setContact(contact);
                }
            }
        }
        if (json.contains("\"appointmentTime\"")) {
            String timeStr = extractJsonValue(json, "appointmentTime");
            if (timeStr != null && !timeStr.isEmpty()) {
                try {
                    // 尝试多种时间格式
                    LocalDateTime time = infoValidator.parseDateTime(timeStr);
                    if (time != null) {
                        info.setAppointmentTime(time);
                    }
                } catch (Exception e) {
                    log.warn("时间解析失败：{}", timeStr);
                }
            }
        }
        if (json.contains("\"topic\"")) {
            String topic = extractJsonValue(json, "topic");
            if (topic != null && !topic.isEmpty()) {
                info.setTopic(topic);
            }
        }
        if (json.contains("\"remark\"")) {
            String remark = extractJsonValue(json, "remark");
            if (remark != null && !remark.isEmpty()) {
                info.setRemark(remark);
            }
        }
    }

    /**
     * 提取 JSON 值（简单实现）
     */
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * 清理会话状态
     */
    public void clearSession(String chatId) {
        sessionStates.remove(chatId);
        sessionInfos.remove(chatId);
        optionalAskedFields.remove(chatId);
        currentQuestionFields.remove(chatId);
        pendingApprovalIds.remove(chatId);
        log.info("清理会话状态：{}", chatId);
    }

    /**
     * 获取会话状态
     */
    public ConsultationState getSessionState(String chatId) {
        return sessionStates.getOrDefault(chatId, ConsultationState.INITIAL);
    }
}
