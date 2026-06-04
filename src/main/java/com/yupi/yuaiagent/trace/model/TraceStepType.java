package com.yupi.yuaiagent.trace.model;

/**
 * Step type enum defining the 10 categories of trace spans.
 * <p>
 * Each value has a unique {@code displayName} (Chinese) for frontend rendering.
 *
 * @author jsq
 */
public enum TraceStepType {

    /**
     * Skill matching phase — check if the user message hits a registered skill.
     */
    SKILL_MATCH("技能匹配"),

    /**
     * Intent detection phase — LLM classifies the user intent.
     */
    INTENT_DETECTION("意图识别"),

    /**
     * Routing phase — orchestrator dispatches to a sub-agent.
     */
    ROUTING("路由分发"),

    /**
     * Profile injection phase — user profile is loaded into the system prompt.
     */
    PROFILE_INJECTION("画像注入"),

    /**
     * Artifact query phase — query the artifact shelf for ready deliverables.
     */
    ARTIFACT_QUERY("交付物查询"),

    /**
     * Artifact consume phase — mark an artifact as consumed.
     */
    ARTIFACT_CONSUME("交付物消费"),

    /**
     * Sub-agent execution phase — a specialized agent processes the request.
     */
    SUB_AGENT_EXECUTION("子Agent执行"),

    /**
     * Tool call phase — an external tool is invoked.
     */
    TOOL_CALL("工具调用"),

    /**
     * Memory compression phase — chat history is compressed by LLM summarization.
     */
    MEMORY_COMPRESSION("记忆压缩"),

    /**
     * Profile update phase — user profile is asynchronously updated after conversation.
     */
    PROFILE_UPDATE("画像更新"),

    /**
     * Quality review phase — QualityGuardAgent reviews the agent's answer.
     */
    QUALITY_REVIEW("质量审查"),

    /**
     * Red team review phase — adversarial review by QualityGuardAgent.
     */
    RED_TEAM_REVIEW("红队审查"),

    /**
     * Red team revision phase — agent revises answer based on red team feedback.
     */
    RED_TEAM_REVISION("蓝队整改"),

    /**
     * Quality blocked — CRITICAL risk, answer was blocked.
     */
    QUALITY_BLOCKED("质量阻断");

    private final String displayName;

    TraceStepType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the Chinese display name for frontend rendering.
     */
    public String getDisplayName() {
        return displayName;
    }
}
