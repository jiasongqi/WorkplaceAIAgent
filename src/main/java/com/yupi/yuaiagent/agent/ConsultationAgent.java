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
            - 使用 Markdown 格式，善用标题、列表、粗体
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

            WorkPilot 目前支持一对一预约以下方向（约 30–60 分钟）：

            1. **简历与求职咨询** — 简历优化、投递策略、面试准备
            2. **薪资谈判咨询** — 谈薪话术、报价区间、涨薪路径
            3. **离职规划咨询** — 辞职节奏、交接、竞业与补偿风险
            4. **通用职场咨询** — 晋升、沟通、团队协作等一对一答疑

            想预约的话，直接说方向 + 时间即可，例如：
            「预约简历咨询，明天下午 3 点」
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

        // 「有什么可以预约」：先介绍目录，不要当成姓名/时间等槽位答案
        if (isServiceCatalogInquiry(message)) {
            String catalog = SERVICE_CATALOG.trim();
            if (state == ConsultationState.INITIAL || state == ConsultationState.COMPLETED) {
                // 仅介绍，不进入填表，避免把闲聊锁进预约状态机
                return catalog;
            }
            String pending = currentQuestionFields.get(chatId);
            if (pending != null) {
                return catalog + "\n\n---\n若继续刚才的预约，请补充您的**" + renderFieldName(pending) + "**。";
            }
            return catalog + "\n\n若要继续预约，请告诉我您想约的方向与时间。";
        }
        
        // 如果是初始状态，先检测意图
        if (state == ConsultationState.INITIAL) {
            if (!detectConsultationIntent(message)) {
                return SERVICE_CATALOG.trim() + "\n\n如果您需要预约，请告诉我希望咨询的方向与时间。";
            }
            state = ConsultationState.COLLECTING_INFO;
            sessionStates.put(chatId, state);
            // 从整个对话历史中提取已有信息（不只是当前消息）
            extractInfoFromHistory(chatId, info);
            // 从当前消息也提取
            extractInfoFromMessage(message, info);

            String missingHint = firstMissingCoreField(info);

            // 用 LLM 生成自然的首条回复（回答用户问题 + 引导提供缺失信息）
            try {
                String missingDesc = missingHint != null
                        ? "接下来需要收集预约信息。请自然地回答用户的问题（如有），然后引导用户提供" + renderFieldName(missingHint) + "。"
                        : "核心预约信息已完整。请简短回答用户的问题（如有），然后请用户确认预约信息。回复要简洁。";
                String aiGreeting = chatClient.prompt()
                        .system("你是一位专业的预约咨询助手。用户想预约咨询服务。" + missingDesc)
                        .user(message)
                        .call()
                        .content();
                // 如果还有缺失字段，设置当前追问字段
                if (missingHint != null) {
                    currentQuestionFields.put(chatId, missingHint);
                } else {
                    // 核心信息完整，进入确认阶段
                    sessionStates.put(chatId, ConsultationState.CONFIRMING);
                }
                // 追加确认信息（如果有）
                if (missingHint == null) {
                    return aiGreeting + "\n\n" + renderConfirmation(info);
                }
                return aiGreeting;
            } catch (Exception e) {
                log.warn("LLM 首条回复失败，使用模板", e);
                if (missingHint != null) {
                    currentQuestionFields.put(chatId, missingHint);
                    return renderCoreQuestion(missingHint);
                } else {
                    sessionStates.put(chatId, ConsultationState.CONFIRMING);
                    return renderConfirmation(info);
                }
            }
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

        if (currentField != null) {
            // 用户在追问中途改口问「能约什么」已在 chat() 入口处理；
            // 明显不是在答当前槽位时，不要用「请提供具体时间」之类校验文案怼回去
            if (isOffTopicSlotReply(message, currentField)) {
                return "我这边先记下您在问预约相关说明。\n\n"
                        + SERVICE_CATALOG.trim()
                        + "\n\n---\n若继续预约，请直接回复您的**"
                        + renderFieldName(currentField) + "**。";
            }
            if (isCoreField(currentField)) {
                // 核心字段：校验用户回答（Req 5.6）
                String validationMessage = validateAnswer(currentField, message);
                if (validationMessage != null) {
                    // 校验失败，重新追问（保持 currentField 不变）
                    return templateConfig.renderValidationFailed(validationMessage);
                }
                saveAnswer(currentField, message, info);
            } else {
                // 非核心字段：允许用户跳过
                if (!isSkipResponse(message)) {
                    saveAnswer(currentField, message, info);
                }
                markOptionalAsked(chatId, currentField);
            }
            currentQuestionFields.remove(chatId);
        } else {
            // 无待回答字段，尝试从自由文本中抽取信息
            extractInfoFromMessage(message, info);
        }

        // 优先收集缺失的核心信息（使用模板追问，Req 5.1, 5.3）
        String missingCoreField = firstMissingCoreField(info);
        if (missingCoreField != null) {
            currentQuestionFields.put(chatId, missingCoreField);
            return renderCoreQuestion(missingCoreField);
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
        return m.matches("(?s).*(有什么|有哪些|哪些服务|能约什么|可以预约什么|先告诉我|介绍一下).*(预约|咨询).*")
                || m.matches("(?s).*(预约|咨询).*(什么|哪些|哪些服务|能约什么).*")
                || m.contains("可以预约什么")
                || m.contains("有什么可以预约")
                || m.contains("能预约什么")
                || m.contains("预约什么服务");
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
            String appointmentSummary = String.format("姓名=%s, 联系方式=%s, 时间=%s, 主题=%s",
                    info.getName(), info.getContact(),
                    infoValidator.formatDateTime(info.getAppointmentTime()), info.getTopic());
            AgentRequestContext.Holder ctx = AgentRequestContext.get();
            String approvalId = ctx != null ? ctx.approvalId() : null;
            boolean approved = approvalService.consumeIfApproved(
                    approvalId, HumanApprovalService.ActionType.CALENDAR_CREATE, appointmentSummary);
            if (!approved) {
                String userId = ctx != null ? ctx.userId() : null;
                HumanApprovalService.ApprovalRequest req = approvalService.requestApproval(
                        userId, chatId, HumanApprovalService.ActionType.CALENDAR_CREATE,
                        "创建日历预约：" + appointmentSummary, appointmentSummary);
                // 保持在 CREATING_APPOINTMENT 状态，等待人工审批后重新触发确认
                return approvalService.pendingMessage(req);
            }
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
        log.info("清理会话状态：{}", chatId);
    }

    /**
     * 获取会话状态
     */
    public ConsultationState getSessionState(String chatId) {
        return sessionStates.getOrDefault(chatId, ConsultationState.INITIAL);
    }
}
