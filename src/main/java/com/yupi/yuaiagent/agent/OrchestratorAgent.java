package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.memory.MemoryCoordinator;
import com.yupi.yuaiagent.message.ChatMemoryAdapter;
import com.yupi.yuaiagent.message.MessageSource;
import com.yupi.yuaiagent.skill.SkillExecutor;
import com.yupi.yuaiagent.skill.SkillRegistry;
import com.yupi.yuaiagent.nlu.NluPipeline;
import com.yupi.yuaiagent.nlu.RouteHint;
import com.yupi.yuaiagent.context.ConversationContextBuilder;
import com.yupi.yuaiagent.workflow.WorkflowMatcher;
import com.yupi.yuaiagent.workflow.WorkflowRegistry;
import com.yupi.yuaiagent.agent.runner.ResumeAgentRunner;
import com.yupi.yuaiagent.agent.runner.NegotiationAgentRunner;
import com.yupi.yuaiagent.agent.runner.EscapeAgentRunner;
import com.yupi.yuaiagent.agent.runner.GeneralCareerAgentRunner;
import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceRecorder;
import com.yupi.yuaiagent.trace.TraceRepository;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import com.yupi.yuaiagent.access.AccessDecisionService;
import com.yupi.yuaiagent.suggestion.SuggestedActions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 主控 Agent（Orchestrator）
 * <ul>
 *   <li>单意图：子 Agent 流式生成，剥离机器引用后分块 SSE</li>
 *   <li>多意图：并行辩论 → LLM/规则综合后再推送（非逐 token）</li>
 * </ul>
 * workflow.dag.enabled=true 时，JOB_CHANGE / INTERVIEW 走 DAG 就绪队列执行；
 * 关闭或未命中模板时仍走单意图 SSE / 多意图并行辩论。
 *
 * 路由：RESUME / NEGOTIATION / ESCAPE / CONSULTATION / GENERAL；
 * DATA_QUERY 映射为 GENERAL + 诚实说明（未接业务数据源）。
 * YuManus 走独立 /manus/chat。
 */
@Slf4j
public class OrchestratorAgent {

    private final NluPipeline nluPipeline;
    private final DataQueryRouter dataQueryRouter;
    private final WorkflowMatcher workflowMatcher;
    private final WorkflowRegistry workflowRegistry;
    private final ConversationContextBuilder contextBuilder;
    private final TaskExecutor taskExecutor;
    private final ResultAggregator resultAggregator;
    private final com.yupi.yuaiagent.workflow.dag.DagCompiler dagCompiler;
    private final com.yupi.yuaiagent.workflow.dag.DagWorkflowExecutor dagWorkflowExecutor;
    private final boolean workflowDagEnabled;

    /** DATA_QUERY 未接真实数据源时注入 GENERAL 的说明 */
    static final String DATA_QUERY_FALLBACK_NOTE = """
            【数据查询说明】用户想查数据/报表。当前未接入业务数据源，请勿编造数字。
            请以职场顾问方式给出替代建议：手工统计、问数口径、仪表盘建设步骤。
            """;

    /** DIGITAL_EMPLOYEE 创建/管理引导 */
    static final String DIGITAL_EMPLOYEE_NOTE = """
            【数字员工助手】用户在创建、管理或委托数字员工。请：
            1) 说明可从模板一键创建（简历专员、谈薪顾问、离职规划专员、通用顾问）
            2) 引导用户打开「我的数字员工」完成创建、设为当前、改人设、回滚
            3) 若上下文已有当前委托数字员工，按其人设专精回答
            """;
    private final ResumeAgent resumeAgent;
    private final NegotiationAgent negotiationAgent;
    private final EscapeAgent escapeAgent;
    private final GeneralCareerAgent generalCareerAgent;
    private final ConsultationAgent consultationAgent;
    private final SkillExecutor skillExecutor;
    private final SkillRegistry skillRegistry;
    private final ChatMemoryManager chatMemoryManager;
    private final MemoryCoordinator memoryCoordinator; // nullable if memory.coordinator.enabled=false
    private final TraceRecorder traceRecorder;
    private final TraceRepository traceRepository;
    private final ChatMemoryAdapter chatMemoryAdapter;
    private final QualityReviewHandler qualityReviewHandler;
    private final ContextInjectionService contextInjectionService;
    private final com.yupi.yuaiagent.message.PersistentMessageRepository messageRepository;
    private final AccessDecisionService accessDecisionService;
    private final com.yupi.yuaiagent.guard.PromptInjectionDetector promptInjectionDetector;
    private final Executor agentExecutor;
    private final com.yupi.yuaiagent.agent.collaboration.AgentCollaborationCoordinator collaborationCoordinator;
    private final com.yupi.yuaiagent.service.DigitalEmployeeAppService digitalEmployeeAppService;
    private final com.yupi.yuaiagent.artifact.ArtifactShelf artifactShelf;
    private final com.yupi.yuaiagent.artifact.adoption.ArtifactAdoptionService artifactAdoptionService;
    private final com.yupi.yuaiagent.artifact.adoption.ArtifactCitationExtractor artifactCitationExtractor =
            new com.yupi.yuaiagent.artifact.adoption.ArtifactCitationExtractor();
    private final com.yupi.yuaiagent.agent.data.DataAnalystAgent dataAnalystAgent;
    private final com.yupi.yuaiagent.agent.data.CareerCoachAgent careerCoachAgent;
    private final com.yupi.yuaiagent.agent.data.ProfileCuratorAgent profileCuratorAgent;
    private final com.yupi.yuaiagent.agent.data.PromotionPlannerAgent promotionPlannerAgent;
    private final com.yupi.yuaiagent.agent.data.LearningResourceRecommenderAgent learningResourceRecommenderAgent;
    private final com.yupi.yuaiagent.service.ExpertPackAppService expertPackAppService;
    private final com.yupi.yuaiagent.sessionstate.SessionSharedStateService sessionSharedStateService;
    private final com.yupi.yuaiagent.hitl.HumanHandoffService humanHandoffService;
    private final com.yupi.yuaiagent.agent.manifest.AgentManifestRegistry agentManifestRegistry;
    private final com.yupi.yuaiagent.auth.UserQuotaService userQuotaService;
    /** chatId → last specialist memory type (for cross-agent sync + handoff). */
    private final java.util.concurrent.ConcurrentHashMap<String, String> lastAgentMemoryByChat =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final RouteHintHolder routeHintHolder = new RouteHintHolder();

    /** Mutable holder so ExpertInvoker can see the latest RouteHint for DATA_QUERY. */
    private static final class RouteHintHolder {
        volatile RouteHint hint;
    }

    /**
     * Constructor — uses aggregated {@link OrchestratorDependencies} to reduce parameter count.
     */
    public OrchestratorAgent(OrchestratorDependencies deps) {

        // 创建各专业 Agent
        this.resumeAgent = new ResumeAgent(deps.chatModel(), deps.pipelineRagAdvisorFactory(), deps.queryRewriter(), deps.chatMemoryManager());
        this.negotiationAgent = new NegotiationAgent(deps.chatModel(), deps.tools(), deps.queryRewriter(), deps.chatMemoryManager());
        this.escapeAgent = new EscapeAgent(deps.chatModel(), deps.tools(), deps.queryRewriter(), deps.chatMemoryManager());
        this.generalCareerAgent = new GeneralCareerAgent(deps.chatModel(), deps.chatMemoryManager());
        this.consultationAgent = new ConsultationAgent(deps.chatModel(), deps.chatMemoryManager(), deps.templateConfig(), deps.infoValidator(), deps.calendarServiceFactory(), deps.appointmentRepository(), deps.humanApprovalService(), deps.sessionSharedStateService());
        this.skillExecutor = deps.skillExecutor();
        this.skillRegistry = deps.skillRegistry();
        this.chatMemoryManager = deps.chatMemoryManager();
        this.memoryCoordinator = deps.memoryCoordinator();
        this.traceRecorder = deps.traceRecorder();
        this.traceRepository = deps.traceRepository();
        this.chatMemoryAdapter = deps.chatMemoryAdapter();
        this.qualityReviewHandler = new QualityReviewHandler(deps.qualityGuardAgent(), deps.qualityModeResolver(), deps.qualityReviewRepository(), deps.traceRecorder());
        this.contextInjectionService = new ContextInjectionService(
                deps.userProfileService(), deps.artifactShelf(), deps.messageRepository(),
                deps.chatMemoryManager(), deps.traceRecorder(), deps.reflexionService(),
                deps.userCompanionService(), deps.digitalEmployeeAppService(),
                deps.sessionSharedStateService(), deps.artifactRecallService(),
                deps.perceptionHybridContextService());
        this.messageRepository = deps.messageRepository();
        this.nluPipeline = deps.nluPipeline();
        this.dataQueryRouter = deps.dataQueryRouter();
        this.workflowMatcher = deps.workflowMatcher();
        this.workflowRegistry = deps.workflowRegistry();
        this.contextBuilder = deps.contextBuilder();
        this.taskExecutor = deps.taskExecutor();
        this.resultAggregator = deps.resultAggregator();
        this.dagCompiler = deps.dagCompiler();
        this.dagWorkflowExecutor = deps.dagWorkflowExecutor();
        this.workflowDagEnabled = deps.workflowDagEnabled();
        this.accessDecisionService = deps.accessDecisionService();
        this.promptInjectionDetector = deps.promptInjectionDetector();
        this.agentExecutor = deps.agentExecutor();
        this.collaborationCoordinator = deps.collaborationCoordinator();
        this.digitalEmployeeAppService = deps.digitalEmployeeAppService();
        this.artifactShelf = deps.artifactShelf();
        this.artifactAdoptionService = deps.artifactAdoptionService();
        this.dataAnalystAgent = deps.dataAnalystAgent();
        this.careerCoachAgent = deps.careerCoachAgent();
        this.profileCuratorAgent = deps.profileCuratorAgent();
        this.promotionPlannerAgent = deps.promotionPlannerAgent();
        this.learningResourceRecommenderAgent = deps.learningResourceRecommenderAgent();
        this.expertPackAppService = deps.expertPackAppService();
        this.sessionSharedStateService = deps.sessionSharedStateService();
        this.humanHandoffService = deps.humanHandoffService();
        this.agentManifestRegistry = deps.agentManifestRegistry();
        this.userQuotaService = deps.userQuotaService();

        // Register AgentRunner map on TaskExecutor + DAG executor
        var runners = java.util.Map.<String, AgentRunner>of(
            "RESUME", new ResumeAgentRunner(this.resumeAgent),
            "NEGOTIATION", new NegotiationAgentRunner(this.negotiationAgent),
            "ESCAPE", new EscapeAgentRunner(this.escapeAgent),
            "GENERAL", new GeneralCareerAgentRunner(this.generalCareerAgent)
        );
        taskExecutor.setAgentRunners(runners);
        dagWorkflowExecutor.setAgentRunners(runners);

        log.info("OrchestratorAgent 初始化完成，已创建 5 个专业 Agent，已加载 {} 个技能，workflow.dag.enabled={}",
                skillRegistry.size(), workflowDagEnabled);
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

        // 2. NLU Pipeline 意图识别
        NluPipeline.NluResult nluResult = nluPipeline.process(message, chatId);
        if (nluResult.isNeedsClarification()) {
            return nluResult.getClarification();
        }
        AgentIntent intent = nluResult.toAgentIntent();
        if (intent == AgentIntent.DATA_QUERY) {
            return generalCareerAgent.chat(message, chatId, DATA_QUERY_FALLBACK_NOTE);
        }
        if (intent == AgentIntent.DIGITAL_EMPLOYEE) {
            return generalCareerAgent.chat(message, chatId, DIGITAL_EMPLOYEE_NOTE);
        }
        return switch (intent) {
            case RESUME -> resumeAgent.chat(message, chatId);
            case NEGOTIATION -> negotiationAgent.chat(message, chatId);
            case ESCAPE -> escapeAgent.chat(message, chatId);
            case CONSULTATION -> consultationAgent.chat(message, chatId);
            default -> generalCareerAgent.chat(message, chatId);
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

        // SSE disconnect handling — clean up resources when client disconnects
        final TraceContext finalTraceCtx = traceCtx;

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                // 0. Prompt Injection detection
                var injectionResult = promptInjectionDetector.detect(message);
                if (injectionResult.safe() == false) {
                    log.warn("[Orchestrator] Prompt injection blocked: type={}, pattern={}",
                            injectionResult.type(), injectionResult.pattern());
                    emitter.send(SseEmitter.event().name("error")
                            .data("检测到不安全的输入内容，请重新提问。"));
                    traceRecorder.failTrace(traceCtx);
                    persistTrace(traceCtx);
                    emitter.complete();
                    return;
                }

                // 1. 技能匹配：先规则命中（0 LLM），未命中立刻走 Agent 路由
                // 旧实现把 userMessage 当成 skillName 查，虽多数会 miss，但语义错误且易误伤
                TraceSpan skillSpan = traceRecorder.startSpan(traceCtx, TraceStepType.SKILL_MATCH, "技能匹配");
                emitter.send(SseEmitter.event().name("routing").data("[正在理解你的问题...]"));

                List<com.yupi.yuaiagent.skill.SkillDefinition> matchedSkills =
                        skillRegistry.findByIntent(message,
                                expertPackAppService != null
                                        ? expertPackAppService.getEnabledSkillNames(userId)
                                        : null);
                // 技能匹配过宽时不要抢占主路由：仅在强意图词命中时走技能 LLM
                boolean strongSkillHit = !matchedSkills.isEmpty()
                        && isStrongSkillTrigger(message, matchedSkills.get(0));
                if (strongSkillHit) {
                    var skill = matchedSkills.get(0);
                    log.info("技能规则命中: {}", skill.getName());
                    Flux<String> skillFlux = skillExecutor.executeStream(skill.getName(), message, null);
                    StringBuilder skillResult = new StringBuilder();
                    skillFlux
                        .doOnNext(skillResult::append)
                        .doOnComplete(() -> {
                            try {
                                if (skillResult.length() > 0 && !skillResult.toString().startsWith("未找到技能")) {
                                    traceRecorder.endSpan(traceCtx, skillSpan);
                                    traceRecorder.endTrace(traceCtx);
                                    persistTrace(traceCtx);

                                    chatMemoryAdapter.addUserMessage(chatId, message, MessageSource.USER, null, null);
                                    var skillMsg = chatMemoryAdapter.startAssistantStream(
                                            chatId, MessageSource.AGENT, "SKILL", "技能");
                                    emitter.send(SseEmitter.event().name("message-start")
                                            .data("{\"assistantMessageId\":\"" + skillMsg.getMessageId() + "\"}"));
                                    emitter.send(SseEmitter.event()
                                            .name("routing")
                                            .data("[技能匹配: " + skill.getName() + "]"));
                                    emitter.send(SseEmitter.event().name("message").data(skillResult.toString()));
                                    chatMemoryAdapter.completeAssistant(skillMsg.getMessageId(), skillResult.toString());
                                    emitSuggestedActions(emitter, AgentIntent.GENERAL, true);
                                    emitter.send(SseEmitter.event().data("[DONE]"));
                                    emitter.complete();
                                } else {
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
                } else {
                    traceRecorder.skipSpan(traceCtx, skillSpan);
                    routeToAgent(message, chatId, userId, emitter, traceCtx);
                }
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
        }, agentExecutor);

        emitter.onTimeout(() -> {
            log.warn("[Orchestrator] SSE timeout for chatId={}, cancelling async task", chatId);
            future.cancel(true);
            traceRecorder.failTrace(finalTraceCtx);
            persistTrace(finalTraceCtx);
        });
        emitter.onError(e -> {
            log.warn("[Orchestrator] SSE error for chatId={}: {}, cancelling async task", chatId, e.getMessage());
            future.cancel(true);
            traceRecorder.failTrace(finalTraceCtx);
            persistTrace(finalTraceCtx);
        });

        return emitter;
    }

    // syncCrossAgentMemory → delegated to ContextInjectionService

    /**
     * Merge two injection strings (skip empty ones).
     */
    private String mergeInjection(String a, String b) {
        boolean hasA = StringUtils.hasText(a);
        boolean hasB = StringUtils.hasText(b);
        if (hasA && hasB) return a + "\n" + b;
        if (hasA) return a;
        if (hasB) return b;
        return "";
    }

    /**
     * 路由到专业 Agent，支持画像注入与对话结束触发。
     */
    private void routeToAgent(String message, String chatId, String userId,
                              SseEmitter emitter, TraceContext traceCtx) throws IOException {
        com.yupi.yuaiagent.hitl.AgentRequestContext.set(userId, chatId, null);
        com.yupi.yuaiagent.trace.TraceContextHolder.set(traceCtx);
        try {
            routeToAgentInternal(message, chatId, userId, emitter, traceCtx);
        } finally {
            com.yupi.yuaiagent.hitl.AgentRequestContext.clear();
            com.yupi.yuaiagent.trace.TraceContextHolder.clear();
            com.yupi.yuaiagent.sessionstate.HandoffScopeContext.clear();
        }
    }

    private void routeToAgentInternal(String message, String chatId, String userId,
                              SseEmitter emitter, TraceContext traceCtx) throws IOException {

        // Async human handoff wake: if parked, hydrate on next user message then continue
        if (humanHandoffService != null && StringUtils.hasText(message)) {
            try {
                var waiting = humanHandoffService.findWaitingByChatId(chatId);
                if (waiting.isPresent()) {
                    var ticket = humanHandoffService.resume(waiting.get().getHandoffId(), userId, message);
                    emitter.send(SseEmitter.event().name("human-handoff").data(
                            "{\"status\":\"RESUMED\",\"handoffId\":\"" + ticket.getHandoffId() + "\"}"));
                    log.info("[HumanHandoff] auto-resumed {} for chatId={}", ticket.getHandoffId(), chatId);
                }
            } catch (Exception e) {
                log.debug("[HumanHandoff] auto-resume skipped: {}", e.getMessage());
            }
        }

        // Lock routing: if ConsultationAgent has an active session for this chat,
        // skip intent detection and route directly to it.
        if (consultationAgent.hasActiveConsultation(chatId)) {
            log.info("会话 {} 有进行中的预约咨询，锁定路由到 ConsultationAgent", chatId);
            TraceSpan routingSpan = traceRecorder.startSpan(traceCtx, TraceStepType.ROUTING, "路由到预约咨询（锁定）");
            emitter.send(SseEmitter.event()
                    .name("routing")
                    .data("[路由到预约咨询（进行中）]"));
            traceRecorder.putMetadata(routingSpan, "targetAgent", "CONSULTATION_LOCKED");
            traceRecorder.endSpan(traceCtx, routingSpan);

            TraceSpan subAgentSpan = traceRecorder.startSpan(traceCtx, TraceStepType.SUB_AGENT_EXECUTION, "预约咨询执行");
            Flux<String> tokenFlux = consultationAgent.chatStream(message, chatId, "");
            // ... reuse the same streaming logic below
            traceRecorder.endSpan(traceCtx, subAgentSpan);

            tokenFlux.subscribe(
                    chunk -> {
                        try {
                            emitter.send(SseEmitter.event().name("message").data(chunk));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        log.error("ConsultationAgent 流式输出出错", error);
                        traceRecorder.failTrace(traceCtx);
                        persistTrace(traceCtx);
                        emitter.completeWithError(error);
                    },
                    () -> {
                        try {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                            traceRecorder.endTrace(traceCtx);
                            persistTrace(traceCtx);
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }
            );
            return;
        }

        // Fast-path: keyword 规则优先（0 LLM）；仅多域冲突 / 槽位抽取 / 无规则命中且像复杂问句时才打 NLU
        // 旧逻辑：只要含「预约」就跳过规则直打 NLU，导致「有什么可以预约」白白等 3–8s
        List<AgentIntent> intents = List.of(AgentIntent.GENERAL);
        RouteHint routeHint = new RouteHint(AgentIntent.GENERAL.name(), null, 0.0, null, null, null);

        // Perception-bound upload: route by docKind before vague NLU clarification
        AgentIntent perceptionIntent = null;
        if (sessionSharedStateService != null && StringUtils.hasText(chatId)) {
            try {
                perceptionIntent = sessionSharedStateService.suggestIntentFromPerception(chatId, userId);
            } catch (Exception e) {
                log.debug("Perception route skipped: {}", e.getMessage());
            }
        }

        AgentIntent keywordIntent = KeywordRouter.keywordRouteIntent(message);
        if (keywordIntent == null && perceptionIntent != null) {
            keywordIntent = perceptionIntent;
            log.info("路由感知路径：perception={} message={}", perceptionIntent, message);
        }
        boolean multiDomain = KeywordRouter.hasMultiDomainConflict(message);
        // Bound perception already has material — do not force NLU for 「帮我分析」slot keywords
        boolean needsSlots = perceptionIntent == null && KeywordRouter.needsSlotExtraction(message);
        boolean fastPath = keywordIntent != null && !multiDomain && !needsSlots;

        if (fastPath) {
            TraceSpan nluSpan = traceRecorder.startSpan(traceCtx, TraceStepType.NLU, "NLU 快速路径（规则匹配）");
            traceRecorder.putMetadata(nluSpan, "intent", keywordIntent.name());
            traceRecorder.putMetadata(nluSpan, "confidence", "1.00");
            traceRecorder.putMetadata(nluSpan, "fastPath", "true");
            traceRecorder.endSpan(traceCtx, nluSpan);
            intents = List.of(keywordIntent);
            routeHint = new RouteHint(keywordIntent.name(), null, 1.0, null, null, null);
            log.info("路由快速路径：keyword={} message={}", keywordIntent, message);
        } else if (multiDomain || needsSlots || KeywordRouter.containsCareerKeyword(message)) {
            // Complex query — full NLU Pipeline (single LLM call)
            // Send progress feedback immediately so user doesn't stare at blank screen
            emitter.send(SseEmitter.event().name("routing").data("[正在分析你的问题...]"));

            TraceSpan nluSpan = traceRecorder.startSpan(traceCtx, TraceStepType.NLU, "NLU 意图理解");
            long nluStart = System.currentTimeMillis();
            NluPipeline.NluResult nluResult = nluPipeline.process(message, chatId);
            long nluMs = System.currentTimeMillis() - nluStart;
            traceRecorder.putMetadata(nluSpan, "intent", nluResult.getRouteHint().intent());
            traceRecorder.putMetadata(nluSpan, "confidence",
                String.format("%.2f", nluResult.getRouteHint().confidence()));
            traceRecorder.putMetadata(nluSpan, "entity", nluResult.getState().getEntity());
            traceRecorder.putMetadata(nluSpan, "nluLatencyMs", String.valueOf(nluMs));
            if (nluResult.getRouteHint().specificRoute() != null) {
                traceRecorder.putMetadata(nluSpan, "routeHint", nluResult.getRouteHint().specificRoute());
            }
            traceRecorder.endSpan(traceCtx, nluSpan);
            log.info("NLU 完整路径耗时 {}ms intent={}", nluMs, nluResult.getRouteHint().intent());

            // Clarification
            if (nluResult.isNeedsClarification()) {
                emitter.send(SseEmitter.event().name("clarification").data(nluResult.getClarification()));
                emitter.complete();
                return;
            }

            // Resolve multi-intents from NLU Pipeline
            intents = AgentIntent.fromMultiIntent(nluResult.getRerankedIntents());
            routeHint = nluResult.getRouteHint();
        } else {
            TraceSpan nluSpan = traceRecorder.startSpan(traceCtx, TraceStepType.NLU, "NLU 快速路径（默认通用）");
            traceRecorder.putMetadata(nluSpan, "intent", AgentIntent.GENERAL.name());
            traceRecorder.putMetadata(nluSpan, "fastPath", "true");
            traceRecorder.endSpan(traceCtx, nluSpan);
        }

        // Manifest secondary routing when confidence is low / default GENERAL
        if (agentManifestRegistry != null
                && (routeHint.confidence() < 0.55 || (intents.size() == 1 && intents.get(0) == AgentIntent.GENERAL))) {
            AgentIntent suggested = agentManifestRegistry.suggest(message, 0.9);
            if (suggested != null && suggested != AgentIntent.GENERAL) {
                log.info("[Manifest] secondary route {} (conf={}) → {}",
                        intents.get(0), routeHint.confidence(), suggested);
                intents = List.of(suggested);
                emitter.send(SseEmitter.event().name("routing").data(
                        "[Manifest 二次路由 → " + suggested.getAgentName() + "]"));
            }
        }

        // DATA_QUERY → treat as GENERAL with honest injection (no fake data)
        boolean dataQueryRemapped = intents.stream().anyMatch(i -> i == AgentIntent.DATA_QUERY);
        intents = intents.stream()
                .map(i -> i == AgentIntent.DATA_QUERY ? AgentIntent.GENERAL : i)
                .distinct()
                .toList();
        if (intents.isEmpty()) {
            intents = List.of(AgentIntent.GENERAL);
        }

        // ROUTING span — list all target agents
        String agentNames = intents.stream().map(AgentIntent::getAgentName).collect(java.util.stream.Collectors.joining(", "));
        TraceSpan routingSpan = traceRecorder.startSpan(traceCtx, TraceStepType.ROUTING, "路由到" + agentNames);
        emitter.send(SseEmitter.event().name("routing").data("[路由到" + agentNames + "]"));
        traceRecorder.putMetadata(routingSpan, "targetAgents", intents.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(",")));
        traceRecorder.endSpan(traceCtx, routingSpan);

        // Explicit structured Handoff Packet (Meta/Mission/Context/Artifacts + hop TTL + scope)
        String previousMemory = lastAgentMemoryByChat.get(chatId);
        com.yupi.yuaiagent.sessionstate.HandoffSanityResult handoffSanity = null;
        if (sessionSharedStateService != null) {
            try {
                String objective = ConsultationAgent.isScheduleInquiry(message)
                        ? "查询/确认预约日程"
                        : null;
                String traceId = traceCtx != null && traceCtx.getTrace() != null
                        ? traceCtx.getTrace().getTraceId() : null;
                handoffSanity = sessionSharedStateService.recordHandoffDetailed(
                        chatId, userId, previousMemory, intents.get(0).name(),
                        message, objective, traceId);
                if (ConsultationAgent.isScheduleInquiry(message)) {
                    sessionSharedStateService.setActiveGoal(chatId, userId, "查询/确认预约日程");
                }
                if (handoffSanity != null && !handoffSanity.accepted()
                        && ("hop_ttl_exceeded".equals(handoffSanity.reason())
                        || "ping_pong_loop".equals(handoffSanity.reason()))) {
                    // Async human escalation — park Packet, release SSE (no thread sleep)
                    log.warn("[Handoff] parking for human due to {}: {}",
                            handoffSanity.reason(), handoffSanity.suggestion());
                    if (humanHandoffService != null) {
                        var ticket = humanHandoffService.park(
                                chatId, userId, handoffSanity.repairedPacket(),
                                handoffSanity.reason(),
                                handoffSanity.suggestion());
                        emitter.send(SseEmitter.event().name("human-handoff").data(
                                "{\"status\":\"WAITING_FOR_HUMAN\",\"handoffId\":\""
                                        + ticket.getHandoffId()
                                        + "\",\"reason\":\"" + escapeJson(handoffSanity.reason()) + "\"}"));
                        String parkMsg = humanHandoffService.pendingMessage(ticket);
                        chatMemoryAdapter.addUserMessage(chatId, message, MessageSource.USER, null, null);
                        chunkAndPersistAnswer(chatId, AgentIntent.GENERAL, parkMsg, emitter);
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                        traceRecorder.endTrace(traceCtx);
                        persistTrace(traceCtx);
                        return;
                    }
                    intents = List.of(AgentIntent.GENERAL);
                }
            } catch (Exception e) {
                log.debug("SharedState handoff skipped: {}", e.getMessage());
            }
        }

        // Pre-compute shared context (profile + companion + shared state + artifacts + …)
        // Seed session goal from this turn when empty (Goal Anchor persistence)
        if (sessionSharedStateService != null && StringUtils.hasText(chatId)) {
            try {
                var state = sessionSharedStateService.getOrCreate(chatId, userId);
                if (!StringUtils.hasText(state.getActiveGoal())) {
                    sessionSharedStateService.setActiveGoal(chatId, userId,
                            com.yupi.yuaiagent.agent.goal.GoalAnchor.resolveGoal(null, message));
                }
            } catch (Exception e) {
                log.debug("Goal seed skipped: {}", e.getMessage());
            }
        }
        ContextInjectionService.InjectionResult injectionResult =
                contextInjectionService.buildCombinedInjectionResult(
                        userId, chatId, traceCtx, intents.get(0).name(), message);
        String combinedInjection = injectionResult.text();
        String turnId = traceCtx != null && traceCtx.getTrace() != null
                ? traceCtx.getTrace().getTraceId() : UUID.randomUUID().toString();
        if (artifactAdoptionService != null && !injectionResult.offeredArtifactIds().isEmpty()) {
            artifactAdoptionService.recordOffered(
                    injectionResult.offeredArtifactIds(), intents.get(0).name(), chatId, turnId);
        }
        if (dataQueryRemapped) {
            combinedInjection = mergeInjection(combinedInjection, DATA_QUERY_FALLBACK_NOTE);
        }
        // NACK → Repair injection (Request-Reply-Repair)
        if (handoffSanity != null && !handoffSanity.accepted()) {
            String nackBlock = """
                    【Handoff NACK — 交接未完全通过健全性检查】
                    - 原因：%s
                    - 修复建议：%s
                    - 要求：不要编造已剥离的交付物/文件；基于会话共享事实继续服务用户。
                    """.formatted(
                    handoffSanity.reason() != null ? handoffSanity.reason() : "unknown",
                    handoffSanity.suggestion() != null ? handoffSanity.suggestion() : "基于 Shared State 继续");
            combinedInjection = mergeInjection(combinedInjection, nackBlock);
        }

        // L27: Assemble layered memory context (if MemoryCoordinator is enabled)
        if (memoryCoordinator != null && StringUtils.hasText(userId)) {
            try {
                String primaryAgentType = memoryTypeOf(intents.get(0));
                org.springframework.ai.chat.messages.SystemMessage memoryContext =
                        memoryCoordinator.assembleContext(userId, chatId, primaryAgentType, message);
                if (memoryContext != null && StringUtils.hasText(memoryContext.getText())) {
                    combinedInjection = mergeInjection(combinedInjection, memoryContext.getText());
                    log.info("[MemoryCoordinator] injected L27 context for userId={}, agentType={}", userId, primaryAgentType);
                }
            } catch (Exception e) {
                log.warn("[MemoryCoordinator] failed to assemble context, continuing without: {}", e.getMessage());
            }
        }

        // Persist user message first (for history / resume correctness)
        chatMemoryAdapter.addUserMessage(chatId, message, MessageSource.USER, null, null);

        routeHintHolder.hint = routeHint;
        final String baseInjection = combinedInjection;
        long turnStart = System.currentTimeMillis();

        // ─── DAG path (feature-flagged): JOB_CHANGE / INTERVIEW fixed flows ───
        if (tryDagWorkflow(message, chatId, userId, routeHint, baseInjection, intents,
                injectionResult.offeredArtifactIds(), turnId, emitter, traceCtx, turnStart)) {
            return;
        }

        String fullAnswer;
        AgentIntent primaryIntent;
        boolean qualityPassed = true;

        if (intents.size() == 1) {
            // ─── Single-intent: true token SSE ───
            primaryIntent = intents.get(0);
            emitter.send(SseEmitter.event().name("collaboration")
                    .data("{\"mode\":\"SINGLE\",\"topology\":\"HUB_SPOKE\",\"agents\":\"" + agentNames + "\"}"));
            sendProgressEvent(emitter, new Object(), primaryIntent, "started", null);

            TraceSpan subSpan = traceRecorder.startSpan(traceCtx, TraceStepType.SUB_AGENT_EXECUTION,
                    primaryIntent.getAgentName() + "执行");
            fullAnswer = streamSingleExpert(
                    primaryIntent, message, chatId, baseInjection,
                    injectionResult.offeredArtifactIds(), emitter, traceCtx, subSpan);
            long dur = System.currentTimeMillis() - turnStart;
            sendProgressEvent(emitter, new Object(), primaryIntent, "finished", dur);
            traceRecorder.endSpan(traceCtx, subSpan);

            var qualityReview = qualityReviewHandler.review(
                    message, fullAnswer, chatId, primaryIntent, traceCtx, emitter);
            if (qualityReview != null
                    && qualityReview.getOverallScore() < com.yupi.yuaiagent.agent.collaboration.AgentCollaborationCoordinator.QUALITY_FAILOVER_THRESHOLD
                    && primaryIntent != AgentIntent.GENERAL) {
                qualityPassed = false;
                var recovery = applyQualityRecovery(
                        primaryIntent, qualityReview, message, chatId, userId, baseInjection,
                        List.of(), injectionResult.offeredArtifactIds(), turnId, emitter);
                fullAnswer = recovery.answer();
                primaryIntent = recovery.intent();
                chunkAndPersistAnswer(chatId, primaryIntent, fullAnswer, emitter);
            }
        } else {
            // ─── Multi-intent: parallel debate then synthesize (chunked push) ───
            emitter.send(SseEmitter.event().name("collaboration").data(
                    "{\"mode\":\"PARALLEL_DEBATE\",\"topology\":\"HUB_SPOKE\",\"agents\":\"" + agentNames + "\"}"));

            TraceSpan collabSpan = traceRecorder.startSpan(traceCtx, TraceStepType.SUB_AGENT_EXECUTION, "多专家并行协作");
            Object sseLock = new Object();
            var progressListener = new com.yupi.yuaiagent.agent.collaboration.AgentCollaborationCoordinator.ProgressListener() {
                @Override
                public void onExpertStarted(AgentIntent intent) {
                    sendProgressEvent(emitter, sseLock, intent, "started", null);
                }

                @Override
                public void onExpertFinished(AgentIntent intent, boolean success, long durationMs) {
                    sendProgressEvent(emitter, sseLock, intent, success ? "finished" : "failed", durationMs);
                }
            };

            var collabResult = collaborationCoordinator.collaborate(
                    intents, message, chatId, userId,
                    (intent, extraInjection) -> invokeExpertSync(intent, message, chatId,
                            mergeInjection(baseInjection, extraInjection)),
                    progressListener);

            traceRecorder.putMetadata(collabSpan, "mode", collabResult.mode().name());
            if (collabResult.handoffArtifactId() != null) {
                traceRecorder.putMetadata(collabSpan, "handoffArtifactId", collabResult.handoffArtifactId());
            }
            emitArtifactReady(emitter, collabResult.handoffArtifactId());
            if (collabResult.usedFailover()) {
                traceRecorder.putMetadata(collabSpan, "failoverTo", collabResult.failoverIntent().name());
                traceRecorder.putMetadata(collabSpan, "failoverReason", collabResult.failoverReason());
                emitter.send(SseEmitter.event().name("failover").data(
                        "{\"from\":\"" + collabResult.primaryIntent().name()
                                + "\",\"to\":\"" + collabResult.failoverIntent().name()
                                + "\",\"reason\":\"" + escapeJson(collabResult.failoverReason()) + "\"}"));
            }
            traceRecorder.endSpan(traceCtx, collabSpan);

            fullAnswer = collabResult.finalAnswer() != null ? collabResult.finalAnswer() : "";
            primaryIntent = collabResult.failoverIntent() != null
                    ? collabResult.failoverIntent()
                    : collabResult.primaryIntent();
            fullAnswer = extractAndSettleAdoption(
                    fullAnswer, injectionResult.offeredArtifactIds(), primaryIntent,
                    chatId, turnId, "Collaboration machine citation", emitter);
            chunkAndPersistAnswer(chatId, primaryIntent, fullAnswer, emitter);

            var qualityReview = qualityReviewHandler.review(
                    message, fullAnswer, chatId, collabResult.primaryIntent(), traceCtx, emitter);
            if (qualityReview != null
                    && qualityReview.getOverallScore() < com.yupi.yuaiagent.agent.collaboration.AgentCollaborationCoordinator.QUALITY_FAILOVER_THRESHOLD
                    && collabResult.mode() != com.yupi.yuaiagent.agent.collaboration.CollaborationResult.Mode.FAILOVER
                    && collabResult.mode() != com.yupi.yuaiagent.agent.collaboration.CollaborationResult.Mode.SELF_REPAIR
                    && collabResult.primaryIntent() != AgentIntent.GENERAL) {
                qualityPassed = false;
                var recovery = applyQualityRecovery(
                        collabResult.primaryIntent(), qualityReview, message, chatId, userId, baseInjection,
                        collabResult.opinions(), injectionResult.offeredArtifactIds(), turnId, emitter);
                fullAnswer = recovery.answer();
                primaryIntent = recovery.intent();
                chunkAndPersistAnswer(chatId, primaryIntent, fullAnswer, emitter);
            }
        }

        fullAnswer = extractAndSettleAdoption(
                fullAnswer, injectionResult.offeredArtifactIds(), primaryIntent,
                chatId, turnId, "Agent response machine citation", emitter);
        if (qualityPassed && StringUtils.hasText(fullAnswer)) {
            publishRequestedStructuredArtifact(
                    message, userId, chatId, primaryIntent, emitter);
        }

        long durationMs = System.currentTimeMillis() - turnStart;
        int approxChars = fullAnswer != null ? fullAnswer.length() : 0;
        int approxTokens = Math.max(1, approxChars / 2);
        recordTurnTokenUsage(userId, approxTokens);
        emitter.send(SseEmitter.event().name("usage").data(
                "{\"approxChars\":" + approxChars
                        + ",\"approxTokens\":" + approxTokens
                        + ",\"durationMs\":" + durationMs
                        + ",\"mode\":\"" + (intents.size() > 1 ? "PARALLEL_DEBATE" : "SINGLE") + "\"}"));

        // Finalize trace
        traceRecorder.endTrace(traceCtx);
        traceCtx.markSseClosed();
        persistTrace(traceCtx);
        emitSuggestedActions(emitter, primaryIntent, false);
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
        contextInjectionService.triggerProfileUpdate(userId, memoryTypeOf(primaryIntent), chatId, traceCtx);

        if (memoryCoordinator != null && StringUtils.hasText(userId)) {
            try {
                List<Message> memoryMessages = new java.util.ArrayList<>();
                memoryMessages.add(new UserMessage(message));
                memoryMessages.add(new AssistantMessage(fullAnswer != null ? fullAnswer : ""));
                memoryCoordinator.onTurnCompleted(userId, chatId, memoryMessages);
            } catch (Exception e) {
                log.warn("[MemoryCoordinator] extraction trigger failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Generates a specialist answer, strips machine citations, then streams safe chunks.
     */
    private String streamSingleExpert(AgentIntent intent, String message, String chatId,
                                      String injection, List<String> offeredArtifactIds,
                                      SseEmitter emitter, TraceContext traceCtx,
                                      TraceSpan subSpan) {
        String memoryType = memoryTypeOf(intent);
        String previousMemory = lastAgentMemoryByChat.get(chatId);
        contextInjectionService.syncCrossAgentMemory(chatId, memoryType, previousMemory);
        lastAgentMemoryByChat.put(chatId, memoryType);

        Flux<String> tokenFlux = switch (intent) {
            case RESUME -> resumeAgent.chatStream(message, chatId, injection);
            case NEGOTIATION -> negotiationAgent.chatStream(message, chatId, injection);
            case ESCAPE -> escapeAgent.chatStream(message, chatId, injection);
            case CONSULTATION -> consultationAgent.chatStream(message, chatId, injection);
            case DIGITAL_EMPLOYEE -> generalCareerAgent.chatStream(message, chatId,
                    mergeInjection(injection, DIGITAL_EMPLOYEE_NOTE));
            default -> generalCareerAgent.chatStream(message, chatId, injection);
        };

        var streamingMsg = chatMemoryAdapter.startAssistantStream(
                chatId, MessageSource.AGENT, intent.name(), intent.getAgentName());
        try {
            emitter.send(SseEmitter.event().name("message-start")
                    .data("{\"assistantMessageId\":\"" + streamingMsg.getMessageId()
                            + "\",\"agentType\":\"" + intent.name() + "\"}"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        StringBuilder rawAnswer = new StringBuilder();
        try {
            tokenFlux.doOnNext(rawAnswer::append).blockLast();
            String cleanAnswer = artifactCitationExtractor
                    .extract(rawAnswer.toString(), offeredArtifactIds).cleanText();
            for (int i = 0; i < cleanAnswer.length(); i += 40) {
                emitter.send(SseEmitter.event().name("message")
                        .data(cleanAnswer.substring(i, Math.min(i + 40, cleanAnswer.length()))));
            }
            chatMemoryAdapter.completeAssistant(streamingMsg.getMessageId(), cleanAnswer);
            if (subSpan != null) {
                traceRecorder.putMetadata(subSpan, "agentType", memoryType);
            }
            return rawAnswer.toString();
        } catch (Exception e) {
            log.error("Agent {} stream failed", intent.name(), e);
            if (subSpan != null && traceCtx != null) {
                traceRecorder.failSpan(traceCtx, subSpan, e.getMessage());
            }
            if (rawAnswer.isEmpty()) {
                rawAnswer.append("（该专家暂时无法回答）");
            }
            String partial = artifactCitationExtractor
                    .extract(rawAnswer.toString(), offeredArtifactIds).cleanText();
            chatMemoryAdapter.updateAssistantPartial(streamingMsg.getMessageId(), partial);
            chatMemoryAdapter.markAssistantPartial(streamingMsg.getMessageId());
            return rawAnswer.toString();
        }
    }

    /**
     * When {@code workflow.dag.enabled} and matcher hits JOB_CHANGE/INTERVIEW (confidence ≥ 0.6),
     * run the ready-queue DAG and stream the synthesized answer. Returns true if handled.
     */
    private boolean tryDagWorkflow(String message, String chatId, String userId, RouteHint routeHint,
                                   String baseInjection, List<AgentIntent> intents,
                                   List<String> offeredArtifactIds, String turnId,
                                   SseEmitter emitter, TraceContext traceCtx, long turnStart)
            throws IOException {
        if (!workflowDagEnabled) {
            return false;
        }
        String specificRoute = routeHint != null ? routeHint.specificRoute() : null;
        var match = workflowMatcher.match(message, specificRoute);
        if (match == null || match.confidence() < 0.6 || !dagCompiler.supports(match.workflowId())) {
            return false;
        }

        TraceSpan matchSpan = traceRecorder.startSpan(traceCtx, TraceStepType.WORKFLOW_MATCH,
                "工作流匹配 " + match.workflowId());
        traceRecorder.putMetadata(matchSpan, "workflowId", match.workflowId());
        traceRecorder.putMetadata(matchSpan, "matchType", match.matchType().name());
        traceRecorder.putMetadata(matchSpan, "confidence", String.format("%.2f", match.confidence()));
        traceRecorder.endSpan(traceCtx, matchSpan);

        emitter.send(SseEmitter.event().name("collaboration").data(
                "{\"mode\":\"DAG_WORKFLOW\",\"topology\":\"LINEAR_PIPELINE\",\"workflowId\":\""
                        + match.workflowId() + "\"}"));
        emitter.send(SseEmitter.event().name("routing")
                .data("[DAG 工作流: " + match.workflowId() + "]"));

        var conversationContext = contextBuilder.build(chatId, userId);
        if (StringUtils.hasText(baseInjection)) {
            conversationContext = new com.yupi.yuaiagent.context.ConversationContext(
                    conversationContext.userProfile(),
                    conversationContext.conversationSummary(),
                    conversationContext.recentMessages(),
                    chatId,
                    baseInjection);
        }

        var dag = dagCompiler.compile(match.workflowId());
        TraceSpan taskSpan = traceRecorder.startSpan(traceCtx, TraceStepType.TASK_EXECUTION,
                "DAG 执行 " + match.workflowId());
        Object sseLock = new Object();
        var progressListener = new com.yupi.yuaiagent.workflow.dag.DagProgressListener() {
            @Override
            public void onNodeStarted(com.yupi.yuaiagent.workflow.dag.DagNodeSpec node) {
                if (node.type() == com.yupi.yuaiagent.workflow.dag.DagNodeType.AGENT
                        && node.agentId() != null) {
                    try {
                        sendProgressEvent(emitter, sseLock, AgentIntent.valueOf(node.agentId()),
                                "started", null);
                    } catch (Exception ignored) {
                        // unknown agent id — skip progress
                    }
                }
            }

            @Override
            public void onNodeFinished(com.yupi.yuaiagent.workflow.dag.DagNodeSpec node,
                                       boolean success, long durationMs) {
                if (node.type() == com.yupi.yuaiagent.workflow.dag.DagNodeType.AGENT
                        && node.agentId() != null) {
                    try {
                        sendProgressEvent(emitter, sseLock, AgentIntent.valueOf(node.agentId()),
                                success ? "finished" : "failed", durationMs);
                    } catch (Exception ignored) {
                        // skip
                    }
                }
            }
        };

        var dagResult = dagWorkflowExecutor.execute(
                dag, conversationContext, message, userId, chatId, progressListener);
        dagResult.artifactIds().forEach(id -> emitArtifactReady(emitter, id));
        traceRecorder.putMetadata(taskSpan, "instanceId", dagResult.instanceId());
        traceRecorder.putMetadata(taskSpan, "status", dagResult.status().name());
        if (dagResult.errorMessage() != null) {
            traceRecorder.putMetadata(taskSpan, "error", dagResult.errorMessage());
        }
        traceRecorder.endSpan(traceCtx, taskSpan);

        TraceSpan aggSpan = traceRecorder.startSpan(traceCtx, TraceStepType.RESULT_AGGREGATION, "DAG 结果汇总");
        String fullAnswer = dagResult.finalAnswer() != null ? dagResult.finalAnswer() : "";
        if (!dagResult.success() && !StringUtils.hasText(fullAnswer)) {
            fullAnswer = "工作流执行未完成，请稍后重试。"
                    + (dagResult.errorMessage() != null ? "（" + dagResult.errorMessage() + "）" : "");
        }
        traceRecorder.endSpan(traceCtx, aggSpan);

        AgentIntent primaryIntent = intents != null && !intents.isEmpty()
                ? intents.get(0) : AgentIntent.GENERAL;
        fullAnswer = extractAndSettleAdoption(
                fullAnswer, offeredArtifactIds, primaryIntent,
                chatId, turnId, "DAG response machine citation", emitter);
        chunkAndPersistAnswer(chatId, primaryIntent, fullAnswer, emitter);

        boolean qualityPassed = true;
        var qualityReview = qualityReviewHandler.review(
                message, fullAnswer, chatId, primaryIntent, traceCtx, emitter);
        if (qualityReview != null
                && qualityReview.getOverallScore() < com.yupi.yuaiagent.agent.collaboration.AgentCollaborationCoordinator.QUALITY_FAILOVER_THRESHOLD
                && primaryIntent != AgentIntent.GENERAL) {
            qualityPassed = false;
            var recovery = applyQualityRecovery(
                    primaryIntent, qualityReview, message, chatId, userId, baseInjection,
                    dagResult.opinions() != null ? dagResult.opinions() : List.of(),
                    offeredArtifactIds, turnId, emitter);
            fullAnswer = recovery.answer();
            primaryIntent = recovery.intent();
            chunkAndPersistAnswer(chatId, primaryIntent, fullAnswer, emitter);
        }
        if (qualityPassed && StringUtils.hasText(fullAnswer)) {
            publishRequestedStructuredArtifact(message, userId, chatId, primaryIntent, emitter);
        }
        int approxChars = fullAnswer != null ? fullAnswer.length() : 0;
        long durationMs = System.currentTimeMillis() - turnStart;
        int approxTokens = Math.max(1, approxChars / 2);
        recordTurnTokenUsage(userId, approxTokens);
        emitter.send(SseEmitter.event().name("usage").data(
                "{\"approxChars\":" + approxChars
                        + ",\"approxTokens\":" + approxTokens
                        + ",\"durationMs\":" + durationMs
                        + ",\"mode\":\"DAG_WORKFLOW\",\"workflowId\":\"" + match.workflowId() + "\"}"));

        traceRecorder.endTrace(traceCtx);
        traceCtx.markSseClosed();
        persistTrace(traceCtx);
        emitSuggestedActions(emitter, primaryIntent, false);
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
        contextInjectionService.triggerProfileUpdate(userId, memoryTypeOf(primaryIntent), chatId, traceCtx);

        if (memoryCoordinator != null && StringUtils.hasText(userId)) {
            try {
                List<Message> memoryMessages = new java.util.ArrayList<>();
                memoryMessages.add(new UserMessage(message));
                memoryMessages.add(new AssistantMessage(fullAnswer != null ? fullAnswer : ""));
                memoryCoordinator.onTurnCompleted(userId, chatId, memoryMessages);
            } catch (Exception e) {
                log.warn("[MemoryCoordinator] extraction trigger failed: {}", e.getMessage());
            }
        }
        log.info("[Orchestrator] DAG workflow {} completed instance={}",
                match.workflowId(), dagResult.instanceId());
        return true;
    }

    private void chunkAndPersistAnswer(String chatId, AgentIntent intent, String fullAnswer,
                                       SseEmitter emitter) throws IOException {
        String text = fullAnswer != null ? fullAnswer : "";
        var streamingMsg = chatMemoryAdapter.startAssistantStream(
                chatId, MessageSource.AGENT, intent.name(), intent.getAgentName());
        emitter.send(SseEmitter.event().name("message-start")
                .data("{\"assistantMessageId\":\"" + streamingMsg.getMessageId()
                        + "\",\"agentType\":\"" + intent.name() + "\"}"));
        int chunkSize = 40;
        for (int i = 0; i < text.length(); i += chunkSize) {
            emitter.send(SseEmitter.event().name("message")
                    .data(text.substring(i, Math.min(i + chunkSize, text.length()))));
        }
        chatMemoryAdapter.completeAssistant(streamingMsg.getMessageId(), text);
    }

    /**
     * Quality NACK → same-expert SELF_REPAIR once → else GENERAL failover.
     */
    private record QualityRecovery(String answer, AgentIntent intent) {}

    private QualityRecovery applyQualityRecovery(
            AgentIntent failedIntent,
            com.yupi.yuaiagent.quality.QualityReview qualityReview,
            String message,
            String chatId,
            String userId,
            String baseInjection,
            List<com.yupi.yuaiagent.agent.collaboration.ExpertOpinion> priorOpinions,
            List<String> offeredArtifactIds,
            String turnId,
            SseEmitter emitter) throws IOException {

        String summary = qualityReview.getSummary() != null
                ? qualityReview.getSummary()
                : "score=" + qualityReview.getOverallScore();

        emitter.send(SseEmitter.event().name("collaboration").data(
                "{\"mode\":\"QUALITY_SELF_REPAIR\",\"score\":" + qualityReview.getOverallScore()
                        + ",\"agent\":\"" + failedIntent.name() + "\"}"));

        var result = collaborationCoordinator.failoverAfterQuality(
                failedIntent,
                summary,
                qualityReview.getIssues(),
                qualityReview.getSuggestions(),
                message, chatId, userId,
                (intent, extraInjection) -> invokeExpertSync(intent, message, chatId,
                        mergeInjection(baseInjection, extraInjection)),
                priorOpinions != null ? priorOpinions : List.of());

        String mode = result.usedSelfRepair() ? "QUALITY_SELF_REPAIR_DONE"
                : (result.usedFailover() ? "QUALITY_FAILOVER" : result.mode().name());
        emitter.send(SseEmitter.event().name("collaboration").data(
                "{\"mode\":\"" + mode + "\",\"score\":" + qualityReview.getOverallScore()
                        + ",\"effectiveIntent\":\"" + result.effectiveIntent().name() + "\"}"));
        if (result.usedFailover()) {
            emitter.send(SseEmitter.event().name("failover").data(
                    "{\"from\":\"" + failedIntent.name()
                            + "\",\"to\":\"" + result.failoverIntent().name()
                            + "\",\"reason\":\"" + escapeJson(result.failoverReason()) + "\"}"));
            if (agentManifestRegistry != null) {
                agentManifestRegistry.penalize(failedIntent, 0.85);
            }
        } else if (result.usedSelfRepair() && agentManifestRegistry != null) {
            agentManifestRegistry.reward(failedIntent, 1.05);
        }

        emitArtifactReady(emitter, result.handoffArtifactId());
        String answer = result.finalAnswer() != null ? result.finalAnswer() : "";
        AgentIntent intent = result.effectiveIntent() != null ? result.effectiveIntent() : failedIntent;
        answer = extractAndSettleAdoption(
                answer, offeredArtifactIds, intent,
                chatId, turnId, "Quality recovery machine citation", emitter);
        return new QualityRecovery(answer, intent);
    }

    /**
     * Synchronously invoke a specialist agent (used by parallel collaboration).
     */
    private String invokeExpertSync(AgentIntent intent, String message, String chatId,
                                    String injection) {
        return com.yupi.yuaiagent.agent.loop.AgentDepthContext.runWithDepth(
                () -> invokeExpertSyncBody(intent, message, chatId, injection),
                () -> com.yupi.yuaiagent.agent.loop.AgentDepthContext.denyMessage(
                        com.yupi.yuaiagent.agent.loop.AgentDepthContext.DEFAULT_MAX_DEPTH));
    }

    private String invokeExpertSyncBody(AgentIntent intent, String message, String chatId,
                                        String injection) {
        String memoryType = memoryTypeOf(intent);
        String previousMemory = lastAgentMemoryByChat.get(chatId);
        contextInjectionService.syncCrossAgentMemory(chatId, memoryType, previousMemory);
        lastAgentMemoryByChat.put(chatId, memoryType);

        Flux<String> tokenFlux = switch (intent) {
            case RESUME -> resumeAgent.chatStream(message, chatId, injection);
            case NEGOTIATION -> negotiationAgent.chatStream(message, chatId, injection);
            case ESCAPE -> escapeAgent.chatStream(message, chatId, injection);
            case CONSULTATION -> consultationAgent.chatStream(message, chatId, injection);
            case DATA_QUERY -> generalCareerAgent.chatStream(message, chatId,
                    mergeInjection(injection, DATA_QUERY_FALLBACK_NOTE));
            case DIGITAL_EMPLOYEE -> generalCareerAgent.chatStream(message, chatId,
                    mergeInjection(injection, DIGITAL_EMPLOYEE_NOTE));
            default -> generalCareerAgent.chatStream(message, chatId, injection);
        };
        StringBuilder sb = new StringBuilder();
        tokenFlux.doOnNext(sb::append).blockLast();
        return sb.toString();
    }

    /**
     * Emits an "agent-progress" SSE event: {"agent":"...","status":"started|finished|failed","durationMs":123}.
     */
    private void sendProgressEvent(SseEmitter emitter, Object lock, AgentIntent intent, String status, Long durationMs) {
        try {
            synchronized (lock) {
                String data = "{\"agent\":\"" + intent.name() + "\",\"status\":\"" + status + "\""
                        + (durationMs != null ? ",\"durationMs\":" + durationMs : "") + "}";
                emitter.send(SseEmitter.event().name("agent-progress").data(data));
            }
        } catch (Exception e) {
            log.debug("[Orchestrator] agent-progress emit skipped: {}", e.getMessage());
        }
    }

    /** Emits artifact-ready so the UI can open the delivery panel. */
    private void emitArtifactReady(SseEmitter emitter, String artifactId) {
        if (!StringUtils.hasText(artifactId) || emitter == null) {
            return;
        }
        try {
            artifactShelf.get(artifactId).ifPresent(a -> {
                try {
                    String data = "{\"artifactId\":\"" + escapeJson(a.getArtifactId())
                            + "\",\"type\":\"" + escapeJson(a.getType())
                            + "\",\"title\":\"" + escapeJson(a.getTitle())
                            + "\",\"chatId\":\"" + escapeJson(a.getChatId()) + "\"}";
                    emitter.send(SseEmitter.event().name("artifact-ready").data(data));
                } catch (Exception e) {
                    log.debug("[Orchestrator] artifact-ready emit skipped: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            log.debug("[Orchestrator] artifact-ready lookup skipped: {}", e.getMessage());
        }
    }

    private String extractAndSettleAdoption(String answer, List<String> offeredArtifactIds,
                                            AgentIntent consumerAgent, String chatId,
                                            String turnId, String evidence, SseEmitter emitter) {
        var extraction = artifactCitationExtractor.extract(answer, offeredArtifactIds);
        if (artifactAdoptionService != null && !extraction.adoptedArtifactIds().isEmpty()) {
            artifactAdoptionService.recordAdopted(
                    extraction.adoptedArtifactIds(),
                    consumerAgent != null ? consumerAgent.name() : AgentIntent.GENERAL.name(),
                    chatId, turnId, 1.0, evidence);
            emitArtifactAdopted(emitter, extraction.adoptedArtifactIds());
        }
        return extraction.cleanText();
    }

    private void emitArtifactAdopted(SseEmitter emitter, List<String> artifactIds) {
        if (emitter == null || artifactIds == null || artifactIds.isEmpty()) {
            return;
        }
        try {
            String ids = artifactIds.stream()
                    .map(id -> "\"" + escapeJson(id) + "\"")
                    .collect(java.util.stream.Collectors.joining(","));
            emitter.send(SseEmitter.event().name("artifact-adopted")
                    .data("{\"artifactIds\":[" + ids + "]}"));
        } catch (Exception e) {
            log.debug("[Orchestrator] artifact-adopted emit skipped: {}", e.getMessage());
        }
    }

    /**
     * Publishes only when the user explicitly asks for a reusable structured deliverable.
     */
    private void publishRequestedStructuredArtifact(String message, String userId, String chatId,
                                                     AgentIntent intent, SseEmitter emitter) {
        if (!StringUtils.hasText(message) || !StringUtils.hasText(userId)
                || !StringUtils.hasText(chatId)) {
            return;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        boolean explicit = containsAny(normalized, "生成", "制定", "输出", "整理", "做一份", "给我一份");
        if (!explicit) {
            return;
        }

        com.yupi.yuaiagent.agent.data.DataEmployeeAgent employee = null;
        if (containsAny(normalized, "晋升规划", "晋升计划", "晋升路线", "晋升行动计划")) {
            employee = promotionPlannerAgent;
        } else if (containsAny(normalized, "用户画像摘要", "画像摘要", "用户画像整理")) {
            employee = profileCuratorAgent;
        } else if (containsAny(normalized, "学习资源清单", "学习资源推荐", "学习方案")) {
            employee = learningResourceRecommenderAgent;
        } else if (containsAny(normalized, "数据分析报告", "分析报告", "数据报告")) {
            employee = dataAnalystAgent;
        } else if (containsAny(normalized, "岗位辅导方案", "职业行动计划", "职业辅导建议")) {
            employee = careerCoachAgent;
        }
        if (employee == null) {
            return;
        }

        try {
            var context = new com.yupi.yuaiagent.agent.data.ProductionContext(
                    userId, chatId,
                    com.yupi.yuaiagent.agent.data.AnalysisSource.CONVERSATION,
                    memoryTypeOf(intent), null);
            var result = employee.produce(context);
            if (result.success()) {
                emitArtifactReady(emitter, result.artifactId());
            } else {
                log.debug("结构化交付物未发布: {}", result.errorMessage());
            }
        } catch (Exception e) {
            log.warn("结构化交付物生产失败，不影响主回答: {}", e.getMessage());
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String memoryTypeOf(AgentIntent intent) {
        return switch (intent) {
            case RESUME -> "resume";
            case NEGOTIATION -> "negotiation";
            case ESCAPE -> "escape";
            case CONSULTATION -> "consultation";
            case DATA_QUERY -> "data_query";
            case DIGITAL_EMPLOYEE -> "digital_employee";
            default -> "general";
        };
    }

    private void emitSuggestedActions(SseEmitter emitter, AgentIntent intent, boolean fromSkill)
            throws IOException {
        var actions = fromSkill
                ? SuggestedActions.forSkill(null)
                : SuggestedActions.forIntent(intent);
        emitter.send(SseEmitter.event()
                .name("suggested-actions")
                .data(SuggestedActions.toJson(actions)));
    }

    /**
     * 技能标签匹配很宽，避免「职业方向/预约」等普通问句误进技能 LLM。
     * 仅当用户话里出现该技能的核心中文触发词时才执行技能。
     */
    private boolean isStrongSkillTrigger(String message, com.yupi.yuaiagent.skill.SkillDefinition skill) {
        if (message == null || skill == null) {
            return false;
        }
        String m = message.toLowerCase();
        return switch (skill.getName()) {
            case "interview-prep" -> m.contains("面试") || m.contains("面经") || m.contains("模拟面试");
            case "salary-research" -> m.contains("薪资调研") || m.contains("谈薪") || m.contains("涨薪")
                    || m.contains("薪水") || m.contains("薪酬");
            case "resignation-letter" -> m.contains("离职信") || m.contains("辞职信") || m.contains("交接清单")
                    || (m.contains("离职") && (m.contains("写") || m.contains("申请") || m.contains("邮件")));
            default -> false;
        };
    }

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

    private void recordTurnTokenUsage(String userId, int approxTokens) {
        if (userQuotaService == null || !StringUtils.hasText(userId) || approxTokens <= 0) {
            return;
        }
        try {
            userQuotaService.addTokenUsage(userId, approxTokens);
        } catch (Exception e) {
            log.warn("[Orchestrator] failed to record turn token usage userId={}: {}", userId, e.getMessage());
        }
    }
}
