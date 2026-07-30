package com.yupi.yuaiagent.agent.loop;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * Out-of-Budget Wrap-up (Ch4 §2.3): never crash — force a partial final answer.
 */
@Slf4j
public final class LoopWrapUp {

    private static final String WRAP_SYSTEM = """
            你是 Agent Loop 的收尾模块。步数/预算已用尽，任务可能未完整完成。
            请基于已有步骤结果，用中文给出：
            1) 当前能确定的结论摘要（2-6 句）
            2) 未完成项清单（条目化）
            3) 是否建议人工介入（是/否 + 一句理由）
            不要假装工具已成功执行；不要编造未出现在步骤结果中的事实。
            """;

    private LoopWrapUp() {
    }

    public static AgentLoopResult wrapUp(String goal, List<String> stepResults,
                                         int maxSteps, ChatClient chatClient) {
        return wrapUp(goal, stepResults, maxSteps, chatClient, null);
    }

    public static AgentLoopResult wrapUp(String goal, List<String> stepResults,
                                         int maxSteps, ChatClient chatClient, String budgetReason) {
        String trace = truncateJoin(stepResults, 2500);
        List<String> incomplete = StrUtil.isNotBlank(budgetReason)
                ? List.of(budgetReason.strip(), "后续步骤未执行或未验证")
                : List.of(
                "达到最大步骤上限 " + maxSteps + "，循环已强制停止",
                "后续步骤未执行或未验证");

        String summary;
        boolean needsHuman = true;
        if (chatClient != null) {
            try {
                String user = "任务目标：\n" + StrUtil.blankToDefault(goal, "(未设置)")
                        + "\n\n已执行步骤摘要：\n" + trace
                        + "\n\n请输出收尾摘要。";
                String llm = chatClient.prompt(new Prompt(List.of(
                                new SystemMessage(WRAP_SYSTEM),
                                new UserMessage(user))))
                        .call()
                        .content();
                if (StrUtil.isNotBlank(llm)) {
                    summary = llm.strip();
                    needsHuman = summary.contains("人工") || summary.contains("是");
                } else {
                    summary = templateSummary(goal, stepResults);
                }
            } catch (Exception e) {
                log.warn("[LoopWrapUp] LLM wrap-up failed, using template: {}", e.getMessage());
                summary = templateSummary(goal, stepResults);
            }
        } else {
            summary = templateSummary(goal, stepResults);
        }

        return AgentLoopResult.partial(summary, incomplete, trace, needsHuman);
    }

    private static String templateSummary(String goal, List<String> stepResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务「").append(StrUtil.blankToDefault(goal, "当前请求"))
                .append("」在步数预算用尽前未能完整结束。\n");
        if (stepResults == null || stepResults.isEmpty()) {
            sb.append("尚未产生可用步骤结果。");
        } else {
            sb.append("已完成 ").append(stepResults.size()).append(" 步；最近一步摘要：")
                    .append(truncate(stepResults.get(stepResults.size() - 1), 300));
        }
        return sb.toString();
    }

    private static String truncateJoin(List<String> steps, int maxChars) {
        if (steps == null || steps.isEmpty()) {
            return "(无步骤结果)";
        }
        List<String> parts = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < steps.size(); i++) {
            String piece = "Step " + (i + 1) + ": " + truncate(steps.get(i), 400);
            if (used + piece.length() > maxChars) {
                break;
            }
            parts.add(piece);
            used += piece.length();
        }
        return String.join("\n", parts);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
