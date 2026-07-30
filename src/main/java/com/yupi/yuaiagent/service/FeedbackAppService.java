package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.agent.reflexion.ReflexionService;
import com.yupi.yuaiagent.feedback.Feedback;
import com.yupi.yuaiagent.feedback.FeedbackRepository;
import com.yupi.yuaiagent.memory.fact.FactCategory;
import com.yupi.yuaiagent.memory.fact.FactEntry;
import com.yupi.yuaiagent.memory.fact.FactStoreLayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feedback closed loop: persist rating, then write back into Reflexion / Fact store.
 * Does not fine-tune the model — next-turn prompt injection is the evolution path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackAppService {

    private final FeedbackRepository feedbackRepository;
    private final ReflexionService reflexionService;
    @Nullable
    private final FactStoreLayer factStoreLayer;

    public void submit(String userId, String chatId, String messageId,
                       Feedback.Rating rating, String comment,
                       String agentType, String intent) {
        Feedback feedback = new Feedback(
                UUID.randomUUID().toString(),
                userId,
                chatId,
                messageId,
                rating,
                comment,
                agentType,
                intent,
                LocalDateTime.now());
        feedbackRepository.save(feedback);

        String taskType = resolveTaskType(agentType, intent);
        if (rating == Feedback.Rating.DOWN) {
            String error = StringUtils.hasText(comment)
                    ? comment
                    : "用户对回答点踩：不满意当前回复质量或方向";
            reflexionService.recordFailure(userId, taskType, error, null);
            log.info("[Feedback] DOWN recorded for user={}, taskType={}", userId, taskType);
        } else if (rating == Feedback.Rating.UP && factStoreLayer != null && StringUtils.hasText(userId)) {
            try {
                factStoreLayer.upsert(userId, new FactEntry(
                        "feedback_liked_" + taskType.toLowerCase(),
                        "用户认可该方向的回答风格与内容（agent=" + taskType + "）",
                        FactCategory.PREFERENCES,
                        chatId,
                        Instant.now()));
            } catch (Exception e) {
                log.debug("[Feedback] UP fact upsert skipped: {}", e.getMessage());
            }
        }
    }

    public FeedbackStatsView getStats() {
        long totalUp = feedbackRepository.countByRating(Feedback.Rating.UP);
        long totalDown = feedbackRepository.countByRating(Feedback.Rating.DOWN);
        double approvalRate = feedbackRepository.getApprovalRate();

        Map<String, AgentIntentStats> byAgent = new LinkedHashMap<>();
        Map<String, AgentIntentStats> byIntent = new LinkedHashMap<>();

        for (Feedback f : feedbackRepository.findAll()) {
            accumulate(byAgent, f.agentType() != null ? f.agentType() : "UNKNOWN", f.rating());
            accumulate(byIntent, f.intent() != null ? f.intent() : "UNKNOWN", f.rating());
        }

        return new FeedbackStatsView(totalUp, totalDown, approvalRate,
                new ArrayList<>(byAgent.entrySet().stream()
                        .map(e -> e.getValue().toView(e.getKey()))
                        .toList()),
                new ArrayList<>(byIntent.entrySet().stream()
                        .map(e -> e.getValue().toView(e.getKey()))
                        .toList()));
    }

    private static void accumulate(Map<String, AgentIntentStats> map, String key, Feedback.Rating rating) {
        AgentIntentStats stats = map.computeIfAbsent(key, k -> new AgentIntentStats());
        if (rating == Feedback.Rating.UP) {
            stats.up++;
        } else {
            stats.down++;
        }
    }

    private static String resolveTaskType(String agentType, String intent) {
        if (StringUtils.hasText(agentType)) {
            return agentType;
        }
        if (StringUtils.hasText(intent)) {
            return intent;
        }
        return "GENERAL";
    }

    private static final class AgentIntentStats {
        long up;
        long down;

        Breakdown toView(String key) {
            long total = up + down;
            double rate = total == 0 ? -1.0 : (double) up / total;
            return new Breakdown(key, up, down, rate);
        }
    }

    public record Breakdown(String key, long thumbsUp, long thumbsDown, double approvalRate) {
    }

    public record FeedbackStatsView(
            long thumbsUp,
            long thumbsDown,
            double approvalRate,
            List<Breakdown> byAgentType,
            List<Breakdown> byIntent
    ) {
    }
}
