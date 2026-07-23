package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.calendar.CalendarServiceFactory;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.memory.MemoryCoordinator;
import com.yupi.yuaiagent.config.FollowUpTemplateConfig;
import com.yupi.yuaiagent.message.ChatMemoryAdapter;
import com.yupi.yuaiagent.message.MessageSource;
import com.yupi.yuaiagent.rag.QueryRewriter;
import com.yupi.yuaiagent.repository.AppointmentRepository;
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
import com.yupi.yuaiagent.validation.InfoValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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
import java.util.concurrent.Executor;

/**
 * 主控 Agent（Orchestrator）
 * <ul>
 *   <li>单意图：子 Agent {@code chatStream} 真 token 级 SSE</li>
 *   <li>多意图：并行辩论 → LLM/规则综合后再推送（非逐 token）</li>
 * </ul>
 * workflowMatcher / taskExecutor：脚手架，未接入主聊天路径（见 FEATURES L18）。
 *
 * 路由：RESUME / NEGOTIATION / ESCAPE / CONSULTATION / GENERAL；
 * DATA_QUERY 映射为 GENERAL + 诚实说明（未接业务数据源）。
 * YuManus 走独立 /manus/chat。
 */
@Slf4j
public class OrchestratorAgent {

    private final NluPipeline nluPipeline;
    private final DataQueryRouter dataQueryRouter;
    /** 脚手架：未接入主聊天（FEATURES L18） */
    private final WorkflowMatcher workflowMatcher;
    private final WorkflowRegistry workflowRegistry;
    private final ConversationContextBuilder contextBuilder;
    /** 脚手架：仅 setAgentRunners，主路径用 CollaborationCoordinator */
    private final TaskExecutor taskExecutor;
    private final ResultAggregator resultAggregator;

    /** DATA_QUERY 未接真实数据源时注入 GENERAL 的说明 */
    static final String DATA_QUERY_FALLBACK_NOTE = """
            【数据查询说明】用户想查数据/报表。当前未接入业务数据源，请勿编造数字。
            请以职场顾问方式给出替代建议：手工统计、问数口径、仪表盘建设步骤。
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
        this.resumeAgent = new ResumeAgent(deps.chatModel(), deps.vectorStore(), deps.queryRewriter(), deps.chatMemoryManager());
        this.negotiationAgent = new NegotiationAgent(deps.chatModel(), deps.tools(), deps.queryRewriter(), deps.chatMemoryManager());
        this.escapeAgent = new EscapeAgent(deps.chatModel(), deps.tools(), deps.queryRewriter(), deps.chatMemoryManager());
        this.generalCareerAgent = new GeneralCareerAgent(deps.chatModel(), deps.chatMemoryManager());
        this.consultationAgent = new ConsultationAgent(deps.chatModel(), deps.chatMemoryManager(), deps.templateConfig(), deps.infoValidator(), deps.calendarServiceFactory(), deps.appointmentRepository(), deps.humanApprovalService());
        this.skillExecutor = deps.skillExecutor();
        this.skillRegistry = deps.skillRegistry();
        this.chatMemoryManager = deps.chatMemoryManager();
        this.memoryCoordinator = deps.memoryCoordinator();
        this.traceRecorder = deps.traceRecorder();
        this.traceRepository = deps.traceRepository();
        this.chatMemoryAdapter = deps.chatMemoryAdapter();
        this.qualityReviewHandler = new QualityReviewHandler(deps.qualityGuardAgent(), deps.qualityModeResolver(), deps.qualityReviewRepository(), deps.traceRecorder());
        this.contextInjectionService = new ContextInjectionService(deps.userProfileService(), deps.artifactShelf(), deps.messageRepository(), deps.chatMemoryManager(), deps.traceRecorder(), deps.reflexionService());
        this.messageRepository = deps.messageRepository();
        this.nluPipeline = deps.nluPipeline();
        this.dataQueryRouter = deps.dataQueryRouter();
        this.workflowMatcher = deps.workflowMatcher();
        this.workflowRegistry = deps.workflowRegistry();
        this.contextBuilder = deps.contextBuilder();
        this.taskExecutor = deps.taskExecutor();
        this.resultAggregator = deps.resultAggregator();
        this.accessDecisionService = deps.accessDecisionService();
        this.promptInjectionDetector = deps.promptInjectionDetector();
        this.agentExecutor = deps.agentExecutor();
        this.collaborationCoordinator = deps.collaborationCoordinator();

        // Register AgentRunner map on TaskExecutor
        taskExecutor.setAgentRunners(java.util.Map.of(
            "RESUME", new ResumeAgentRunner(this.resumeAgent),
            "NEGOTIATION", new NegotiationAgentRunner(this.negotiationAgent),
            "ESCAPE", new EscapeAgentRunner(this.escapeAgent),
            "GENERAL", new GeneralCareerAgentRunner(this.generalCareerAgent)
        ));

        log.info("OrchestratorAgent 初始化完成，已创建 5 个专业 Agent，已加载 {} 个技能", skillRegistry.size());
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
                        skillRegistry.findByIntent(message);
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
        try {
            routeToAgentInternal(message, chatId, userId, emitter, traceCtx);
        } finally {
            com.yupi.yuaiagent.hitl.AgentRequestContext.clear();
        }
    }

    private void routeToAgentInternal(String message, String chatId, String userId,
                              SseEmitter emitter, TraceContext traceCtx) throws IOException {

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

        AgentIntent keywordIntent = KeywordRouter.keywordRouteIntent(message);
        boolean multiDomain = KeywordRouter.hasMultiDomainConflict(message);
        boolean needsSlots = KeywordRouter.needsSlotExtraction(message);
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

        // Pre-compute shared context (profile + artifacts + cross-agent + reflexion by intent)
        String combinedInjection = contextInjectionService.buildCombinedInjection(
                userId, chatId, traceCtx, intents.get(0).name());
        if (dataQueryRemapped) {
            combinedInjection = mergeInjection(combinedInjection, DATA_QUERY_FALLBACK_NOTE);
        }

        // L27: Assemble layered memory context (if MemoryCoordinator is enabled)
        if (memoryCoordinator != null && StringUtils.hasText(userId)) {
            try {
                String primaryAgentType = memoryTypeOf(intents.get(0));
                org.springframework.ai.chat.messages.SystemMessage memoryContext =
                        memoryCoordinator.assembleContext(userId, chatId, primaryAgentType);
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

        String fullAnswer;
        AgentIntent primaryIntent;

        if (intents.size() == 1) {
            // ─── Single-intent: true token SSE ───
            primaryIntent = intents.get(0);
            emitter.send(SseEmitter.event().name("collaboration")
                    .data("{\"mode\":\"SINGLE\",\"agents\":\"" + agentNames + "\"}"));
            sendProgressEvent(emitter, new Object(), primaryIntent, "started", null);

            TraceSpan subSpan = traceRecorder.startSpan(traceCtx, TraceStepType.SUB_AGENT_EXECUTION,
                    primaryIntent.getAgentName() + "执行");
            fullAnswer = streamSingleExpert(primaryIntent, message, chatId, baseInjection, emitter, traceCtx, subSpan);
            long dur = System.currentTimeMillis() - turnStart;
            sendProgressEvent(emitter, new Object(), primaryIntent, "finished", dur);
            traceRecorder.endSpan(traceCtx, subSpan);

            var qualityReview = qualityReviewHandler.review(
                    message, fullAnswer, chatId, primaryIntent, traceCtx, emitter);
            if (qualityReview != null
                    && qualityReview.getOverallScore() < com.yupi.yuaiagent.agent.collaboration.AgentCollaborationCoordinator.QUALITY_FAILOVER_THRESHOLD
                    && primaryIntent != AgentIntent.GENERAL) {
                emitter.send(SseEmitter.event().name("collaboration").data(
                        "{\"mode\":\"QUALITY_FAILOVER\",\"score\":" + qualityReview.getOverallScore() + "}"));
                var failover = collaborationCoordinator.failoverAfterQuality(
                        primaryIntent,
                        qualityReview.getSummary() != null ? qualityReview.getSummary()
                                : "score=" + qualityReview.getOverallScore(),
                        message, chatId, userId,
                        (intent, extraInjection) -> invokeExpertSync(intent, message, chatId,
                                mergeInjection(baseInjection, extraInjection)),
                        List.of());
                fullAnswer = failover.finalAnswer() != null ? failover.finalAnswer() : fullAnswer;
                primaryIntent = failover.failoverIntent() != null ? failover.failoverIntent() : AgentIntent.GENERAL;
                chunkAndPersistAnswer(chatId, primaryIntent, fullAnswer, emitter);
            }
        } else {
            // ─── Multi-intent: parallel debate then synthesize (chunked push) ───
            emitter.send(SseEmitter.event().name("collaboration").data(
                    "{\"mode\":\"PARALLEL_DEBATE\",\"agents\":\"" + agentNames + "\"}"));

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
            chunkAndPersistAnswer(chatId, primaryIntent, fullAnswer, emitter);

            var qualityReview = qualityReviewHandler.review(
                    message, fullAnswer, chatId, collabResult.primaryIntent(), traceCtx, emitter);
            if (qualityReview != null
                    && qualityReview.getOverallScore() < com.yupi.yuaiagent.agent.collaboration.AgentCollaborationCoordinator.QUALITY_FAILOVER_THRESHOLD
                    && collabResult.mode() != com.yupi.yuaiagent.agent.collaboration.CollaborationResult.Mode.FAILOVER
                    && collabResult.primaryIntent() != AgentIntent.GENERAL) {
                emitter.send(SseEmitter.event().name("collaboration").data(
                        "{\"mode\":\"QUALITY_FAILOVER\",\"score\":" + qualityReview.getOverallScore() + "}"));
                var failover = collaborationCoordinator.failoverAfterQuality(
                        collabResult.primaryIntent(),
                        qualityReview.getSummary() != null ? qualityReview.getSummary()
                                : "score=" + qualityReview.getOverallScore(),
                        message, chatId, userId,
                        (intent, extraInjection) -> invokeExpertSync(intent, message, chatId,
                                mergeInjection(baseInjection, extraInjection)),
                        collabResult.opinions());
                fullAnswer = failover.finalAnswer() != null ? failover.finalAnswer() : fullAnswer;
                primaryIntent = failover.failoverIntent() != null ? failover.failoverIntent() : AgentIntent.GENERAL;
                chunkAndPersistAnswer(chatId, primaryIntent, fullAnswer, emitter);
            }
        }

        long durationMs = System.currentTimeMillis() - turnStart;
        int approxChars = fullAnswer != null ? fullAnswer.length() : 0;
        emitter.send(SseEmitter.event().name("usage").data(
                "{\"approxChars\":" + approxChars
                        + ",\"approxTokens\":" + Math.max(1, approxChars / 2)
                        + ",\"durationMs\":" + durationMs
                        + ",\"mode\":\"" + (intents.size() > 1 ? "PARALLEL_DEBATE" : "SINGLE") + "\"}"));

        // Finalize trace
        traceRecorder.endTrace(traceCtx);
        traceCtx.markSseClosed();
        persistTrace(traceCtx);
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
     * True token-level SSE for a single specialist; persists assistant message.
     */
    private String streamSingleExpert(AgentIntent intent, String message, String chatId,
                                      String injection, SseEmitter emitter, TraceContext traceCtx,
                                      TraceSpan subSpan) {
        String memoryType = memoryTypeOf(intent);
        contextInjectionService.syncCrossAgentMemory(chatId, memoryType, memoryType);

        Flux<String> tokenFlux = switch (intent) {
            case RESUME -> resumeAgent.chatStream(message, chatId, injection);
            case NEGOTIATION -> negotiationAgent.chatStream(message, chatId, injection);
            case ESCAPE -> escapeAgent.chatStream(message, chatId, injection);
            case CONSULTATION -> consultationAgent.chatStream(message, chatId, injection);
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

        StringBuilder agentAnswer = new StringBuilder();
        java.util.concurrent.atomic.AtomicLong lastFlush =
                new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
        try {
            tokenFlux.doOnNext(token -> {
                try {
                    agentAnswer.append(token);
                    emitter.send(SseEmitter.event().name("message").data(token));
                    long now = System.currentTimeMillis();
                    if (now - lastFlush.get() >= 500L || agentAnswer.length() % 40 == 0) {
                        chatMemoryAdapter.updateAssistantPartial(
                                streamingMsg.getMessageId(), agentAnswer.toString());
                        lastFlush.set(now);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).blockLast();
            chatMemoryAdapter.completeAssistant(streamingMsg.getMessageId(), agentAnswer.toString());
            if (subSpan != null) {
                traceRecorder.putMetadata(subSpan, "agentType", memoryType);
            }
            return agentAnswer.toString();
        } catch (Exception e) {
            log.error("Agent {} stream failed", intent.name(), e);
            if (subSpan != null && traceCtx != null) {
                traceRecorder.failSpan(traceCtx, subSpan, e.getMessage());
            }
            if (agentAnswer.isEmpty()) {
                agentAnswer.append("（该专家暂时无法回答）");
            }
            chatMemoryAdapter.updateAssistantPartial(streamingMsg.getMessageId(), agentAnswer.toString());
            chatMemoryAdapter.markAssistantPartial(streamingMsg.getMessageId());
            return agentAnswer.toString();
        }
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
     * Synchronously invoke a specialist agent (used by parallel collaboration).
     */
    private String invokeExpertSync(AgentIntent intent, String message, String chatId,
                                    String injection) {
        String memoryType = memoryTypeOf(intent);
        contextInjectionService.syncCrossAgentMemory(chatId, memoryType, memoryType);

        Flux<String> tokenFlux = switch (intent) {
            case RESUME -> resumeAgent.chatStream(message, chatId, injection);
            case NEGOTIATION -> negotiationAgent.chatStream(message, chatId, injection);
            case ESCAPE -> escapeAgent.chatStream(message, chatId, injection);
            case CONSULTATION -> consultationAgent.chatStream(message, chatId, injection);
            case DATA_QUERY -> generalCareerAgent.chatStream(message, chatId,
                    mergeInjection(injection, DATA_QUERY_FALLBACK_NOTE));
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

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ");
    }

    private String memoryTypeOf(AgentIntent intent) {
        return switch (intent) {
            case RESUME -> "resume";
            case NEGOTIATION -> "negotiation";
            case ESCAPE -> "escape";
            case CONSULTATION -> "consultation";
            case DATA_QUERY -> "data_query";
            default -> "general";
        };
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
}
