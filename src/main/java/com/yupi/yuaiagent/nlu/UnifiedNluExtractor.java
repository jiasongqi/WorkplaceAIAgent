package com.yupi.yuaiagent.nlu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Unified NLU extractor — single LLM call produces intent ranking + slots + domain + action.
 *
 * <p>Replaces both SlotExtractor and IntentClassifier (V2) to halve latency.
 * Aliases are passed as a HINT via NluContext, NOT injected into state.
 *
 * @author jsq
 */
@Slf4j
@Component
public class UnifiedNluExtractor {

    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String NLU_PROMPT = """
        You are a structured NLU engine. Analyze the user message and output a single JSON object.

        ## Intent Taxonomy
        - RESUME_OPTIMIZE: resume editing, resume polishing
        - INTERVIEW_PREP: interview prep, mock interview
        - JOB_CHANGE: job switching, job hunting, offer evaluation
        - SALARY_ANALYZE: salary analysis, compensation benchmarking
        - SALARY_NEGOTIATE: raise request, salary negotiation
        - LEAVE_PLAN: resignation, layoff, offboarding
        - CONSULTATION: booking expert consultation
        - QUERY_DATA: data query, metrics lookup, report viewing, KPI check
        - CAREER_GENERAL: career advice, workplace relationships, planning
        - UNKNOWN: cannot determine

        ## Output Format (strict JSON, no markdown fences)
        {
          "intents": [
            {"intent": "QUERY_DATA", "score": 0.7},
            {"intent": "SALARY_ANALYZE", "score": 0.2}
          ],
          "entity": "腾讯" or null,
          "metric": "ROI" or null,
          "timeRange": "7d" or null,
          "dimension": null,
          "domain": "ADVERTISER" or null,
          "action": "QUERY" or null
        }

        ## Domain Values
        - ADVERTISER: advertiser/资方 related
        - RESUME: resume/job related
        - SALARY: salary/compensation related
        - CAREER: general career advice
        - CONSULTATION: booking/scheduling
        - null: cannot determine domain

        ## Action Values
        - QUERY: data query, metrics lookup
        - ANALYZE: analysis, comparison, evaluation
        - OPTIMIZE: editing, improving, polishing
        - CREATE: creating, generating, drafting
        - BOOK: scheduling, reserving
        - null: cannot determine action

        ## Rules
        1. Rank intents by likelihood. Include top 2-3. Scores are relative weights (sum ~1.0).
           NOTE: scores are approximate reference values, not calibrated probabilities.
        2. Extract entity/metric/timeRange/dimension ONLY if explicitly mentioned in THIS message.
           Do NOT copy from previous state — state is provided only for reference.
        3. Normalize timeRange: "7天"→"7d", "昨天"→"1d", "本月"→"this_month".
        4. If aliases are provided, use the canonical name for entity extraction.
           Example: message says "TX", aliases say TX=腾讯资方 → entity="腾讯资方"

        ## Previous Conversation State (for reference only — do NOT copy fields)
        {previousState}

        ## Alias Hints (for entity resolution only)
        {aliasHint}

        ## User Message
        {message}
        """;

    public UnifiedNluExtractor(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    /**
     * Single LLM call: extract intent + slots + domain + action.
     */
    public NluExtraction extract(String message, NluContext nluContext) {
        ConversationState state = nluContext.state();
        String stateJson = state != null ? toJson(state) : "{}";
        String aliasHint = nluContext.aliasHint();

        String response = chatClient.prompt()
            .user(NLU_PROMPT
                .replace("{message}", message)
                .replace("{previousState}", stateJson)
                .replace("{aliasHint}", aliasHint))
            .call()
            .content();

        return parse(response);
    }

    private String toJson(ConversationState state) {
        try {
            var map = new LinkedHashMap<String, Object>();
            map.put("entity", state.getEntity());
            map.put("metric", state.getMetric());
            map.put("timeRange", state.getTimeRange());
            map.put("dimension", state.getDimension());
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private NluExtraction parse(String raw) {
        try {
            String cleaned = raw.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").strip();
            }
            JsonNode root = mapper.readTree(cleaned);

            var intents = new ArrayList<IntentScore>();
            if (root.has("intents") && root.get("intents").isArray()) {
                for (JsonNode n : root.get("intents")) {
                    intents.add(new IntentScore(
                        n.has("intent") ? n.get("intent").asText() : "UNKNOWN",
                        n.has("score") ? n.get("score").asDouble() : 0.0));
                }
            }

            return new NluExtraction(
                intents,
                getTextOrNull(root, "entity"),
                getTextOrNull(root, "metric"),
                getTextOrNull(root, "timeRange"),
                getTextOrNull(root, "dimension"),
                getTextOrNull(root, "domain"),
                getTextOrNull(root, "action"));

        } catch (Exception e) {
            log.warn("[NLU] Parse failed: {}", raw, e);
            return NluExtraction.empty();
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) return node.get(field).asText();
        return null;
    }

    // ─── Result Types ───

    public record IntentScore(String intent, double score) {}

    public record NluExtraction(
        List<IntentScore> intents,
        String entity, String metric, String timeRange, String dimension,
        String domain, String action
    ) {
        public String topIntent() {
            return intents.isEmpty() ? "UNKNOWN" : intents.get(0).intent();
        }

        /**
         * Confidence = Top1 score - Top2 score.
         * NOTE: rough heuristic — scores are LLM-estimated, not calibrated.
         */
        public double confidence() {
            if (intents.isEmpty()) return 0.0;
            double top1 = intents.get(0).score();
            double top2 = intents.size() > 1 ? intents.get(1).score() : 0.0;
            return top1 - top2;
        }

        public NluIntent resolvedIntent() {
            try {
                return NluIntent.valueOf(topIntent());
            } catch (IllegalArgumentException e) {
                return NluIntent.UNKNOWN;
            }
        }

        public static NluExtraction empty() {
            return new NluExtraction(List.of(), null, null, null, null, null, null);
        }
    }
}
