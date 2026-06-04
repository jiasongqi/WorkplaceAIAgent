# Design Document: Appointment Consultation Intent

## 1. 系统架构设计

### 1.1 架构概览

本功能在现有职场 AI Agent 系统基础上扩展，采用分层架构设计，新增以下核心组件：

```
┌─────────────────────────────────────────────────────────────────┐
│                      Presentation Layer                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   Controller    │  │   SSE Emitter   │  │   WebSocket     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────┐
│                       Agent Layer                               │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │                    OrchestratorAgent                       │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │ │
│  │  │IntentDetect │  │   Router    │  │  SSE Handler│        │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘        │ │
│  └───────────────────────────────────────────────────────────┘ │
│                              │                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │ResumeAgent│ │Negotiation│ │EscapeAgent│ │ConsultationAgent│  │
│  │          │ │  Agent    │ │          │ │   (NEW)          │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────┐
│                      Service Layer                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐ │
│  │  CalendarService │  │ FollowUpService  │  │MemoryCompress │ │
│  │    (NEW)         │  │    (NEW)         │  │  Service(NEW) │ │
│  └──────────────────┘  └──────────────────┘  └───────────────┘ │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ FeishuCalendar   │  │ DingTalkCalendar │                    │
│  │   Impl (NEW)     │  │    Impl (NEW)    │                    │
│  └──────────────────┘  └──────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────┐
│                       Data Layer                                │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐ │
│  │ AppointmentRepo  │  │ FollowUpTemplate │  │ChatMemoryRepo │ │
│  │    (NEW)         │  │    Repo (NEW)    │  │               │ │
│  └──────────────────┘  └──────────────────┘  └───────────────┘ │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ CompressedMemory │  │   MySQL/H2 DB    │                    │
│  │    Repo (NEW)    │  │                  │                    │
│  └──────────────────┘  └──────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 核心组件职责

| 组件 | 职责 | 新增/修改 |
|------|------|----------|
| **OrchestratorAgent** | 扩展意图识别，新增 CONSULTATION 意图路由 | 修改 |
| **ConsultationAgent** | 处理预约咨询请求，管理追问流程 | 新增 |
| **CalendarService** | 统一日历服务接口，抽象飞书/钉钉差异 | 新增 |
| **FeishuCalendarService** | 飞书日历 API 实现 | 新增 |
| **DingTalkCalendarService** | 钉钉日历 API 实现 | 新增 |
| **MemoryCompressionService** | 对话记忆压缩服务 | 新增 |
| **FollowUpService** | 追问逻辑管理服务 | 新增 |
| **FollowUpTemplateManager** | 追问模板配置管理 | 新增 |
| **ChatMemoryManager** | 扩展压缩状态查询和触发能力 | 修改 |

---

## 2. 组件设计

### 2.1 ConsultationAgent 类设计

```java
package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.model.*;
import com.yupi.yuaiagent.service.CalendarService;
import com.yupi.yuaiagent.service.FollowUpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.CompletableFuture;

/**
 * 预约咨询 Agent
 * 处理预约咨询请求，管理追问流程，对接日历服务
 */
@Slf4j
public class ConsultationAgent {

    private static final String SYSTEM_PROMPT = """
            你是一位专业的预约咨询顾问，帮助用户预约咨询服务。
            
            你的职责：
            1. 引导用户提供必要的预约信息（姓名、联系方式、预约时间）
            2. 解答用户关于预约流程的疑问
            3. 确认预约信息后协助完成预约
            
            追问原则：
            - 核心信息（姓名、联系方式、预约时间）使用标准追问模板
            - 非核心信息（咨询主题、备注）根据上下文智能追问
            - 信息格式不正确时，提示正确格式并重新追问
            
            回答风格：
            - 专业、耐心、清晰
            - 主动引导而非被动等待
            """;

    private final ChatClient chatClient;
    private final FollowUpService followUpService;
    private final CalendarService calendarService;
    private final ConsultationStateService stateService;

    // 构造函数
    public ConsultationAgent(ChatModel chatModel, 
                             ChatMemoryManager chatMemoryManager,
                             FollowUpService followUpService,
                             CalendarService calendarService,
                             ConsultationStateService stateService) {
        ChatMemory chatMemory = chatMemoryManager.getMemory("consultation");
        
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
        this.followUpService = followUpService;
        this.calendarService = calendarService;
        this.stateService = stateService;
    }

    /**
     * 处理用户消息，返回响应（同步）
     */
    public String chat(String message, String chatId) {
        // 1. 获取当前会话状态
        ConsultationState state = stateService.getState(chatId);
        
        // 2. 解析用户输入，提取信息
        UserInfoExtractor.ExtractionResult extracted = 
            followUpService.extractUserInfo(message, state);
        
        // 3. 更新会话状态
        state = stateService.updateState(chatId, extracted);
        
        // 4. 检查是否需要追问
        if (state.needsFollowUp()) {
            FollowUpQuestion question = followUpService.getNextQuestion(state);
            return generateFollowUpResponse(question);
        }
        
        // 5. 检查是否需要确认
        if (state.isReadyForConfirmation()) {
            return generateConfirmationResponse(state);
        }
        
        // 6. 检查是否已确认，执行预约
        if (state.isConfirmed()) {
            AppointmentResult result = createAppointment(state);
            return generateSuccessResponse(result);
        }
        
        // 7. 普通对话处理
        return handleGeneralChat(message, chatId);
    }

    /**
     * 流式对话处理
     */
    public Flux<String> chatStream(String message, String chatId) {
        // 流式实现
    }

    /**
     * 创建预约
     */
    private AppointmentResult createAppointment(ConsultationState state) {
        Appointment appointment = Appointment.fromState(state);
        return calendarService.createAppointment(appointment);
    }
}
```

### 2.2 CalendarService 接口与实现

```java
package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.model.Appointment;
import com.yupi.yuaiagent.model.AppointmentResult;

/**
 * 日历服务统一接口
 * 抽象飞书、钉钉等企业日历的差异
 */
public interface CalendarService {

    /**
     * 创建预约事件
     * @param appointment 预约信息
     * @return 创建结果，包含事件ID和日历链接
     */
    AppointmentResult createAppointment(Appointment appointment);

    /**
     * 取消预约事件
     * @param eventId 事件ID
     * @return 是否成功
     */
    boolean cancelAppointment(String eventId);

    /**
     * 修改预约事件
     * @param eventId 事件ID
     * @param appointment 新的预约信息
     * @return 修改结果
     */
    AppointmentResult updateAppointment(String eventId, Appointment appointment);

    /**
     * 检查时间段是否可用
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 是否可用
     */
    boolean checkAvailability(java.time.LocalDateTime startTime, 
                              java.time.LocalDateTime endTime);

    /**
     * 获取日历提供商类型
     */
    CalendarProvider getProvider();
}

/**
 * 飞书日历服务实现
 */
@Service
@ConditionalOnProperty(name = "calendar.provider", havingValue = "FEISHU")
public class FeishuCalendarService implements CalendarService {

    private final FeishuApiClient feishuClient;
    private final CalendarConfig config;

    @Override
    public AppointmentResult createAppointment(Appointment appointment) {
        try {
            // 构建飞书日历事件请求
            FeishuCalendarEvent event = FeishuCalendarEvent.builder()
                .summary("预约咨询 - " + appointment.getName())
                .description(buildEventDescription(appointment))
                .startTime(appointment.getAppointmentTime())
                .endTime(appointment.getAppointmentTime().plusHours(1))
                .attendees(List.of(appointment.getContact()))
                .build();

            // 调用飞书 API
            FeishuEventResponse response = feishuClient.createCalendarEvent(
                config.getCalendarId(), event);

            return AppointmentResult.success(
                response.getEventId(),
                response.getEventUrl(),
                CalendarProvider.FEISHU
            );
        } catch (FeishuApiException e) {
            log.error("飞书日历创建失败: {}", e.getMessage(), e);
            return AppointmentResult.failure(e.getMessage());
        }
    }

    // ... 其他方法实现
}

/**
 * 钉钉日历服务实现
 */
@Service
@ConditionalOnProperty(name = "calendar.provider", havingValue = "DINGTALK")
public class DingTalkCalendarService implements CalendarService {

    private final DingTalkApiClient dingTalkClient;
    private final CalendarConfig config;

    @Override
    public AppointmentResult createAppointment(Appointment appointment) {
        try {
            // 构建钉钉日历事件请求
            DingTalkCalendarEvent event = DingTalkCalendarEvent.builder()
                .summary("预约咨询 - " + appointment.getName())
                .description(buildEventDescription(appointment))
                .startTime(appointment.getAppointmentTime())
                .endTime(appointment.getAppointmentTime().plusHours(1))
                .build();

            // 调用钉钉 API
            DingTalkEventResponse response = dingTalkClient.createCalendarEvent(event);

            return AppointmentResult.success(
                response.getEventId(),
                response.getEventUrl(),
                CalendarProvider.DINGTALK
            );
        } catch (DingTalkApiException e) {
            log.error("钉钉日历创建失败: {}", e.getMessage(), e);
            return AppointmentResult.failure(e.getMessage());
        }
    }

    // ... 其他方法实现
}
```

### 2.3 记忆压缩服务

```java
package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.model.CompressedMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.List;

/**
 * 对话记忆压缩服务
 */
@Service
public class MemoryCompressionService {

    private static final String COMPRESSION_PROMPT = """
            请分析以下对话历史，提取并整理以下关键信息：
            
            1. 用户关键需求：用户想要解决什么问题？
            2. 已确认的信息：双方已达成共识的事实
            3. 未解决的问题：还有哪些问题待处理
            4. 重要决策：已做出的决定
            5. 约定事项：双方约定的事项
            
            请以结构化方式输出，每个部分用标题分隔。
            
            对话历史：
            {conversation}
            """;

    private final ChatModel chatModel;
    private final CompressionConfig config;

    /**
     * 压缩对话记忆
     * @param messages 原始消息列表
     * @param retainCount 保留最近N轮对话
     * @return 压缩后的记忆
     */
    public Mono<CompressedMemory> compress(List<Message> messages, int retainCount) {
        // 1. 分割：保留最近N轮 + 待压缩部分
        int splitIndex = Math.max(0, messages.size() - retainCount * 2);
        List<Message> toCompress = messages.subList(0, splitIndex);
        List<Message> retained = messages.subList(splitIndex, messages.size());

        if (toCompress.isEmpty()) {
            return Mono.just(CompressedMemory.empty());
        }

        // 2. 构建压缩提示
        String conversationText = buildConversationText(toCompress);
        String prompt = COMPRESSION_PROMPT.replace("{conversation}", conversationText);

        // 3. 调用 LLM 生成摘要（异步）
        return Mono.fromCallable(() -> {
            String summary = chatModel.call(prompt);
            return CompressedMemory.builder()
                .summary(summary)
                .originalMessageCount(toCompress.size())
                .compressedAt(java.time.Instant.now())
                .build();
        });
    }

    /**
     * 检查是否需要压缩
     */
    public boolean needsCompression(List<Message> messages, 
                                     CompressionTrigger trigger) {
        return switch (trigger.getType()) {
            case TOKEN_THRESHOLD -> 
                estimateTokens(messages) > trigger.getTokenThreshold();
            case ROUND_THRESHOLD -> 
                messages.size() / 2 > trigger.getRoundThreshold();
        };
    }

    private int estimateTokens(List<Message> messages) {
        // 简化估算：每4个字符约等于1个token
        return messages.stream()
            .mapToInt(m -> m.getContent().length() / 4)
            .sum();
    }
}

/**
 * 压缩策略枚举
 */
public enum CompressionTriggerType {
    TOKEN_THRESHOLD,    // 基于 Token 阈值触发
    ROUND_THRESHOLD     // 基于对话轮数触发
}

/**
 * 压缩触发条件
 */
@Data
public class CompressionTrigger {
    private CompressionTriggerType type;
    private int tokenThreshold = 4000;  // 默认 4000 tokens
    private int roundThreshold = 20;    // 默认 20 轮
}
```

### 2.4 追问服务

```java
package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.model.*;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * 追问服务
 * 管理追问流程和模板
 */
@Service
public class FollowUpService {

    private final FollowUpTemplateManager templateManager;
    private final UserInfoExtractor infoExtractor;

    /**
     * 提取用户信息
     */
    public UserInfoExtractor.ExtractionResult extractUserInfo(
            String message, ConsultationState currentState) {
        return infoExtractor.extract(message, currentState);
    }

    /**
     * 获取下一个追问问题
     */
    public FollowUpQuestion getNextQuestion(ConsultationState state) {
        // 按优先级检查缺失的核心信息
        if (!state.hasName()) {
            return createFollowUpQuestion(
                CoreInfoType.NAME, 
                templateManager.getTemplate(CoreInfoType.NAME)
            );
        }
        if (!state.hasContact()) {
            return createFollowUpQuestion(
                CoreInfoType.CONTACT,
                templateManager.getTemplate(CoreInfoType.CONTACT)
            );
        }
        if (!state.hasAppointmentTime()) {
            return createFollowUpQuestion(
                CoreInfoType.APPOINTMENT_TIME,
                templateManager.getTemplate(CoreInfoType.APPOINTMENT_TIME)
            );
        }

        // 核心信息完整，检查非核心信息
        return generateSmartFollowUp(state);
    }

    /**
     * 生成智能追问（非核心信息）
     */
    private FollowUpQuestion generateSmartFollowUp(ConsultationState state) {
        // 由 AI 根据上下文生成追问
        // 这里返回 null 表示无需追问
        return null;
    }

    private FollowUpQuestion createFollowUpQuestion(
            CoreInfoType type, FollowUpTemplate template) {
        return FollowUpQuestion.builder()
            .questionType(type)
            .question(template.getQuestion())
            .formatHint(template.getFormatHint())
            .isRequired(true)
            .build();
    }
}

/**
 * 追问模板管理器
 */
@Service
public class FollowUpTemplateManager {

    private final Map<CoreInfoType, FollowUpTemplate> templates = new ConcurrentHashMap<>();
    private final FollowUpTemplateRepository repository;

    /**
     * 加载模板（支持热更新）
     */
    @PostConstruct
    public void loadTemplates() {
        List<FollowUpTemplate> loaded = repository.findAll();
        loaded.forEach(t -> templates.put(t.getInfoType(), t));
    }

    /**
     * 热更新模板
     */
    @EventListener(TemplateUpdateEvent.class)
    public void refreshTemplates() {
        loadTemplates();
    }

    /**
     * 获取模板，不存在则返回默认模板
     */
    public FollowUpTemplate getTemplate(CoreInfoType type) {
        return templates.getOrDefault(type, getDefaultTemplate(type));
    }

    private FollowUpTemplate getDefaultTemplate(CoreInfoType type) {
        return switch (type) {
            case NAME -> FollowUpTemplate.builder()
                .infoType(CoreInfoType.NAME)
                .question("请问怎么称呼您？")
                .formatHint("请输入您的姓名")
                .build();
            case CONTACT -> FollowUpTemplate.builder()
                .infoType(CoreInfoType.CONTACT)
                .question("请提供您的联系方式（手机号或邮箱），以便我们与您确认预约。")
                .formatHint("手机号格式：13800138000，邮箱格式：example@email.com")
                .build();
            case APPOINTMENT_TIME -> FollowUpTemplate.builder()
                .infoType(CoreInfoType.APPOINTMENT_TIME)
                .question("您希望预约什么时间进行咨询？")
                .formatHint("例如：明天下午3点、2024年1月15日上午10点")
                .build();
        };
    }
}
```

---

## 3. 接口设计

### 3.1 CalendarService API

```java
/**
 * 日历服务 REST API
 */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    /**
     * 创建预约
     * POST /api/calendar/appointments
     */
    @PostMapping("/appointments")
    public Result<AppointmentResult> createAppointment(
            @RequestBody @Valid AppointmentRequest request) {
        Appointment appointment = request.toAppointment();
        AppointmentResult result = calendarService.createAppointment(appointment);
        
        if (result.isSuccess()) {
            return Result.success(result);
        } else {
            return Result.error(result.getErrorMessage());
        }
    }

    /**
     * 检查时间段可用性
     * GET /api/calendar/availability?start={start}&end={end}
     */
    @GetMapping("/availability")
    public Result<Boolean> checkAvailability(
            @RequestParam @DateTimeFormat LocalDateTime start,
            @RequestParam @DateTimeFormat LocalDateTime end) {
        return Result.success(calendarService.checkAvailability(start, end));
    }
}
```

### 3.2 追问机制接口

```java
/**
 * 追问服务 API
 */
@RestController
@RequestMapping("/api/follow-up")
public class FollowUpController {

    private final FollowUpService followUpService;
    private final FollowUpTemplateManager templateManager;

    /**
     * 获取追问模板列表
     * GET /api/follow-up/templates
     */
    @GetMapping("/templates")
    public Result<List<FollowUpTemplate>> getTemplates() {
        return Result.success(templateManager.getAllTemplates());
    }

    /**
     * 更新追问模板
     * PUT /api/follow-up/templates/{type}
     */
    @PutMapping("/templates/{type}")
    public Result<FollowUpTemplate> updateTemplate(
            @PathVariable CoreInfoType type,
            @RequestBody @Valid FollowUpTemplate template) {
        return Result.success(templateManager.updateTemplate(type, template));
    }

    /**
     * 获取会话追问状态
     * GET /api/follow-up/state/{chatId}
     */
    @GetMapping("/state/{chatId}")
    public Result<ConsultationState> getState(@PathVariable String chatId) {
        return Result.success(stateService.getState(chatId));
    }
}
```

### 3.3 记忆压缩接口

```java
/**
 * 记忆管理 API
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final ChatMemoryManager memoryManager;
    private final MemoryCompressionService compressionService;

    /**
     * 获取压缩状态
     * GET /api/memory/compression/status/{chatId}
     */
    @GetMapping("/compression/status/{chatId}")
    public Result<CompressionStatus> getCompressionStatus(
            @PathVariable String chatId,
            @RequestParam String agentType) {
        return Result.success(memoryManager.getCompressionStatus(agentType, chatId));
    }

    /**
     * 手动触发压缩
     * POST /api/memory/compression/trigger
     */
    @PostMapping("/compression/trigger")
    public Result<CompressedMemory> triggerCompression(
            @RequestBody @Valid CompressionRequest request) {
        List<Message> messages = memoryManager.getMessages(
            request.getAgentType(), request.getChatId());
        CompressedMemory compressed = compressionService.compress(
            messages, request.getRetainCount()).block();
        return Result.success(compressed);
    }
}
```

---

## 4. 数据模型

### 4.1 Appointment（预约记录）

```java
package com.yupi.yuaiagent.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 预约记录实体
 */
@Entity
@Table(name = "appointments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 预约人姓名
     */
    @Column(nullable = false)
    private String name;

    /**
     * 联系方式
     */
    @Column(nullable = false)
    private String contact;

    /**
     * 联系方式类型
     */
    @Enumerated(EnumType.STRING)
    private ContactType contactType;

    /**
     * 预约时间
     */
    @Column(nullable = false)
    private LocalDateTime appointmentTime;

    /**
     * 咨询主题
     */
    @Column(length = 500)
    private String topic;

    /**
     * 备注
     */
    @Column(length = 1000)
    private String notes;

    /**
     * 日历事件ID
     */
    private String calendarEventId;

    /**
     * 日历链接
     */
    private String calendarUrl;

    /**
     * 日历提供商
     */
    @Enumerated(EnumType.STRING)
    private CalendarProvider provider;

    /**
     * 预约状态
     */
    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;

    /**
     * 会话ID
     */
    private String chatId;

    /**
     * 创建时间
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = AppointmentStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

/**
 * 预约状态枚举
 */
public enum AppointmentStatus {
    PENDING,        // 待确认
    CONFIRMED,      // 已确认
    COMPLETED,      // 已完成
    CANCELLED,      // 已取消
    FAILED          // 创建失败
}

/**
 * 联系方式类型
 */
public enum ContactType {
    PHONE,          // 手机号
    EMAIL           // 邮箱
}

/**
 * 日历提供商
 */
public enum CalendarProvider {
    FEISHU,         // 飞书
    DINGTALK        // 钉钉
}
```

### 4.2 CompressedMemory（压缩记忆）

```java
package com.yupi.yuaiagent.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * 压缩后的对话记忆
 */
@Entity
@Table(name = "compressed_memories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompressedMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 会话ID
     */
    @Column(nullable = false, unique = true)
    private String chatId;

    /**
     * Agent 类型
     */
    @Column(nullable = false)
    private String agentType;

    /**
     * 压缩摘要内容
     */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /**
     * 用户关键需求
     */
    @Column(length = 1000)
    private String keyNeeds;

    /**
     * 已确认的信息
     */
    @Column(length = 1000)
    private String confirmedInfo;

    /**
     * 未解决的问题
     */
    @Column(length = 1000)
    private String unresolvedIssues;

    /**
     * 重要决策
     */
    @Column(length = 1000)
    private String decisions;

    /**
     * 约定事项
     */
    @Column(length = 1000)
    private String agreements;

    /**
     * 原始消息数量
     */
    private int originalMessageCount;

    /**
     * 压缩时间
     */
    private Instant compressedAt;

    /**
     * 压缩版本（同一会话可多次压缩）
     */
    private int version;
}
```

### 4.3 FollowUpQuestion（追问问题）

```java
package com.yupi.yuaiagent.model;

import lombok.*;
import jakarta.persistence.*;

/**
 * 追问问题实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpQuestion {

    /**
     * 问题类型
     */
    private CoreInfoType questionType;

    /**
     * 问题内容
     */
    private String question;

    /**
     * 格式提示
     */
    private String formatHint;

    /**
     * 是否必填
     */
    private boolean isRequired;

    /**
     * 变量上下文（用于模板替换）
     */
    private Map<String, String> context;
}

/**
 * 核心信息类型
 */
public enum CoreInfoType {
    NAME,               // 姓名
    CONTACT,            // 联系方式
    APPOINTMENT_TIME    // 预约时间
}

/**
 * 追问模板
 */
@Entity
@Table(name = "follow_up_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 信息类型
     */
    @Enumerated(EnumType.STRING)
    @Column(unique = true)
    private CoreInfoType infoType;

    /**
     * 问题模板
     */
    @Column(nullable = false, length = 500)
    private String question;

    /**
     * 格式提示
     */
    @Column(length = 500)
    private String formatHint;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
```

### 4.4 ConsultationState（咨询状态）

```java
package com.yupi.yuaiagent.model;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 咨询会话状态
 * 用于追踪追问进度
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationState {

    private String chatId;
    
    // 已收集的核心信息
    private String name;
    private String contact;
    private ContactType contactType;
    private LocalDateTime appointmentTime;
    
    // 非核心信息
    private String topic;
    private String notes;
    
    // 状态标记
    private ConsultationPhase phase;
    private int followUpCount;
    private LocalDateTime lastUpdated;

    public boolean hasName() {
        return name != null && !name.isBlank();
    }

    public boolean hasContact() {
        return contact != null && !contact.isBlank();
    }

    public boolean hasAppointmentTime() {
        return appointmentTime != null;
    }

    public boolean needsFollowUp() {
        return !hasName() || !hasContact() || !hasAppointmentTime();
    }

    public boolean isReadyForConfirmation() {
        return hasName() && hasContact() && hasAppointmentTime() 
            && phase == ConsultationPhase.COLLECTING;
    }

    public boolean isConfirmed() {
        return phase == ConsultationPhase.CONFIRMED;
    }
}

/**
 * 咨询阶段
 */
public enum ConsultationPhase {
    COLLECTING,     // 收集信息中
    CONFIRMING,     // 确认信息中
    CONFIRMED,      // 已确认
    COMPLETED,      // 已完成预约
    FAILED          // 预约失败
}
```

---

## 5. 流程设计

### 5.1 预约咨询流程

```
┌─────────────────────────────────────────────────────────────────┐
│                     预约咨询完整流程                              │
└─────────────────────────────────────────────────────────────────┘

用户发送消息
      │
      ▼
┌─────────────┐
│ 意图识别    │ ──非预约意图──→ 路由到其他 Agent
│ (Orchestrator)│
└─────────────┘
      │ 预约意图
      ▼
┌─────────────┐
│ 路由到      │
│Consultation │
│   Agent     │
└─────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│         解析用户输入                 │
│  (提取姓名/联系方式/时间等信息)       │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│      检查核心信息是否完整             │
└─────────────────────────────────────┘
      │
      ├──不完整──→ 生成追问 ──→ 返回追问给用户
      │                         (等待用户回复)
      │                              │
      │                              └──────┐
      │                                     │
      ▼ 完整                                │
┌─────────────────────────────────────┐    │
│     生成预约信息确认页面              │    │
│  (展示姓名/联系方式/时间)             │    │
└─────────────────────────────────────┘    │
      │                                    │
      ▼                                    │
┌─────────────────────────────────────┐    │
│        用户确认预约信息              │◄───┘
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│     调用 CalendarService            │
│     创建日历事件                     │
└─────────────────────────────────────┘
      │
      ├──成功──→ 返回成功响应（含日历链接）
      │
      ▼ 失败
┌─────────────────────────────────────┐
│   返回错误提示，建议重试或联系客服    │
└─────────────────────────────────────┘
```

### 5.2 记忆压缩流程

```
┌─────────────────────────────────────────────────────────────────┐
│                     记忆压缩流程                                  │
└─────────────────────────────────────────────────────────────────┘

新消息到达
      │
      ▼
┌─────────────────────────────────────┐
│    检查是否满足压缩触发条件          │
│  (Token数 > 4000 或 轮数 > 20)      │
└─────────────────────────────────────┘
      │
      ├──不满足──→ 正常处理消息
      │
      ▼ 满足
┌─────────────────────────────────────┐
│    推送"正在整理对话记忆..."状态     │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│         分割对话历史                 │
│  [待压缩部分] + [保留最近N轮]        │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│      调用 LLM 生成压缩摘要           │
│  (提取关键需求/已确认信息/未解决     │
│   问题/重要决策/约定事项)            │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│    将摘要作为系统消息添加到上下文    │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│    推送"记忆整理完成"状态            │
└─────────────────────────────────────┘
      │
      ▼
    继续对话
```

### 5.3 追问流程详细设计

```
┌─────────────────────────────────────────────────────────────────┐
│                       追问流程                                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 追问优先级队列（核心信息）                                         │
│                                                                   │
│  1. 姓名 (NAME)                                                   │
│  2. 联系方式 (CONTACT)                                            │
│  3. 预约时间 (APPOINTMENT_TIME)                                   │
│                                                                   │
│  ─────────────────────────────────────────────────────────────── │
│  非核心信息（AI 智能追问）                                         │
│                                                                   │
│  4. 咨询主题 (TOPIC)                                              │
│  5. 备注 (NOTES)                                                  │
└─────────────────────────────────────────────────────────────────┘

追问执行流程:

用户: "我想预约咨询"
      │
      ▼
系统检查状态: name=null, contact=null, time=null
      │
      ▼
生成追问 #1 (姓名): "请问怎么称呼您？"
      │
      ├──用户输入有效──→ 提取姓名，进入下一步
      │
      └──用户输入无效──→ "请输入有效的姓名格式"
                        └──→ 重新追问姓名

用户: "我叫张三"
      │
      ▼
系统检查状态: name="张三", contact=null, time=null
      │
      ▼
生成追问 #2 (联系方式): "请提供您的联系方式（手机号或邮箱）"
      │
      ├──格式正确──→ 提取联系方式，进入下一步
      │
      └──格式错误──→ "手机号格式应为11位数字，邮箱格式为xxx@xxx.com"
                    └──→ 重新追问联系方式

用户: "13800138000"
      │
      ▼
系统检查状态: name="张三", contact="13800138000", time=null
      │
      ▼
生成追问 #3 (时间): "您希望预约什么时间进行咨询？"
      │
      ├──时间有效──→ 提取时间{}
└─────────────────────────────────────┐
│   返回错误提示，建议重试或联系客服    │
└─────────────────────────────────────┘
```

### 5.2 记忆压缩流程

```
┌─────────────────────────────────────────────────────────────────┐
│                     记忆压缩流程                                  │
└─────────────────────────────────────────────────────────────────┘

新消息到达
      │
      ▼
┌─────────────────────────────────────┐
│    检查是否满足压缩触发条件          │
│  (Token数 > 4000 或 轮数 > 20)      │
└─────────────────────────────────────┘
      │
      ├──不满足──→ 正常处理消息
      │
      ▼ 满足
┌─────────────────────────────────────┐
│    推送"正在整理对话记忆..."状态     │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│         分割对话历史                 │
│  [待压缩部分] + [保留最近N轮]        │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│      调用 LLM 生成压缩摘要           │
│  (提取关键需求/已确认信息/未解决     │
│   问题/重要决策/约定事项)            │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│    将摘要作为系统消息添加到上下文    │
└─────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│    推送"记忆整理完成"状态            │
└─────────────────────────────────────┘
      │
      ▼
    继续对话
```

### 5.3 追问流程详细设计

```
┌─────────────────────────────────────────────────────────────────┐
│                       追问流程                                    │
└─────────────────────────────────────────────────────────────────┘

追问优先级队列（核心信息）：
  1. 姓名 (NAME)
  2. 联系方式 (CONTACT)  
  3. 预约时间 (APPOINTMENT_TIME)

非核心信息（AI 智能追问）：
  4. 咨询主题 (TOPIC)
  5. 备注 (NOTES)

追问执行流程示例:

用户: "我想预约咨询"
      │
      ▼
系统检查状态: name=null, contact=null, time=null
      │
      ▼
生成追问 #1 (姓名): "请问怎么称呼您？"
      │
      ├──用户输入有效──→ 提取姓名，进入下一步
      │
      └──用户输入无效──→ "请输入有效的姓名格式"
                        └──→ 重新追问姓名

用户: "我叫张三"
      │
      ▼
系统检查状态: name="张三", contact=null, time=null
      │
      ▼
生成追问 #2 (联系方式): "请提供您的联系方式（手机号或邮箱）"
      │
      ├──格式正确──→ 提取联系方式，进入下一步
      │
      └──格式错误──→ "手机号格式应为11位数字，邮箱格式为xxx@xxx.com"
                    └──→ 重新追问联系方式

用户: "13800138000"
      │
      ▼
系统检查状态: name="张三", contact="13800138000", time=null
      │
      ▼
生成追问 #3 (时间): "您希望预约什么时间进行咨询？"
      │
      ├──时间有效──→ 提取时间，进入确认阶段
      │
      └──时间无效──→ "请提供具体的预约时间，如'明天下午3点'"
                    └──→ 重新追问时间

用户: "明天下午3点"
      │
      ▼
系统检查状态: name="张三", contact="13800138000", time=解析后的时间
      │
      ▼
核心信息完整，生成确认页面:
  ┌─────────────────────────────────┐
  │ 请确认您的预约信息：              │
  │                                 │
  │ 姓名：张三                       │
  │ 联系方式：13800138000            │
  │ 预约时间：2024年X月X日 15:00     │
  │                                 │
  │ 请回复"确认"完成预约，            │
  │ 或告诉我需要修改的信息。          │
  └─────────────────────────────────┘

用户: "确认"
      │
      ▼
调用 CalendarService 创建日历事件
      │
      ├──成功──→ 返回成功信息 + 日历链接
      │
      └──失败──→ 返回错误提示 + 建议重试
```

---

## 6. 错误处理设计

### 6.1 异常体系

```java
/**
 * 预约咨询异常基类
 */
public class ConsultationException extends RuntimeException {
    private final ErrorCode errorCode;
    
    public ConsultationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

/**
 * 日历服务异常
 */
public class CalendarServiceException extends ConsultationException {
    public CalendarServiceException(String provider, String message) {
        super(ErrorCode.CALENDAR_ERROR, 
              String.format("日历服务[%s]异常: %s", provider, message));
    }
}

/**
 * 验证异常
 */
public class ValidationException extends ConsultationException {
    public ValidationException(String field, String message) {
        super(ErrorCode.VALIDATION_ERROR,
              String.format("字段[%s]验证失败: %s", field, message));
    }
}

/**
 * 错误码枚举
 */
public enum ErrorCode {
    INTENT_RECOGNITION_FAILED,   // 意图识别失败
    CALENDAR_ERROR,              // 日历服务错误
    VALIDATION_ERROR,            // 验证错误
    COMPRESSION_ERROR,           // 压缩错误
    STATE_NOT_FOUND,             // 会话状态不存在
    MISSING_CORE_INFO            // 缺少核心信息
}
```

### 6.2 全局异常处理

```java
@RestControllerAdvice
public class ConsultationExceptionHandler {

    @ExceptionHandler(CalendarServiceException.class)
    public Result<?> handleCalendarError(CalendarServiceException e) {
        log.error("日历服务异常: {}", e.getMessage(), e);
        return Result.error("预约创建失败，请稍后重试或联系人工客服");
    }

    @ExceptionHandler(ValidationException.class)
    public Result<?> handleValidationError(ValidationException e) {
        log.warn("验证异常: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(ConsultationException.class)
    public Result<?> handleConsultationError(ConsultationException e) {
        log.error("预约咨询异常: {}", e.getMessage(), e);
        return Result.error("系统处理异常，请稍后重试");
    }
}
```

---

## 7. 配置设计

### 7.1 应用配置

```yaml
# application.yml
calendar:
  provider: FEISHU  # FEISHU | DINGTALK
  feishu:
    app-id: ${FEISHU_APP_ID}
    app-secret: ${FEISHU_APP_SECRET}
    calendar-id: ${FEISHU_CALENDAR_ID}
  dingtalk:
    app-key: ${DINGTALK_APP_KEY}
    app-secret: ${DINGTALK_APP_SECRET}
    calendar-id: ${DINGTALK_CALENDAR_ID}

memory:
  compression:
    enabled: true
    token-threshold: 4000
    round-threshold: 20
    retain-rounds: 5
    async: true

follow-up:
  templates:
    hot-reload: true
    refresh-interval: 300000  # 5分钟
```

### 7.2 配置类

```java
@Configuration
@ConfigurationProperties(prefix = "calendar")
@Data
public class CalendarConfig {
    private CalendarProvider provider;
    private FeishuConfig feishu;
    private DingTalkConfig dingtalk;
    
    @Data
    public static class FeishuConfig {
        private String appId;
        private String appSecret;
        private String calendarId;
    }
    
    @Data
    public static class DingTalkConfig {
        private String appKey;
        private String appSecret;
        private String calendarId;
    }
}

@Configuration
@ConfigurationProperties(prefix = "memory.compression")
@Data
public class CompressionConfig {
    private boolean enabled = true;
    private int tokenThreshold = 4000;
    private int roundThreshold = 20;
    private int retainRounds = 5;
    private boolean async = true;
}
```

---

## 8. Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system - essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Consultation Intent Routing

*For any* user message containing consultation-related keywords ("预约", "咨询", "预约咨询"), the OrchestratorAgent SHALL route the message to ConsultationAgent.

**Validates: Requirements 1.1, 1.3, 1.4**

### Property 2: Intent Enum Completeness

*For any* valid AgentIntent value, the CONSULTATION enum constant SHALL exist with agentName "预约咨询专家" and appropriate description.

**Validates: Requirements 1.2**

### Property 3: Calendar Service Provider Selection

*For any* configured CalendarProvider value (FEISHU or DINGTALK), the system SHALL use the corresponding calendar service implementation when creating appointments.

**Validates: Requirements 2.5, 2.6**

### Property 4: Appointment Persistence Round-Trip

*For any* valid Appointment entity, saving it to the database and then retrieving it SHALL produce an equivalent entity with all fields preserved.

**Validates: Requirements 2.7**

### Property 5: Calendar API Error Handling

*For any* calendar API failure scenario, the system SHALL return a user-friendly error message suggesting retry or customer service contact, and log the error details.

**Validates: Requirements 2.4**

### Property 6: Memory Compression Retention

*For any* conversation history with N retainable rounds configured, after compression the system SHALL preserve the exact content of the most recent N rounds unchanged.

**Validates: Requirements 3.2**

### Property 7: Compression Trigger by Token Threshold

*For any* conversation where token count exceeds the configured threshold (default 4000), the system SHALL automatically trigger memory compression.

**Validates: Requirements 4.1**

### Property 8: Compression Trigger by Round Threshold

*For any* conversation where round count exceeds the configured threshold (default 20), the system SHALL automatically trigger memory compression.

**Validates: Requirements 4.2**

### Property 9: Compressed Memory Content Completeness

*For any* conversation that undergoes compression, the resulting CompressedMemory SHALL contain all five required fields: keyNeeds, confirmedInfo, unresolvedIssues, decisions, and agreements.

**Validates: Requirements 4.6**

### Property 10: Follow-Up Trigger on Missing Core Info

*For any* consultation request where core information (name, contact, or appointment time) is missing, the system SHALL trigger the follow-up question flow.

**Validates: Requirements 5.1**

### Property 11: Follow-Up Template Usage

*For any* follow-up question for core information types (NAME, CONTACT, APPOINTMENT_TIME), the system SHALL use the configured template if available, or the default template otherwise.

**Validates: Requirements 5.3, 6.3**

### Property 12: Confirmation After Core Info Complete

*For any* consultation session where all core information has been collected, the system SHALL present a confirmation page to the user before creating the calendar event.

**Validates: Requirements 5.5**

### Property 13: Invalid Input Validation and Retry

*For any* user input that fails validation during follow-up, the system SHALL display the correct format hint and re-ask the question.

**Validates: Requirements 5.6**

### Property 14: Template Placeholder Substitution

*For any* follow-up template containing variable placeholders, the system SHALL correctly substitute placeholders with context values before presenting to the user.

**Validates: Requirements 6.2**

---

## 9. 测试策略

### 9.1 单元测试

| 测试类 | 测试内容 | 测试类型 |
|--------|----------|----------|
| `ConsultationAgentTest` | Agent 核心逻辑、状态转换 | 单元测试 |
| `CalendarServiceTest` | 日历服务接口行为 | 单元测试 (Mock) |
| `FollowUpServiceTest` | 追问逻辑、模板选择 | 单元测试 |
| `MemoryCompressionServiceTest` | 压缩逻辑、触发条件 | 单元测试 |
| `ConsultationStateTest` | 状态判断方法 | 单元测试 |

### 9.2 集成测试

| 测试类 | 测试内容 | 测试类型 |
|--------|----------|----------|
| `CalendarIntegrationTest` | 真实日历 API 调用 | 集成测试 |
| `ConsultationFlowTest` | 完整预约流程 | 集成测试 |
| `MemoryCompressionIntegrationTest` | 真实压缩效果 | 集成测试 |

### 9.3 属性测试配置

```java
@TestPropertySource(properties = {
    "jqwik.testing.enabled=true",
    "jqwik.generation.max-tries=100"
})
public abstract class PropertyTestBase {
    // 基础配置
}
```

---

## 10. 扩展性设计

### 10.1 新增日历提供商

要支持新的日历提供商（如企业微信、Google Calendar）：

1. 实现 `CalendarService` 接口
2. 添加对应的配置类
3. 在 `CalendarProvider` 枚举中添加新类型
4. 使用 `@ConditionalOnProperty` 实现条件装配

### 10.2 新增追问类型

要支持新的追问信息类型：

1. 在 `CoreInfoType` 枚举中添加新类型
2. 添加对应的默认模板
3. 在 `FollowUpService.getNextQuestion()` 中添加处理逻辑

### 10.3 新增压缩策略

要支持新的压缩触发策略：

1. 在 `CompressionTriggerType` 枚举中添加新类型
2. 在 `MemoryCompressionService.needsCompression()` 中添加判断逻辑
