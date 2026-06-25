package com.yupi.yuaiagent.nlu;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.nlu.NluContext.AliasMatch;
import com.yupi.yuaiagent.nlu.UnifiedNluExtractor.IntentScore;
import com.yupi.yuaiagent.nlu.UnifiedNluExtractor.NluExtraction;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

/**
 * NLU Pipeline V4.2 — final production design.
 *
 * <pre>
 * User message
 *   → AliasResolver (metadata only)
 *   → UnifiedNluExtractor (1× LLM: intents + slots + domain + action)
 *   → IntentReranker (alias domain signal)
 *   → IntentAmbiguityDetector (same-category check)
 *   → RouteTemplate (domain.action.metric)
 *   → ContextShiftDetector (follow-up / entity-switch / new-query)
 *   → ConversationState.smartMerge (3-way)
 *   → IntentRequirementRegistry (required/optional slots)
 *   → ClarificationHandler (template, 0× LLM)
 *   → NluResult
 * </pre>
 *
 * Total LLM calls: 1. Total template calls: 1 (clarification, only if needed).
 *
 * @author jsq
 */
@Slf4j
public class NluPipeline {

    private final ConversationStateStore stateStore;
    private final AliasResolver aliasResolver;
    private final UnifiedNluExtractor extractor;
    private final IntentReranker intentReranker;
    private final IntentAmbiguityDetector ambiguityDetector;
    private final RouteTemplate routeTemplate;
    private final ContextShiftDetector shiftDetector;
    private final IntentRequirementRegistry requirementRegistry;
    private final ClarificationHandler clarificationHandler;

    public NluPipeline(ConversationStateStore stateStore,
                       AliasResolver aliasResolver,
                       UnifiedNluExtractor extractor,
                       IntentReranker intentReranker,
                       IntentAmbiguityDetector ambiguityDetector,
                       RouteTemplate routeTemplate,
                       @Qualifier("ruleContextShiftDetector") ContextShiftDetector shiftDetector,
                       IntentRequirementRegistry requirementRegistry,
                       ClarificationHandler clarificationHandler) {
        this.stateStore = stateStore;
        this.aliasResolver = aliasResolver;
        this.extractor = extractor;
        this.intentReranker = intentReranker;
        this.ambiguityDetector = ambiguityDetector;
        this.routeTemplate = routeTemplate;
        this.shiftDetector = shiftDetector;
        this.requirementRegistry = requirementRegistry;
        this.clarificationHandler = clarificationHandler;
    }

    public NluResult process(String message, String chatId) {
        // 1. Load current state
        ConversationState currentState = stateStore.get(chatId);

        // 2. Alias resolution (metadata only)
        List<AliasMatch> aliasMatches = aliasResolver.resolve(message);
        if (!aliasMatches.isEmpty()) {
            log.info("[NLU] Alias matches: {}", aliasMatches);
        }

        // 3. Build NluContext (state + aliases separated)
        NluContext nluContext = new NluContext(currentState, aliasMatches);

        // 4. Unified NLU extraction (single LLM call)
        // 添加降级兜底：LLM 调用失败时 fallback 到 UNKNOWN 意图，不阻断用户请求
        NluExtraction extraction;
        try {
            extraction = extractor.extract(message, nluContext);
        } catch (Exception e) {
            log.error("[NLU] LLM extraction failed, falling back to UNKNOWN intent: {}", e.getMessage(), e);
            extraction = NluExtraction.empty();
        }

        // 5. Re-rank intents using alias domain signal
        List<IntentScore> rerankedIntents = intentReranker.rerank(extraction.intents(), aliasMatches);

        // 6. Check ambiguity on re-ranked scores
        IntentAmbiguityDetector.AmbiguityResult ambiguity = ambiguityDetector.check(rerankedIntents);

        // 7. Resolve intent from re-ranked scores
        NluIntent resolvedIntent;
        if (!rerankedIntents.isEmpty()) {
            try {
                resolvedIntent = NluIntent.valueOf(rerankedIntents.get(0).intent());
            } catch (IllegalArgumentException e) {
                resolvedIntent = NluIntent.UNKNOWN;
            }
        } else {
            resolvedIntent = NluIntent.UNKNOWN;
        }

        // 8. Generate route hint
        String specificRoute = routeTemplate.resolve(extraction.domain(), extraction.action(), extraction.metric());

        // 9. Context shift detection
        ContextShiftDetector.ShiftType shiftType = shiftDetector.detect(message, currentState, extraction);

        // 10. Build fresh state from extraction
        ConversationState fresh = new ConversationState();
        fresh.setEntity(extraction.entity());
        fresh.setMetric(extraction.metric());
        fresh.setTimeRange(extraction.timeRange());
        fresh.setDimension(extraction.dimension());
        fresh.setResolvedIntent(resolvedIntent);
        fresh.setConfidence(extraction.confidence());

        // 11. Smart merge
        ConversationState merged = currentState.smartMerge(fresh, resolvedIntent, shiftType);

        // 12. Clarification check
        String clarification = null;
        boolean needsClarification = false;

        if (ambiguity.isAmbiguous()) {
            clarification = "您的问题可能涉及多个领域，能否具体描述一下？";
            needsClarification = true;
        } else {
            List<String> missingRequired = requirementRegistry.findMissingRequired(
                resolvedIntent.name(), specificRoute, merged);
            if (!missingRequired.isEmpty()) {
                clarification = clarificationHandler.clarify(merged, missingRequired);
                needsClarification = true;
            }
        }

        // 13. Build RouteHint
        RouteHint routeHint = new RouteHint(
            resolvedIntent.name(), specificRoute, extraction.confidence(),
            merged.getEntity(), merged.getMetric(), merged.getTimeRange());

        // 14. Persist state (only if no clarification needed)
        if (!needsClarification) {
            stateStore.save(chatId, merged);
        }

        log.info("[NLU] intent={}, confidence={}, route={}, entity={}, shift={}, clarification={}",
            resolvedIntent, extraction.confidence(), specificRoute,
            merged.getEntity(), shiftType, needsClarification ? "YES" : "NO");

        return new NluResult(message, merged, extraction, aliasMatches,
            rerankedIntents, routeHint, clarification, needsClarification);
    }

    @Data
    public static class NluResult {
        private final String originalMessage;
        private final ConversationState state;
        private final NluExtraction extraction;
        private final List<AliasMatch> aliasMatches;
        private final List<IntentScore> rerankedIntents;
        private final RouteHint routeHint;
        private final String clarification;
        private final boolean needsClarification;

        public AgentIntent toAgentIntent() {
            return routeHint.toAgentIntent();
        }
    }
}
