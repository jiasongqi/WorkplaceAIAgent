package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.feedback.Feedback;
import com.yupi.yuaiagent.feedback.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Feedback Controller — captures user ratings on agent responses.
 *
 * @author jsq
 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;

    @PostMapping
    public Response<String> submitFeedback(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam String chatId,
            @RequestParam String messageId,
            @RequestParam Feedback.Rating rating,
            @RequestParam(required = false) String comment,
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String intent) {

        String userId = extractUserId(authHeader);

        Feedback feedback = new Feedback(
                UUID.randomUUID().toString(),
                userId, chatId, messageId,
                rating, comment, agentType, intent,
                LocalDateTime.now());

        feedbackRepository.save(feedback);
        return Response.success("feedback recorded");
    }

    @GetMapping("/stats")
    public Response<FeedbackStats> getStats() {
        long totalUp = feedbackRepository.countByRating(Feedback.Rating.UP);
        long totalDown = feedbackRepository.countByRating(Feedback.Rating.DOWN);
        double approvalRate = feedbackRepository.getApprovalRate();
        return Response.success(new FeedbackStats(totalUp, totalDown, approvalRate));
    }

    private String extractUserId(String authHeader) {
        // Simplified — in production, decode JWT
        return authHeader != null ? authHeader.substring(0, Math.min(20, authHeader.length())) : "anonymous";
    }

    public record FeedbackStats(long thumbsUp, long thumbsDown, double approvalRate) {}
}
