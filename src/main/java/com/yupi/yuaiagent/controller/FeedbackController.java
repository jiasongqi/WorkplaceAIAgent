package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.feedback.Feedback;
import com.yupi.yuaiagent.feedback.FeedbackRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "反馈", description = "用户对AI回答的评分反馈")
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;

    @PostMapping
    @Operation(summary = "提交反馈", description = "用户对AI回答提交评分反馈（点赞/踩）")
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
    @Operation(summary = "反馈统计", description = "获取反馈统计数据（点赞数、踩数、好评率）")
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
