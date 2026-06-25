package com.yupi.yuaiagent.guard;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 工具结果分级处理器 — 核心改进一
 * 
 * 对工具调用返回值进行四级分类，并根据分类结果采取不同策略：
 * - TIMEOUT → 建议直接重试（方向对，网络问题）
 * - EMPTY → 建议换策略（不是重试能解决的）
 * - GARBAGE → 过滤后建议换关键词（内容有毒，不能用）
 * - NORMAL → 不干预
 */
@Slf4j
@Component
public class ToolResultClassifier {

    public enum ResultGrade { TIMEOUT, EMPTY, GARBAGE, NORMAL }

    // Stack trace indicators
    private static final String[] STACK_TRACE_MARKERS = {
        "at ", "Exception", "Caused by:", ".java:", "Traceback"
    };

    // 登录墙/付费墙/垃圾内容关键词
    private static final String[] GARBAGE_KEYWORDS = {
        "请登录", "登录后查看", "登录后继续", "请先登录",
        "subscribe to read", "paywall", "sign in to continue",
        "会员专享", "付费内容", "开通会员", "VIP专属",
        "验证码", "访问受限", "access denied", "403 forbidden"
    };

    /**
     * Classify a tool result and inject differentiated guidance into messageList.
     *
     * @param result      the raw tool result (null allowed)
     * @param isTimeout   whether the result was produced by a TimeoutException
     * @param messageList the agent's message list for guidance injection
     * @return the classification grade
     */
    public ResultGrade classifyAndGuide(String result, boolean isTimeout, List<Message> messageList) {
        ResultGrade grade = classify(result, isTimeout);
        try {
            injectGuidance(grade, result, messageList);
        } catch (Exception e) {
            log.warn("[ToolResultClassifier] guidance injection failed: {}", e.getMessage());
        }
        return grade;
    }

    /**
     * Pure classification logic (no side effects).
     */
    public ResultGrade classify(String result, boolean isTimeout) {
        if (isTimeout) {
            return ResultGrade.TIMEOUT;
        }
        if (result == null || result.isBlank()) {
            return ResultGrade.EMPTY;
        }
        String trimmed = result.strip();
        // 太短的响应大概率是拦截页或无效内容
        if (trimmed.length() < 100) {
            return ResultGrade.GARBAGE;
        }
        if (isGarbageContent(trimmed)) {
            return ResultGrade.GARBAGE;
        }
        if (isStackTrace(trimmed)) {
            return ResultGrade.GARBAGE;
        }
        return ResultGrade.NORMAL;
    }

    /**
     * 垃圾内容检测：登录墙、付费墙、验证码拦截等
     */
    private boolean isGarbageContent(String content) {
        String lower = content.toLowerCase();
        for (String keyword : GARBAGE_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean isStackTrace(String content) {
        int matchCount = 0;
        for (String marker : STACK_TRACE_MARKERS) {
            if (content.contains(marker)) {
                matchCount++;
            }
        }
        return matchCount >= 2;
    }

    /**
     * 分级注入引导消息 — 每种失败类型给出不同策略：
     * - TIMEOUT: 重试（方向对，网络问题）
     * - EMPTY: 换策略（不是重试能解决的）
     * - GARBAGE: 过滤 + 换关键词
     */
    private void injectGuidance(ResultGrade grade, String originalResult, List<Message> messageList) {
        switch (grade) {
            case TIMEOUT -> messageList.add(new UserMessage(
                "[Guard] 上次工具调用超时，这通常是网络问题而非方向错误。" +
                "请直接重试一次相同的工具调用，不要更换关键词或工具。"));
            case EMPTY -> messageList.add(new UserMessage(
                "[Guard] 上次工具调用返回了空结果，说明当前方向可能不对。" +
                "请换一个关键词、换一个搜索源、或尝试其他工具来完成任务。不要重试相同的参数。"));
            case GARBAGE -> {
                String hint = suggestNewDirection(originalResult);
                messageList.add(new UserMessage(
                    "[Guard] 上次工具返回了不可用的内容（登录墙/付费墙/拦截页），已过滤。" +
                    "请不要再访问相同的来源。" + hint));
            }
            case NORMAL -> { /* no action */ }
        }
    }

    /**
     * 从垃圾结果中尝试提取关键信息，给出换方向建议
     */
    private String suggestNewDirection(String garbageResult) {
        if (garbageResult == null || garbageResult.isBlank()) {
            return "建议：换一个关键词或使用其他搜索工具重试。";
        }
        // 如果含有"登录"相关，建议换源
        String lower = garbageResult.toLowerCase();
        if (lower.contains("登录") || lower.contains("sign in") || lower.contains("subscribe")) {
            return "建议：该来源需要登录，请换一个公开可访问的来源或搜索引擎。";
        }
        if (lower.contains("会员") || lower.contains("付费") || lower.contains("vip") || lower.contains("paywall")) {
            return "建议：该内容为付费内容，请换一个免费来源，或者泛化关键词搜索公开资料。";
        }
        return "建议：换一个关键词或使用其他搜索工具重试。";
    }
}
