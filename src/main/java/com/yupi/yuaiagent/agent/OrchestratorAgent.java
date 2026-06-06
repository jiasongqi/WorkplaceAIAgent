package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactQuery;
import com.yupi.yuaiagent.artifact.model.ArtifactStatus;
import com.yupi.yuaiagent.calendar.CalendarServiceFactory;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.config.FollowUpTemplateConfig;
import com.yupi.yuaiagent.message.ChatMemoryAdapter;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.quality.*;
import com.yupi.yuaiagent.rag.QueryRewriter;
import com.yupi.yuaiagent.repository.AppointmentRepository;
import com.yupi.yuaiagent.skill.SkillExecutor;
import com.yupi.yuaiagent.skill.SkillRegistry;
import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceRecorder;
import com.yupi.yuaiagent.trace.TraceRepository;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import com.yupi.yuaiagent.validation.InfoValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 主控 Agent（Orchestrator）
 * 根据用户意图智能分发给对应的专业子 Agent，支持真正的 token 级流式输出。
 * 
 * 路由策略：
 * - RESUME：简历优化、面试技巧、求职相关问题 → ResumeAgent
 * - NEGOTIATION：薪资谈判、涨薪、薪酬分析 → NegotiationAgent
 * - ESCAPE：离职、辞职、劳动纠纷 → EscapeAgent
 * - GENERAL：其他职场问题（人际关系、压力、职业规划等）→ GeneralCareerAgent
 * 
 * 注意：YuManus（工具型 Agent）不再通过 Orchestrator 路由，
 * 可通过 /manus/chat 接口单独调用执行具体任务。
 */
@Slf4j
public class OrchestratorAgent {

    // 意图识别提示词
    private static final String INTENT_PROMPT = """
            你是一个职场问题分类器。请分析用户的问题，判断属于以下哪个类别，只输出类别名称，不要有任何其他内容：
            
            - RESUME：涉及简历优化、面试技巧、求职投递、offer 选择、跳槽等求职相关问题
            - NEGOTIATION：涉及薪资谈判、涨薪、薪资包分析、绩效奖金等薪酬相关问题
            - ESCAPE：涉及离职、辞职、被裁员、劳动纠纷、工作交接等离职相关问题
            - CONSULTATION：涉及预约咨询、预约专家、咨询预约、约时间聊聊、请教问题等预约相关问题
            - GENERAL：其他职场问题，如职场人际关系、工作压力、职业规划、职场困惑、情绪问题等
            
            用户问题：{message}
            """;

    private final ChatClient intentClient;
    private final ResumeAgent resumeAgent;
    private final NegotiationAgent negotiationAgent;
    private final EscapeAgent escapeAgent;
    private final GeneralCareerAgent generalCareerAgent;
    private final ConsultationAgent consultationAgent;
    private final SkillExecutor skillExecutor;
    private final SkillRegistry skillRegistry;
    private final ChatMemoryManager chatMemoryManager;
    private final UserProfileService userProfileService;
    private final ArtifactShelf artifactShelf;
    private final TraceRecorder traceRecorder;
    private final TraceRepository traceRepository;
    private final ChatMemoryAdapter chatMemoryAdapter;
    private final QualityGuardAgent qualityGuardAgent;
    private final QualityModeResolver qualityModeResolver;
    private final QualityReviewRepository qualityReviewRepository;

    /**
     * 构造函数 - 使用 ChatMemoryManager
     */
    public OrchestratorAgent(ChatModel chatModel, VectorStore vectorStore,
                             ToolCallback[] tools, QueryRewriter queryRewriter,
                             ChatMemoryManager chatMemoryManager,
                             FollowUpTemplateConfig templateConfig,
                             InfoValidator infoValidator,
                             CalendarServiceFactory calendarServiceFactory,
                             AppointmentRepository appointmentRepository,
                             SkillExecutor skillExecutor,
                             SkillRegistry skillRegistry,
                             UserProfileService userProfileService,
                             ArtifactShelf artifactShelf,
                             TraceRecorder traceRecorder,
                             TraceRepository traceRepository,
                             ChatMemoryAdapter chatMemoryAdapter,
                             QualityGuardAgent qualityGuardAgent,
                             QualityModeResolver qualityModeResolver,
                             QualityReviewRepository qualityReviewRepository) {
        this.intentClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        
        // 创建各专业 Agent
        this.resumeAgent = new ResumeAgent(chatModel, vectorStore, queryRewriter, chatMemoryManager);
        this.negotiationAgent = new NegotiationAgent(chatModel, tools, queryRewriter, chatMemoryManager);
        this.escapeAgent = new EscapeAgent(chatModel, tools, queryRewriter, chatMemoryManager);
        this.generalCareerAgent = new GeneralCareerAgent(chatModel, chatMemoryManager);
        this.consultationAgent = new ConsultationAgent(chatModel, chatMemoryManager, templateConfig, infoValidator, calendarServiceFactory, appointmentRepository);
        this.skillExecutor = skillExecutor;
        this.skillRegistry = skillRegistry;
        this.chatMemoryManager = chatMemoryManager;
        this.userProfileService = userProfileService;
        this.artifactShelf = artifactShelf;
        this.traceRecorder = traceRecorder;
        this.traceRepository = traceRepository;
        this.chatMemoryAdapter = chatMemoryAdapter;
        this.qualityGuardAgent = qualityGuardAgent;
        this.qualityModeResolver = qualityModeResolver;
        this.qualityReviewRepository = qualityReviewRepository;
        
        log.info("OrchestratorAgent 初始化完成，已创建 5 个专业 Agent，已加载 {} 个技能", skillRegistry.size());
    }

    /**
     * 识别用户意图（使用枚举统一处理）
     */
    private AgentIntent detectIntent(String message) {
        String rawIntent = intentClient.prompt()
                .user(INTENT_PROMPT.replace("{message}", message))
                .call()
                .content();
        
        AgentIntent intent = AgentIntent.fromRawIntent(rawIntent);
        log.info("意图识别结果：{}（原始输出：{}）", intent, rawIntent);
        return intent;
    }

    /**
     * 根据意图路由到对应子 Agent（同步）
     */
    public String chat(String message, String chatId) {
        // 1. 先尝试技能匹配
        String skillResult = skillExecutor.executeSmart(message, chatId);
        if (skillResult != null) {
            log.info("匹配到技能，使用技能回答");
            return skillResult;
        }
        
        // 2. 原有意图路由逻辑
        AgentIntent intent = detectIntent(message);
        return switch (intent) {
            case RESUME -> {
                log.info("路由到 ResumeAgent");
                yield resumeAgent.chat(message, chatId);
            }
            case NEGOTIATION -> {
                log.info("路由到 NegotiationAgent");
                yield negotiationAgent.chat(message, chatId);
            }
            case ESCAPE -> {
                log.info("路由到 EscapeAgent");
                yield escapeAgent.chat(message, chatId);
            }
            case CONSULTATION -> {
                log.info("路由到 ConsultationAgent");
                yield consultationAgent.chat(message, chatId);
            }
            default -> {
                log.info("路由到 GeneralCareerAgent");
                yield generalCareerAgent.chat(message, chatId);
            }
        };
    }

    /**
     * 根据意图路由到对应子 Agent（SSE 真正流式）。
     */
    public SseEmitter chatStream(String message, String chatId) {
        return chatStream(message, chatId, null, null);
    }

    /**
     * 根据意图路由到对应子 Agent（SSE 真正流式），支持用户画像注入与对话结束触发。
     */
    public SseEmitter chatStream(String message, String chatId, String userId) {
        return chatStream(message, chatId, userId, null);
    }

    /**
     * 根据意图路由到对应子 Agent（SSE 真正流式），支持用户画像注入、对话结束触发与执行轨迹。
     *
     * @param message   用户消息
     * @param chatId    会话 ID
     * @param userId    用户 ID（可为 null）
     * @param requestId HTTP 请求 ID（用于关联执行轨迹，可为 null）
     */
    public SseEmitter chatStream(String message, String chatId, String userId, String requestId) {
        SseEmitter emitter = new SseEmitter(300000L);

        // Create trace context (Req 8.2)
        TraceContext traceCtx = traceRecorder.startTrace(userId, chatId,
                requestId != null ? requestId : UUID.randomUUID().toString());
        // Bind SSE emitter for real-time trace events (Req 10.1)
        traceCtx.bindSseEmitter(emitter);

        CompletableFuture.runAsync(() -> {
            try {
                // 1. 先尝试技能匹配
                TraceSpan skillSpan = traceRecorder.startSpan(traceCtx, TraceStepType.SKILL_MATCH, "技能匹配");
                Flux<String> skillFlux = skillExecutor.executeStream(message, chatId, null);
                
                // 收集技能结果判断是否有匹配
                StringBuilder skillResult = new StringBuilder();
                skillFlux
                    .doOnNext(skillResult::append)
                    .doOnComplete(() -> {
                        try {
                            if (skillResult.length() > 0 && !skillResult.toString().startsWith("未找到技能")) {
                                // 技能匹配成功
                                traceRecorder.endSpan(traceCtx, skillSpan);
                                traceRecorder.endTrace(traceCtx);
                                persistTrace(traceCtx);

                                emitter.send(SseEmitter.event()
                                        .name("routing")
                                        .data("[技能匹配]"));
                                emitter.send(SseEmitter.event().name("message").data(skillResult.toString()));
                                emitter.complete();
                            } else {
                                // 2. 技能未匹配，走原有路由逻辑
                                traceRecorder.skipSpan(traceCtx, skillSpan);
                                routeToAgent(message, chatId, userId, emitter, traceCtx);
                            }
                        } catch (IOException e) {
                            traceRecorder.failTrace(traceCtx);
                            persistTrace(traceCtx);
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(e -> {
                        log.error("技能执行出错，降级到原有路由", e);
                        traceRecorder.failSpan(traceCtx, skillSpan, e.getMessage());
                        try {
                            routeToAgent(message, chatId, userId, emitter, traceCtx);
                        } catch (Exception ex) {
                            traceRecorder.failTrace(traceCtx);
                            persistTrace(traceCtx);
                            emitter.completeWithError(ex);
                        }
                    })
                    .subscribe();
                    
            } catch (Exception e) {
                log.error("OrchestratorAgent 执行出错", e);
                traceRecorder.failTrace(traceCtx);
                persistTrace(traceCtx);
                try {
                    emitter.send(SseEmitter.event().name("error").data("执行出错：" + e.getMessage()));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    /**
     * 路由到专业 Agent，支持画像注入与对话结束触发。
     */
    private void routeToAgent(String message, String chatId, String userId,
                              SseEmitter emitter, TraceContext traceCtx) throws IOException {
        // INTENT_DETECTION span (Req 8.3)
        TraceSpan intentSpan = traceRecorder.startSpan(traceCtx, TraceStepType.INTENT_DETECTION, "意图识别");
        AgentIntent intent = detectIntent(message);
        traceRecorder.putMetadata(intentSpan, "intent", intent.name());
        traceRecorder.endSpan(traceCtx, intentSpan);
        log.info("意图识别结果：{}，路由到：{}", intent, intent.getAgentName());

        // ROUTING span (Req 8.3)
        TraceSpan routingSpan = traceRecorder.startSpan(traceCtx, TraceStepType.ROUTING, "路由到" + intent.getAgentName());
        emitter.send(SseEmitter.event()
                .name("routing")
                .data("[路由到" + intent.getAgentName() + "]"));
        traceRecorder.putMetadata(routingSpan, "targetAgent", intent.name());
        traceRecorder.endSpan(traceCtx, routingSpan);

        // MEMORY_COMPRESSION span (Req 8.6)
        TraceSpan memorySpan = traceRecorder.startSpan(traceCtx, TraceStepType.MEMORY_COMPRESSION, "记忆压缩检查");
        String memoryType = memoryTypeOf(intent);
        chatMemoryManager.autoCompressIfNeeded(memoryType, chatId, traceCtx, status -> {
            try {
                emitter.send(SseEmitter.event().name("status").data(status));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        traceRecorder.endSpan(traceCtx, memorySpan);

        // PROFILE_INJECTION span (Req 8.3)
        TraceSpan profileSpan = traceRecorder.startSpan(traceCtx, TraceStepType.PROFILE_INJECTION, "画像注入");
        String profileInjection = StringUtils.hasText(userId)
                ? userProfileService.buildPromptInjection(userId)
                : "";
        boolean hasProfile = StringUtils.hasText(profileInjection);
        traceRecorder.putMetadata(profileSpan, "hasProfile", String.valueOf(hasProfile));
        traceRecorder.endSpan(traceCtx, profileSpan);

        // ARTIFACT_QUERY span (Req 8.3)
        TraceSpan artifactQuerySpan = traceRecorder.startSpan(traceCtx, TraceStepType.ARTIFACT_QUERY, "交付物查询");
        List<Artifact> readyArtifacts = queryReadyArtifacts(userId, chatId);
        String artifactContext = buildArtifactContext(readyArtifacts);
        traceRecorder.putMetadata(artifactQuerySpan, "foundCount", String.valueOf(readyArtifacts.size()));
        traceRecorder.endSpan(traceCtx, artifactQuerySpan);

        // Merge injection
        String combinedInjection = mergeInjection(profileInjection, artifactContext);

        // SUB_AGENT_EXECUTION span (Req 8.3)
        TraceSpan subAgentSpan = traceRecorder.startSpan(traceCtx, TraceStepType.SUB_AGENT_EXECUTION,
                intent.getAgentName() + "执行");
        traceRecorder.putMetadata(subAgentSpan, "agentType", memoryType);

        Flux<String> tokenFlux = switch (intent) {
            case RESUME -> resumeAgent.chatStream(message, chatId, combinedInjection);
            case NEGOTIATION -> negotiationAgent.chatStream(message, chatId, combinedInjection);
            case ESCAPE -> escapeAgent.chatStream(message, chatId, combinedInjection);
            case CONSULTATION -> consultationAgent.chatStream(message, chatId, combinedInjection);
            default -> generalCareerAgent.chatStream(message, chatId, combinedInjection);
        };

        // ARTIFACT_CONSUME span (Req 8.3)
        TraceSpan consumeSpan = traceRecorder.startSpan(traceCtx, TraceStepType.ARTIFACT_CONSUME, "交付物消费");
        markArtifactsConsumed(readyArtifacts);
        traceRecorder.putMetadata(consumeSpan, "consumedCount", String.valueOf(readyArtifacts.size()));
        traceRecorder.endSpan(traceCtx, consumeSpan);

        // Collect full answer for quality review
        StringBuilder answerCollector = new StringBuilder();

        tokenFlux
                .doOnNext(token -> {
                    try {
                        answerCollector.append(token);
                        emitter.send(SseEmitter.event().name("message").data(token));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .doOnError(e -> {
                    log.error("子 Agent 流式输出出错", e);
                    traceRecorder.failSpan(traceCtx, subAgentSpan, e.getMessage());
                    traceRecorder.failTrace(traceCtx);
                    traceCtx.markSseClosed();
                    persistTrace(traceCtx);
                    try {
                        emitter.send(SseEmitter.event().name("error").data("执行出错：" + e.getMessage()));
                        emitter.complete();
                    } catch (IOException ex) {
                        emitter.completeWithError(ex);
                    }
                })
                .doOnComplete(() -> {
                    traceRecorder.endSpan(traceCtx, subAgentSpan);

                    // Quality Guard review
                    String fullAnswer = answerCollector.toString();
                    runQualityReview(message, fullAnswer, chatId, intent, traceCtx, emitter);

                    // Persist messages to PersistentMessageRepository (Source of Truth)
                    try {
                        chatMemoryAdapter.addUserMessage(chatId, message);
                        chatMemoryAdapter.addAssistantMessage(chatId, fullAnswer);
                    } catch (Exception e) {
                        log.error("Failed to persist messages to PersistentMessageRepository", e);
                    }

                    traceRecorder.endTrace(traceCtx);
                    traceCtx.markSseClosed();
                    persistTrace(traceCtx);
                    try {
                        emitter.complete();
                    } catch (Exception ex) {
                        log.debug("Emitter already completed", ex);
                    }
                    // 对话结束：异步更新用户画像
                    triggerProfileUpdate(userId, intent, chatId, traceCtx);
                })
                .subscribe();
    }

    /**
     * 按 userId + chatId 查询货架中状态为 READY 的相关交付物。
     */
    private List<Artifact> queryReadyArtifacts(String userId, String chatId) {
        if (!StringUtils.hasText(userId) && !StringUtils.hasText(chatId)) {
            return List.of();
        }
        try {
            return artifactShelf.query(ArtifactQuery.builder()
                    .userId(StringUtils.hasText(userId) ? userId : null)
                    .chatId(StringUtils.hasText(chatId) ? chatId : null)
                    .status(ArtifactStatus.READY)
                    .build());
        } catch (Exception e) {
            log.error("查询 READY 交付物失败，降级为不注入交付物，userId={}, chatId={}", userId, chatId, e);
            return List.of();
        }
    }

    /**
     * 将查询到的 READY 交付物拼接为中文上下文文本。
     */
    private String buildArtifactContext(List<Artifact> readyArtifacts) {
        if (readyArtifacts == null || readyArtifacts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【参考交付物】以下是与本次对话相关的已就绪交付物，请在回答时参考：\n");
        for (Artifact artifact : readyArtifacts) {
            String title = StringUtils.hasText(artifact.getTitle()) ? artifact.getTitle() : "（无标题）";
            String content = artifact.getContent() != null ? artifact.getContent() : "";
            sb.append("- ").append(title).append("：").append(content).append("\n");
        }
        log.info("注入 {} 个 READY 交付物到子 Agent 上下文", readyArtifacts.size());
        return sb.toString();
    }

    /**
     * 将被注入（取用）的交付物逐个标记为 CONSUMED。
     */
    private void markArtifactsConsumed(List<Artifact> consumedArtifacts) {
        if (consumedArtifacts == null || consumedArtifacts.isEmpty()) {
            return;
        }
        for (Artifact artifact : consumedArtifacts) {
            String artifactId = artifact.getArtifactId();
            if (!StringUtils.hasText(artifactId)) {
                continue;
            }
            try {
                boolean marked = artifactShelf.markConsumed(artifactId);
                if (marked) {
                    log.info("交付物已标记为 CONSUMED，artifactId={}", artifactId);
                } else {
                    log.warn("标记交付物消费失败（可能已不存在），artifactId={}", artifactId);
                }
            } catch (Exception e) {
                log.error("标记交付物消费异常，artifactId={}", artifactId, e);
            }
        }
    }

    /**
     * 合并画像注入片段与交付物上下文片段。
     */
    private String mergeInjection(String profileInjection, String artifactContext) {
        boolean hasProfile = StringUtils.hasText(profileInjection);
        boolean hasArtifact = StringUtils.hasText(artifactContext);
        if (hasProfile && hasArtifact) {
            return profileInjection + "\n" + artifactContext;
        }
        if (hasProfile) {
            return profileInjection;
        }
        if (hasArtifact) {
            return artifactContext;
        }
        return "";
    }

    /**
     * Runs quality review after agent answer is complete.
     * Sends quality-review SSE event. Blocks answer if CRITICAL risk.
     */
    private void runQualityReview(String userQuestion, String agentAnswer, String chatId,
                                   AgentIntent intent, TraceContext traceCtx, SseEmitter emitter) {
        if (agentAnswer == null || agentAnswer.isBlank()) {
            return;
        }

        // Resolve quality mode (AUTO)
        QualityMode mode = qualityModeResolver.resolve(userQuestion, intent, QualityMode.AUTO);
        if (mode == QualityMode.OFF) {
            return;
        }

        TraceSpan reviewSpan = traceRecorder.startSpan(traceCtx, TraceStepType.QUALITY_REVIEW, "质量审查");
        try {
            QualityReview review;
            if (mode == QualityMode.RED_TEAM) {
                review = qualityGuardAgent.redTeamReview(userQuestion, agentAnswer, chatId);
            } else {
                review = qualityGuardAgent.review(userQuestion, agentAnswer, chatId);
            }

            // Record trace metadata
            traceRecorder.putMetadata(reviewSpan, "overallScore", String.valueOf(review.getOverallScore()));
            traceRecorder.putMetadata(reviewSpan, "riskLevel", review.getRiskLevel().name());
            traceRecorder.putMetadata(reviewSpan, "mode", mode.name());
            traceRecorder.endSpan(traceCtx, reviewSpan);

            // Persist HIGH/CRITICAL reviews
            qualityReviewRepository.saveIfHighRisk(review);

            // Send quality-review SSE event
            try {
                emitter.send(SseEmitter.event().name("quality-review").data(review));
            } catch (IOException e) {
                log.debug("Failed to send quality-review SSE event", e);
            }

            // Block if CRITICAL risk
            if (review.getRiskLevel().isBlocking()) {
                TraceSpan blockedSpan = traceRecorder.startSpan(traceCtx, TraceStepType.QUALITY_BLOCKED, "质量阻断");
                traceRecorder.putMetadata(blockedSpan, "reason", review.getSummary());
                traceRecorder.endSpan(traceCtx, blockedSpan);

                try {
                    emitter.send(SseEmitter.event().name("quality-blocked").data(
                            "⚠️ 该回答已被质量守卫阻断。风险原因：" + review.getSummary() + "。建议咨询相关领域的专业人士。"));
                } catch (IOException e) {
                    log.debug("Failed to send quality-blocked SSE event", e);
                }
            }

            log.info("[QualityGuard] mode={}, overall={}, risk={}", mode, review.getOverallScore(), review.getRiskLevel());

        } catch (Exception e) {
            log.error("Quality review failed, continuing normally", e);
            traceRecorder.failSpan(traceCtx, reviewSpan, e.getMessage());
        }
    }

    /**
     * 对话结束后异步触发画像更新（Req 8.4 — async tail step）。
     */
    private void triggerProfileUpdate(String userId, AgentIntent intent, String chatId, TraceContext traceCtx) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        TraceSpan profileUpdateSpan = traceRecorder.startSpan(traceCtx, TraceStepType.PROFILE_UPDATE, "画像异步更新");
        try {
            ChatMemory memory = chatMemoryManager.getMemory(memoryTypeOf(intent));
            List<Message> conversation = memory.get(chatId);
            userProfileService.updateAsync(userId, conversation);
            traceRecorder.endSpan(traceCtx, profileUpdateSpan);
        } catch (Exception e) {
            log.error("对话结束触发画像更新失败，userId={}, chatId={}", userId, chatId, e);
            traceRecorder.failSpan(traceCtx, profileUpdateSpan, e.getMessage());
        }
    }

    /**
     * 将路由意图映射为子 Agent 在 ChatMemoryManager 中使用的记忆类型 key。
     */
    private String memoryTypeOf(AgentIntent intent) {
        return switch (intent) {
            case RESUME -> "resume";
            case NEGOTIATION -> "negotiation";
            case ESCAPE -> "escape";
            case CONSULTATION -> "consultation";
            default -> "general";
        };
    }

    /**
     * Persists the trace to the repository (fail-safe).
     */
    private void persistTrace(TraceContext traceCtx) {
        try {
            traceRepository.save(traceCtx.getTrace());
            String userId = traceCtx.getTrace().getUserId();
            if (StringUtils.hasText(userId)) {
                traceRepository.enforceRetentionPolicy(userId);
            }
        } catch (Exception e) {
            log.error("[trace] failed to persist trace", e);
        }
    }
}
