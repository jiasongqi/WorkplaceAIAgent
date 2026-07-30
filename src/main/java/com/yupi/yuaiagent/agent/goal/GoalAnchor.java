package com.yupi.yuaiagent.agent.goal;

import org.springframework.util.StringUtils;

/**
 * Builds a per-turn goal block that must be re-injected every Agent step / specialist call.
 * Prevents long-context "goal forgetting" (mm_agent_tutorial Ch1 Gotcha).
 */
public final class GoalAnchor {

    public static final String MARKER = "【本轮任务目标 Goal Anchor】";

    private GoalAnchor() {
    }

    /**
     * @param activeGoal session-level goal (may be null)
     * @param turnMessage current user message
     * @param taskType    routing intent, e.g. RESUME
     */
    public static String buildBlock(String activeGoal, String turnMessage, String taskType) {
        String goal = resolveGoal(activeGoal, turnMessage);
        if (!StringUtils.hasText(goal)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(MARKER).append('\n');
        sb.append("你必须始终围绕以下目标行动；若与历史摘要冲突，以本目标为准。\n");
        sb.append("- 目标：").append(goal.trim()).append('\n');
        if (StringUtils.hasText(taskType)) {
            sb.append("- 路由意图：").append(taskType.trim()).append('\n');
        }
        sb.append("- 要求：每一步思考前先对照本目标；完成后用工具或明确结论交付，不要偏离。\n");
        return sb.toString();
    }

    /** Prefer session activeGoal; else truncate current user message. */
    public static String resolveGoal(String activeGoal, String turnMessage) {
        if (StringUtils.hasText(activeGoal)) {
            return truncate(activeGoal.trim(), 240);
        }
        if (StringUtils.hasText(turnMessage)) {
            return truncate(turnMessage.trim(), 240);
        }
        return "";
    }

    /** Reminder line for ReAct inner loops (appended each think). */
    public static String stepReminder(String goal) {
        if (!StringUtils.hasText(goal)) {
            return "";
        }
        return MARKER + " 提醒：当前目标仍是「" + truncate(goal.trim(), 120) + "」。请对照目标选择下一步。";
    }

    public static boolean isGoalReminder(String text) {
        return text != null && text.contains(MARKER);
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}
