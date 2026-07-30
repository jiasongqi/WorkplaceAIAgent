package com.yupi.yuaiagent.agent.collaboration;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.agent.ResultAggregator;
import com.yupi.yuaiagent.agent.reflexion.ReflexionService;
import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactScope;
import com.yupi.yuaiagent.artifact.model.ArtifactStatus;
import com.yupi.yuaiagent.metrics.AgentExecutionMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Multi-agent collaboration coordinator — upgrades "boss dispatches one worker"
 * into real collaboration patterns:
 * <ul>
 *   <li><b>Parallel debate</b>: multiple specialists answer concurrently, then synthesize</li>
 *   <li><b>Failover with reason</b>: failed / low-quality expert → GENERAL with explanation</li>
 *   <li><b>Blackboard handoff</b>: write HANDOFF / DEBATE artifacts for next-turn injection</li>
 * </ul>
 *
 * <p>Does not own the agents themselves — callers supply an executor function per intent.
 */
@Slf4j
@Component
public class AgentCollaborationCoordinator {

    public static final String ARTIFACT_TYPE_DEBATE = "MULTI_AGENT_DEBATE";
    public static final String ARTIFACT_TYPE_HANDOFF = "AGENT_HANDOFF";
    /** Soft quality floor — below this, trigger failover to GENERAL */
    public static final int QUALITY_FAILOVER_THRESHOLD = 50;

    private final ResultAggregator resultAggregator;
    private final ArtifactShelf artifactShelf;
    private final ReflexionService reflexionService;
    private final AgentExecutionMetrics executionMetrics;
    private final Executor agentExecutor;

    public AgentCollaborationCoordinator(
            ResultAggregator resultAggregator,
            ArtifactShelf artifactShelf,
            ReflexionService reflexionService,
            AgentExecutionMetrics executionMetrics,
            @Qualifier("agentExecutor") Executor agentExecutor) {
        this.resultAggregator = resultAggregator;
        this.artifactShelf = artifactShelf;
        this.reflexionService = reflexionService;
        this.executionMetrics = executionMetrics;
        this.agentExecutor = agentExecutor;
    }

    /**
     * Functional interface: (intent, injectionHint) → answer text.
     * Injection hint may carry failover reason or debate context.
     */
    @FunctionalInterface
    public interface ExpertInvoker {
        String invoke(AgentIntent intent, String extraInjection) throws Exception;
    }

    /**
     * SSE progress callback so callers (e.g. OrchestratorAgent) can stream per-expert
     * start/finish events to the client during parallel debate / single-agent execution.
     */
    public interface ProgressListener {
        default void onExpertStarted(AgentIntent intent) {}
        default void onExpertFinished(AgentIntent intent, boolean success, long durationMs) {}

        ProgressListener NOOP = new ProgressListener() {};
    }

    /**
     * Run parallel debate when ≥2 intents; otherwise single-agent path.
     * On total failure, failover to GENERAL with reason.
     */
    public CollaborationResult collaborate(
            List<AgentIntent> intents,
            String userMessage,
            String chatId,
            String userId,
            ExpertInvoker invoker) {
        return collaborate(intents, userMessage, chatId, userId, invoker, ProgressListener.NOOP);
    }

    /**
     * Same as {@link #collaborate(List, String, String, String, ExpertInvoker)} but reports
     * per-expert progress via {@code progressListener} (used for SSE "agent-progress" events).
     */
    public CollaborationResult collaborate(
            List<AgentIntent> intents,
            String userMessage,
            String chatId,
            String userId,
            ExpertInvoker invoker,
            ProgressListener progressListener) {

        if (intents == null || intents.isEmpty()) {
            intents = List.of(AgentIntent.GENERAL);
        }
        if (progressListener == null) {
            progressListener = ProgressListener.NOOP;
        }

        AgentIntent primary = intents.get(0);

        if (intents.size() == 1) {
            return runSingleWithFailover(primary, userMessage, chatId, userId, invoker, progressListener);
        }

        return runParallelDebate(intents, userMessage, chatId, userId, invoker, progressListener);
    }

    private CollaborationResult runSingleWithFailover(
            AgentIntent intent,
            String userMessage,
            String chatId,
            String userId,
            ExpertInvoker invoker,
            ProgressListener progressListener) {

        long start = System.currentTimeMillis();
        executionMetrics.recordExecutionStart(intent.name());
        progressListener.onExpertStarted(intent);
        try {
            String answer = invoker.invoke(intent, "");
            long duration = System.currentTimeMillis() - start;
            boolean ok = StringUtils.hasText(answer) && !answer.contains("暂时无法回答");
            executionMetrics.recordExecutionEnd(intent.name(), duration, 0, 0, 1, ok);
            progressListener.onExpertFinished(intent, ok, duration);

            if (ok) {
                return new CollaborationResult(
                        CollaborationResult.Mode.SINGLE, answer,
                        List.of(ExpertOpinion.ok(intent, answer, duration)),
                        intent, null, null, null);
            }
            return failover(intent, "专家返回空/失败占位回答", userMessage, chatId, userId, invoker,
                    List.of(ExpertOpinion.failed(intent, "empty_or_placeholder", duration)));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            executionMetrics.recordExecutionEnd(intent.name(), duration, 0, 0, 1, false);
            progressListener.onExpertFinished(intent, false, duration);
            log.warn("[Collaboration] {} failed: {}", intent.name(), e.getMessage());
            return failover(intent, e.getMessage(), userMessage, chatId, userId, invoker,
                    List.of(ExpertOpinion.failed(intent, e.getMessage(), duration)));
        }
    }

    private CollaborationResult runParallelDebate(
            List<AgentIntent> intents,
            String userMessage,
            String chatId,
            String userId,
            ExpertInvoker invoker,
            ProgressListener progressListener) {

        log.info("[Collaboration] PARALLEL_DEBATE intents={}",
                intents.stream().map(Enum::name).toList());

        List<CompletableFuture<ExpertOpinion>> futures = new ArrayList<>();
        for (AgentIntent intent : intents) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                executionMetrics.recordExecutionStart(intent.name());
                progressListener.onExpertStarted(intent);
                try {
                    String answer = invoker.invoke(intent, "");
                    long duration = System.currentTimeMillis() - start;
                    boolean ok = StringUtils.hasText(answer);
                    executionMetrics.recordExecutionEnd(intent.name(), duration, 0, 0, 1, ok);
                    progressListener.onExpertFinished(intent, ok, duration);
                    return ok
                            ? ExpertOpinion.ok(intent, answer, duration)
                            : ExpertOpinion.failed(intent, "empty_answer", duration);
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - start;
                    executionMetrics.recordExecutionEnd(intent.name(), duration, 0, 0, 1, false);
                    progressListener.onExpertFinished(intent, false, duration);
                    return ExpertOpinion.failed(intent, e.getMessage(), duration);
                }
            }, agentExecutor));
        }

        List<ExpertOpinion> opinions = futures.stream()
                .map(f -> {
                    try {
                        return f.get(120, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        return ExpertOpinion.failed(AgentIntent.GENERAL, "timeout: " + e.getMessage(), 0);
                    }
                })
                .toList();

        List<ExpertOpinion> successes = opinions.stream().filter(ExpertOpinion::success).toList();
        if (successes.isEmpty()) {
            String reason = "所有专家并行执行均失败: " + opinions.stream()
                    .map(o -> o.intent().name() + "=" + o.error())
                    .reduce((a, b) -> a + "; " + b).orElse("unknown");
            return failover(intents.get(0), reason, userMessage, chatId, userId, invoker, opinions);
        }

        // Synthesize via ResultAggregator (LLM when available, else structured concat)
        String synthesized = resultAggregator.synthesizeDebate(userMessage, successes);

        String artifactId = writeDebateArtifact(chatId, userId, userMessage, successes, synthesized);

        // If every expert but one failed, still ok; if quality of synthesis is tiny, failover
        if (!StringUtils.hasText(synthesized) || synthesized.length() < 20) {
            return failover(intents.get(0), "并行辩论综合结果过短", userMessage, chatId, userId, invoker, opinions);
        }

        return new CollaborationResult(
                CollaborationResult.Mode.PARALLEL_DEBATE,
                synthesized,
                opinions,
                intents.get(0),
                null,
                null,
                artifactId);
    }

    /**
     * Quality review rejected the answer — Request-Reply-Repair then failover:
     * <ol>
     *   <li>Same expert once with structured NACK (issues + suggestion)</li>
     *   <li>If repair still weak / throws → GENERAL failover</li>
     * </ol>
     */
    public CollaborationResult failoverAfterQuality(
            AgentIntent failedIntent,
            String qualityReason,
            String userMessage,
            String chatId,
            String userId,
            ExpertInvoker invoker,
            List<ExpertOpinion> priorOpinions) {
        return failoverAfterQuality(failedIntent, qualityReason, null, null,
                userMessage, chatId, userId, invoker, priorOpinions);
    }

    /**
     * Same as {@link #failoverAfterQuality(AgentIntent, String, String, String, String, ExpertInvoker, List)}
     * with optional quality issues / suggestions for richer NACK.
     */
    public CollaborationResult failoverAfterQuality(
            AgentIntent failedIntent,
            String qualityReason,
            List<String> issues,
            List<String> suggestions,
            String userMessage,
            String chatId,
            String userId,
            ExpertInvoker invoker,
            List<ExpertOpinion> priorOpinions) {

        String reason = "质量审查未通过: " + (qualityReason != null ? qualityReason : "low_score");

        // Already GENERAL — skip self-repair thrash; surface or honest message via failover()
        if (failedIntent == AgentIntent.GENERAL) {
            return failover(failedIntent, reason, userMessage, chatId, userId, invoker, priorOpinions);
        }

        // ── Attempt 1: same-expert SELF_REPAIR ──
        String repairInjection = buildQualityNackInjection(failedIntent, reason, issues, suggestions);
        long start = System.currentTimeMillis();
        executionMetrics.recordExecutionStart(failedIntent.name());
        try {
            String repaired = invoker.invoke(failedIntent, repairInjection);
            long duration = System.currentTimeMillis() - start;
            boolean ok = isAcceptableRepair(repaired);
            executionMetrics.recordExecutionEnd(failedIntent.name(), duration, 0, 0, 1, ok);

            if (ok) {
                log.info("[Collaboration] SELF_REPAIR {} succeeded after quality NACK", failedIntent.name());
                recordReflexion(userId, failedIntent, reason, "self_repair");
                String handoffId = writeHandoffArtifact(chatId, userId, failedIntent, failedIntent,
                        "self_repair: " + reason);
                List<ExpertOpinion> all = new ArrayList<>(priorOpinions != null ? priorOpinions : List.of());
                all.add(ExpertOpinion.ok(failedIntent, repaired, duration));
                return new CollaborationResult(
                        CollaborationResult.Mode.SELF_REPAIR,
                        repaired,
                        all,
                        failedIntent,
                        null,
                        reason,
                        handoffId);
            }
            log.info("[Collaboration] SELF_REPAIR {} too weak, escalating to GENERAL", failedIntent.name());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            executionMetrics.recordExecutionEnd(failedIntent.name(), duration, 0, 0, 1, false);
            log.warn("[Collaboration] SELF_REPAIR {} failed: {}", failedIntent.name(), e.getMessage());
            reason = reason + " / self_repair_error: " + e.getMessage();
        }

        // ── Attempt 2: failover GENERAL ──
        return failover(failedIntent, reason, userMessage, chatId, userId, invoker, priorOpinions);
    }

    private static String buildQualityNackInjection(AgentIntent intent, String reason,
                                                    List<String> issues, List<String> suggestions) {
        StringBuilder sb = new StringBuilder();
        sb.append("【Handoff NACK — 质量审查未通过，请自我修复后重答】\n");
        sb.append("- 你仍是「").append(intent.getAgentName()).append("」，不要换角色。\n");
        sb.append("- 失败原因：").append(reason).append('\n');
        if (issues != null && !issues.isEmpty()) {
            sb.append("- 问题点：").append(String.join("；", issues)).append('\n');
        }
        if (suggestions != null && !suggestions.isEmpty()) {
            sb.append("- 修复建议：").append(String.join("；", suggestions)).append('\n');
        } else {
            sb.append("- 修复建议：补全缺失信息、去掉幻觉引用、给出可执行步骤，勿重复上一版空话。\n");
        }
        sb.append("- 要求：直接输出改进后的完整回答，不要解释审查流程。\n");
        return sb.toString();
    }

    private static boolean isAcceptableRepair(String answer) {
        if (!StringUtils.hasText(answer)) {
            return false;
        }
        String t = answer.trim();
        // CJK answers are dense; 30 chars is enough to reject empty / one-liner repairs
        return t.length() >= 30;
    }

    private CollaborationResult failover(
            AgentIntent failedIntent,
            String reason,
            String userMessage,
            String chatId,
            String userId,
            ExpertInvoker invoker,
            List<ExpertOpinion> priorOpinions) {

        AgentIntent fallback = AgentIntent.GENERAL;
        if (failedIntent == AgentIntent.GENERAL) {
            // Already on GENERAL — don't recurse; surface honest failure
            String msg = "抱歉，当前专家暂时无法完整回答（原因：" + reason + "）。请稍后重试或换一种问法。";
            recordReflexion(userId, failedIntent, reason, "surfaced_error");
            String handoffId = writeHandoffArtifact(chatId, userId, failedIntent, null, reason);
            return new CollaborationResult(
                    CollaborationResult.Mode.FAILOVER, msg,
                    priorOpinions != null ? priorOpinions : List.of(),
                    failedIntent, null, reason, handoffId);
        }

        log.info("[Collaboration] FAILOVER {} → GENERAL, reason={}", failedIntent.name(), reason);
        recordReflexion(userId, failedIntent, reason, "failover_to_GENERAL");

        String injection = """
                【协作换人说明】
                原专家 %s 未能完成任务。
                失败原因：%s
                请你以职场通用顾问身份接手，给出可执行建议，并简要说明已换人接手。
                """.formatted(failedIntent.getAgentName(), reason);

        long start = System.currentTimeMillis();
        executionMetrics.recordExecutionStart(fallback.name());
        try {
            String answer = invoker.invoke(fallback, injection);
            long duration = System.currentTimeMillis() - start;
            executionMetrics.recordExecutionEnd(fallback.name(), duration, 0, 0, 1, true);

            String handoffId = writeHandoffArtifact(chatId, userId, failedIntent, fallback, reason);
            List<ExpertOpinion> all = new ArrayList<>(priorOpinions != null ? priorOpinions : List.of());
            all.add(ExpertOpinion.ok(fallback, answer, duration));

            return new CollaborationResult(
                    CollaborationResult.Mode.FAILOVER,
                    answer,
                    all,
                    failedIntent,
                    fallback,
                    reason,
                    handoffId);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            executionMetrics.recordExecutionEnd(fallback.name(), duration, 0, 0, 1, false);
            String msg = "抱歉，主专家与备用顾问均暂时不可用（" + reason + " / " + e.getMessage() + "）。";
            String handoffId = writeHandoffArtifact(chatId, userId, failedIntent, fallback, reason);
            return new CollaborationResult(
                    CollaborationResult.Mode.FAILOVER, msg,
                    priorOpinions != null ? priorOpinions : List.of(),
                    failedIntent, fallback, reason, handoffId);
        }
    }

    private void recordReflexion(String userId, AgentIntent intent, String error, String resolution) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        try {
            reflexionService.recordFailure(userId, intent.name(), error, resolution);
        } catch (Exception e) {
            log.warn("[Collaboration] reflexion record failed: {}", e.getMessage());
        }
    }

    private String writeDebateArtifact(String chatId, String userId, String question,
                                       List<ExpertOpinion> successes, String synthesized) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            StringBuilder content = new StringBuilder();
            content.append("question: ").append(question).append("\n\n");
            for (ExpertOpinion o : successes) {
                content.append("## ").append(o.intent().getAgentName()).append("\n")
                        .append(o.answer()).append("\n\n");
            }
            content.append("## 综合结论\n").append(synthesized);

            Artifact artifact = Artifact.builder()
                    .userId(userId)
                    .chatId(chatId)
                    .type(ARTIFACT_TYPE_DEBATE)
                    .producer("AgentCollaborationCoordinator")
                    .title("多专家并行辩论记录")
                    .content(content.toString())
                    .status(ArtifactStatus.PUBLISHED)
                    .reusable(false)
                    .scope(ArtifactScope.TASK)
                    .build();
            ArtifactShelf.PutResult put = artifactShelf.put(artifact);
            return put.success() && put.artifact() != null
                    ? put.artifact().getArtifactId() : null;
        } catch (Exception e) {
            log.warn("[Collaboration] debate artifact write failed: {}", e.getMessage());
            return null;
        }
    }

    private String writeHandoffArtifact(String chatId, String userId,
                                        AgentIntent from, AgentIntent to, String reason) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            boolean selfRepair = from != null && to != null && from == to;
            String objective = selfRepair ? "self_repair" : "failover_repair";
            String dod = selfRepair
                    ? "原专家按 NACK 修复后给出可执行回答"
                    : "备用顾问给出可执行回答";
            // Build raw then clean through middleware (Schema Promise Break guard)
            String raw = """
                    {
                      "meta": {"from":"%s","to":"%s","producer":"AgentCollaborationCoordinator"},
                      "mission": {"objective":"%s","definitionOfDone":"%s"},
                      "context": {"reason":"%s","userOriginalIntent":"quality_or_execution_failure"},
                      "artifacts": {},
                      "scope": ["general.*","rag.query"],
                      "targetAgent":"%s"
                    }
                    """.formatted(
                    from.name(),
                    to != null ? to.name() : "NONE",
                    objective,
                    dod,
                    reason == null ? "" : reason.replace("\"", "'").replace("\n", " "),
                    to != null ? to.name() : "NONE");

            String content = com.yupi.yuaiagent.sessionstate.HandoffPacketParser
                    .extractJsonObject(raw)
                    .orElseThrow(() -> new IllegalStateException("handoff JSON clean failed"));

            Artifact artifact = Artifact.builder()
                    .userId(userId)
                    .chatId(chatId)
                    .type(ARTIFACT_TYPE_HANDOFF)
                    .producer("AgentCollaborationCoordinator")
                    .title((selfRepair ? "质量自修复: " : "Agent 换人交接: ")
                            + from.name() + " → " + (to != null ? to.name() : "NONE"))
                    .content(content)
                    .status(ArtifactStatus.PUBLISHED)
                    .reusable(false)
                    .scope(ArtifactScope.TASK)
                    .build();
            ArtifactShelf.PutResult put = artifactShelf.put(artifact);
            return put.success() && put.artifact() != null
                    ? put.artifact().getArtifactId() : null;
        } catch (Exception e) {
            log.warn("[Collaboration] handoff artifact write failed: {}", e.getMessage());
            return null;
        }
    }
}
