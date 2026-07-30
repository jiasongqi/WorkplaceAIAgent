package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.JwtUtil;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.feedback.Feedback;
import com.yupi.yuaiagent.service.FeedbackAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Feedback Controller — captures user ratings on agent responses.
 * Closed-loop writeback is delegated to {@link FeedbackAppService}.
 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
@Tag(name = "反馈", description = "用户对AI回答的评分反馈")
public class FeedbackController {

    private final FeedbackAppService feedbackAppService;
    private final JwtUtil jwtUtil;

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
        if (userId == null) {
            return Response.failed(401, "未授权，请先登录");
        }

        feedbackAppService.submit(userId, chatId, messageId, rating, comment, agentType, intent);
        return Response.success("feedback recorded");
    }

    @GetMapping("/stats")
    @Operation(summary = "反馈统计", description = "获取反馈统计数据（点赞数、踩数、好评率、按Agent/意图聚合）")
    public Response<FeedbackAppService.FeedbackStatsView> getStats(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = extractUserId(authHeader);
        if (userId == null) {
            return Response.failed(401, "未授权，请先登录");
        }
        return Response.success(feedbackAppService.getStats());
    }

    private String extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return jwtUtil.validateToken(authHeader.substring(7));
    }
}
