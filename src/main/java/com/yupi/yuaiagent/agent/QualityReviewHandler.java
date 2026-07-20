package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.quality.*;
import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceRecorder;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Handles quality review after an agent produces an answer.
 *
 * <p>Extracted from OrchestratorAgent to reduce god-class complexity.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Resolve quality mode (AUTO → intent + LLM risk classification)</li>
 *   <li>Run REVIEW or RED_TEAM review</li>
 *   <li>Send quality-review SSE event</li>
 *   <li>Block if CRITICAL risk</li>
 *   <li>Persist HIGH/CRITICAL reviews for audit</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
public class QualityReviewHandler {

    private final QualityGuardAgent qualityGuardAgent;
    private final QualityModeResolver qualityModeResolver;
    private final QualityReviewRepository qualityReviewRepository;
    private final TraceRecorder traceRecorder;

    public QualityReviewHandler(QualityGuardAgent qualityGuardAgent,
                                QualityModeResolver qualityModeResolver,
                                QualityReviewRepository qualityReviewRepository,
                                TraceRecorder traceRecorder) {
        this.qualityGuardAgent = qualityGuardAgent;
        this.qualityModeResolver = qualityModeResolver;
        this.qualityReviewRepository = qualityReviewRepository;
        this.traceRecorder = traceRecorder;
    }

    /**
     * Runs quality review after agent answer is complete.
     * Sends quality-review SSE event. Blocks answer if CRITICAL risk.
     *
     * @return review result, or null if skipped / failed
     */
    public QualityReview review(String userQuestion, String agentAnswer, String chatId,
                                AgentIntent intent, TraceContext traceCtx, SseEmitter emitter) {
        if (agentAnswer == null || agentAnswer.isBlank()) {
            return null;
        }

        QualityMode mode = qualityModeResolver.resolve(userQuestion, intent, QualityMode.AUTO);
        if (mode == QualityMode.OFF) {
            return null;
        }

        TraceSpan reviewSpan = traceRecorder.startSpan(traceCtx, TraceStepType.QUALITY_REVIEW, "质量审查");
        try {
            QualityReview qualityReview;
            if (mode == QualityMode.RED_TEAM) {
                qualityReview = qualityGuardAgent.redTeamReview(userQuestion, agentAnswer, chatId);
            } else {
                qualityReview = qualityGuardAgent.review(userQuestion, agentAnswer, chatId);
            }

            traceRecorder.putMetadata(reviewSpan, "overallScore", String.valueOf(qualityReview.getOverallScore()));
            traceRecorder.putMetadata(reviewSpan, "riskLevel", qualityReview.getRiskLevel().name());
            traceRecorder.putMetadata(reviewSpan, "mode", mode.name());
            traceRecorder.endSpan(traceCtx, reviewSpan);

            qualityReviewRepository.saveIfHighRisk(qualityReview);

            try {
                emitter.send(SseEmitter.event().name("quality-review").data(qualityReview));
            } catch (IOException e) {
                log.debug("Failed to send quality-review SSE event", e);
            }

            if (qualityReview.getRiskLevel().isBlocking()) {
                TraceSpan blockedSpan = traceRecorder.startSpan(traceCtx, TraceStepType.QUALITY_BLOCKED, "质量阻断");
                traceRecorder.putMetadata(blockedSpan, "reason", qualityReview.getSummary());
                traceRecorder.endSpan(traceCtx, blockedSpan);

                try {
                    emitter.send(SseEmitter.event().name("quality-blocked").data(
                            "⚠️ 该回答已被质量守卫阻断。风险原因：" + qualityReview.getSummary() + "。建议咨询相关领域的专业人士。"));
                } catch (IOException e) {
                    log.debug("Failed to send quality-blocked SSE event", e);
                }
            }

            log.info("[QualityGuard] mode={}, overall={}, risk={}", mode, qualityReview.getOverallScore(), qualityReview.getRiskLevel());
            return qualityReview;

        } catch (Exception e) {
            log.error("Quality review failed, continuing normally", e);
            traceRecorder.failSpan(traceCtx, reviewSpan, e.getMessage());
            return null;
        }
    }
}
