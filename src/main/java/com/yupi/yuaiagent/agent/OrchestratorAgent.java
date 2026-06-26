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
import java.util.concurrent.Executor;

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

    private final NluPipeline nluPipeline;
    private final DataQueryRouter dataQueryRouter;
    // V2 workflow infrastructure
    private final WorkflowMatcher workflowMatcher;
    private final WorkflowRegistry workflowRegistry;
    private final ConversationContextBuilder contextBuilder;
    private final TaskExecutor taskExecutor;
    private final ResultAggregator resultAggregator;
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

    /**
     * Constructor — uses aggregated {@link OrchestratorDependencies} to reduce parameter count.
     */
    public OrchestratorAgent(OrchestratorDependencies deps) {

        // 创建各专业 Agent
        this.resumeAgent = new ResumeAgent(deps.chatModel(), deps.vectorStore(), deps.queryRewriter(), deps.chatMemoryManager());
        this.negotiationAgent = new NegotiationAgent(deps.chatModel(), deps.tools(), deps.queryRewriter(), deps.chatMemoryManager());
        this.escapeAgent = new EscapeAgent(deps.chatModel(), deps.tools(), deps.queryRewriter(), deps.chatMemoryManager());
        this.generalCareerAgent = new GeneralCareerAgent(deps.chatModel(), deps.chatMemoryManager());
        this.consultationAgent = new ConsultationAgent(deps.chatModel(), deps.chatMemoryManager(), deps.templateConfig(), deps.infoValidator(), deps.calendarServiceFactory(), deps.appointmentRepository());
        this.skillExecutor = deps.skillExecutor();
        this.skillRegistry = deps.skillRegistry();
        this.chatMemoryManager = deps.chatMemoryManager();
        this.memoryCoordinator = deps.memoryCoordinator();
        this.traceRecorder = deps.traceRecorder();
        this.traceRepository = deps.traceRepository();
        this.chatMemoryAdapter = deps.chatMemoryAdapter();
        this.qualityReviewHandler = new QualityReviewHandler(deps.qualityGuardAgent(), deps.qualityModeResolver(), deps.qualityReviewRepository(), deps.traceRecorder());
        this.contextInjectionService = new ContextInjectionService(deps.userProfileService(), deps.artifactShelf(), deps.messageRepository(), deps.chatMemoryManager(), deps.traceRecorder());
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
        return switch (intent) {
            case RESUME -> resumeAgent.chat(message, chatId);
            case NEGOTIATION -> negotiationAgent.chat(message, chatId);
            case ESCAPE -> escapeAgent.chat(message, chatId);
            case CONSULTATION -> consultationAgent.chat(message, chatId);
            case DATA_QUERY -> "数据查询功能正在建设中";
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
        emitter.onTimeout(() -> {
            log.warn("[Orchestrator] SSE timeout for chatId={}", chatId);
            traceRecorder.failTrace(finalTraceCtx);
            persistTrace(finalTraceCtx);
        });
        emitter.onError(e -> {
            log.warn("[Orchestrator] SSE error for chatId={}: {}", chatId, e.getMessage());
            traceRecorder.failTrace(finalTraceCtx);
            persistTrace(finalTraceCtx);
        });

        CompletableFuture.runAsync(() -> {
            try {
                // 0. Prompt Injection detection
                var injectionResult = promptInjectionDetector.detect(message);
                if (!injectionResult.safe()) {
                    log.warn("[Orchestrator] Prompt injection blocked: type={}, pattern={}",
                            injectionResult.type(), injectionResult.pattern());
                    emitter.send(SseEmitter.event().name("error")
                            .data("检测到不安全的输入内容，请重新提问。"));
                    traceRecorder.failTrace(traceCtx);
                    persistTrace(traceCtx);
                    emitter.complete();
                    return;
                }

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
        }, agentExecutor);

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

        // Fast-path: short/simple messages skip NLU LLM call → go directly to GENERAL agent
        // This avoids 3-8s DashScope latency for simple greetings or vague messages
        boolean fastPath = !KeywordRouter.containsCareerKeyword(message);
        List<AgentIntent> intents;
        RouteHint routeHint;

        if (fastPath) {
            // Try rule-based keyword routing first (no LLM call)
            AgentIntent keywordIntent = KeywordRouter.keywordRouteIntent(message);
            if (keywordIntent != null) {
                TraceSpan nluSpan = traceRecorder.startSpan(traceCtx, TraceStepType.NLU, "NLU 快速路径（规则匹配）");
                traceRecorder.putMetadata(nluSpan, "intent", keywordIntent.name());
                traceRecorder.putMetadata(nluSpan, "confidence", "1.00");
                traceRecorder.putMetadata(nluSpan, "fastPath", "true");
                traceRecorder.endSpan(traceCtx, nluSpan);
                intents = List.of(keywordIntent);
                routeHint = new RouteHint(keywordIntent.name(), null, 1.0, null, null, null);
            } else {
                // No keyword match — fall through to full NLU
                fastPath = false;
            }
        }

        if (!fastPath) {
            // Complex query with career keywords — needs full NLU Pipeline (single LLM call)
            // Send progress feedback immediately so user doesn't stare at blank screen
            emitter.send(SseEmitter.event().name("routing").data("[正在分析你的问题...]"));

            TraceSpan nluSpan = traceRecorder.startSpan(traceCtx, TraceStepType.NLU, "NLU 意图理解");
            NluPipeline.NluResult nluResult = nluPipeline.process(message, chatId);
            traceRecorder.putMetadata(nluSpan, "intent", nluResult.getRouteHint().intent());
            traceRecorder.putMetadata(nluSpan, "confidence",
                String.format("%.2f", nluResult.getRouteHint().confidence()));
            traceRecorder.putMetadata(nluSpan, "entity", nluResult.getState().getEntity());
            if (nluResult.getRouteHint().specificRoute() != null) {
                traceRecorder.putMetadata(nluSpan, "routeHint", nluResult.getRouteHint().specificRoute());
            }
            traceRecorder.endSpan(traceCtx, nluSpan);

            // Clarification
            if (nluResult.isNeedsClarification()) {
                emitter.send(SseEmitter.event().name("clarification").data(nluResult.getClarification()));
                emitter.complete();
                return;
            }

            // Resolve multi-intents from NLU Pipeline
            intents = AgentIntent.fromMultiIntent(nluResult.getRerankedIntents());
            routeHint = nluResult.getRouteHint();
        }

        // ROUTING span — list all target agents
        String agentNames = intents.stream().map(AgentIntent::getAgentName).collect(java.util.stream.Collectors.joining(", "));
        TraceSpan routingSpan = traceRecorder.startSpan(traceCtx, TraceStepType.ROUTING, "路由到" + agentNames);
        emitter.send(SseEmitter.event().name("routing").data("[路由到" + agentNames + "]"));
        traceRecorder.putMetadata(routingSpan, "targetAgents", intents.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(",")));
        traceRecorder.endSpan(traceCtx, routingSpan);

        // Pre-compute shared context (profile + artifacts + cross-agent history)
        String combinedInjection = contextInjectionService.buildCombinedInjection(userId, chatId, traceCtx);

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

        // ─── Multi-intent serial execution (V1 群聊模式) ───
        StringBuilder combinedAnswer = new StringBuilder();

        for (AgentIntent intent : intents) {
            String memoryType = memoryTypeOf(intent);
            contextInjectionService.syncCrossAgentMemory(chatId, memoryType, memoryTypeOf(intent));

            // MEMORY_COMPRESSION
            TraceSpan memorySpan = traceRecorder.startSpan(traceCtx, TraceStepType.MEMORY_COMPRESSION, "记忆压缩检查");
            chatMemoryManager.autoCompressIfNeeded(memoryType, chatId, traceCtx, status -> {
                try { emitter.send(SseEmitter.event().name("status").data(status)); }
                catch (IOException e) { throw new RuntimeException(e); }
            });
            traceRecorder.endSpan(traceCtx, memorySpan);

            // Agent turn event
            emitter.send(SseEmitter.event().name("agent-turn").data(
                "{\"agentType\":\"" + intent.name() + "\",\"agentName\":\"" + intent.getAgentName() + "\"}"));

            // SUB_AGENT_EXECUTION span
            TraceSpan subAgentSpan = traceRecorder.startSpan(traceCtx, TraceStepType.SUB_AGENT_EXECUTION,
                intent.getAgentName() + "执行");
            traceRecorder.putMetadata(subAgentSpan, "agentType", memoryType);

            // Execute agent
            Flux<String> tokenFlux = switch (intent) {
                case RESUME -> resumeAgent.chatStream(message, chatId, combinedInjection);
                case NEGOTIATION -> negotiationAgent.chatStream(message, chatId, combinedInjection);
                case ESCAPE -> escapeAgent.chatStream(message, chatId, combinedInjection);
                case CONSULTATION -> consultationAgent.chatStream(message, chatId, combinedInjection);
                case DATA_QUERY -> dataQueryRouter.chatStream(routeHint, message, chatId);
                default -> generalCareerAgent.chatStream(message, chatId, combinedInjection);
            };

            // Collect answer (blocking per agent — serial execution)
            StringBuilder agentAnswer = new StringBuilder();
            try {
                tokenFlux.doOnNext(token -> {
                    try {
                        agentAnswer.append(token);
                        emitter.send(SseEmitter.event().name("message").data(token));
                    } catch (IOException e) { throw new RuntimeException(e); }
                }).blockLast(); // Block until Flux completes (replaces anti-pattern toStream().count())
                traceRecorder.endSpan(traceCtx, subAgentSpan);
            } catch (Exception e) {
                log.error("Agent {} 执行出错", intent.name(), e);
                traceRecorder.failSpan(traceCtx, subAgentSpan, e.getMessage());
                agentAnswer.append("（该专家暂时无法回答）");
            }

            // Persist agent answer with source tracking
            combinedAnswer.append(agentAnswer);
            chatMemoryAdapter.addAssistantMessage(chatId, agentAnswer.toString(),
                MessageSource.AGENT, intent.name(), intent.getAgentName());
        }

        // Quality review on combined answer
        String fullAnswer = combinedAnswer.toString();
        AgentIntent primaryIntent = intents.get(0);
        qualityReviewHandler.review(message, fullAnswer, chatId, primaryIntent, traceCtx, emitter);

        // Persist user message
        chatMemoryAdapter.addUserMessage(chatId, message, MessageSource.USER, null, null);

        // Finalize trace
        traceRecorder.endTrace(traceCtx);
        traceCtx.markSseClosed();
        persistTrace(traceCtx);
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
        contextInjectionService.triggerProfileUpdate(userId, memoryTypeOf(primaryIntent), chatId, traceCtx);

        // L27: Trigger memory extraction pipeline (async, non-blocking)
        if (memoryCoordinator != null && StringUtils.hasText(userId)) {
            try {
                memoryCoordinator.onTurnCompleted(userId, chatId, message, fullAnswer);
            } catch (Exception e) {
                log.warn("[MemoryCoordinator] extraction trigger failed: {}", e.getMessage());
            }
        }
    }

    // queryReadyArtifacts, buildArtifactContext, buildCrossAgentContext,
    // markArtifactsConsumed, mergeInjection → delegated to ContextInjectionService

    // runQualityReview → delegated to QualityReviewHandler

    // triggerProfileUpdate → delegated to ContextInjectionService

    /**
     * 将路由意图映射为子 Agent 在 ChatMemoryManager 中使用的记忆类型 key。
     */
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

    // containsCareerKeyword, keywordRouteIntent → delegated to KeywordRouter
}
