package com.yupi.yuaiagent.agent.paradigm;

/**
 * Agent Paradigm — defines the reasoning strategy for task execution.
 *
 * <p>Each paradigm represents a different approach to how an agent thinks and acts:</p>
 * <ul>
 *     <li>{@link #REACT} — Reasoning and Acting: think-then-act loop, dynamic adjustment</li>
 *     <li>{@link #PLAN_AND_SOLVE} — Plan first, then execute step by step</li>
 *     <li>{@link #REFLECTION} — Generate, evaluate, reflect, and revise</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
public enum AgentParadigm {

    /**
     * ReAct (Reasoning and Acting) paradigm.
     *
     * <p>Best for:</p>
     * <ul>
     *     <li>Real-time interactive tasks</li>
     *     <li>Tasks requiring dynamic adjustment</li>
     *     <li>Simple to medium complexity tasks</li>
     *     <li>Tool-heavy workflows</li>
     * </ul>
     */
    REACT("react", "ReAct: 思考-行动循环，动态调整"),

    /**
     * Plan-and-Solve paradigm.
     *
     * <p>Best for:</p>
     * <ul>
     *     <li>Complex multi-step tasks</li>
     *     <li>Tasks requiring structured approach</li>
     *     <li>Research and analysis tasks</li>
     *     <li>Tasks with clear sub-goals</li>
     * </ul>
     */
    PLAN_AND_SOLVE("plan_and_solve", "Plan-and-Solve: 先规划后执行"),

    /**
     * Reflection paradigm.
     *
     * <p>Best for:</p>
     * <ul>
     *     <li>Tasks requiring high quality output</li>
     *     <li>Creative writing and content generation</li>
     *     <li>Code review and optimization</li>
     *     <li>Tasks where accuracy is critical</li>
     * </ul>
     */
    REFLECTION("reflection", "Reflection: 自我批判和修正");

    private final String code;
    private final String description;

    AgentParadigm(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Get paradigm by code string.
     *
     * @param code paradigm code (e.g., "react", "plan_and_solve")
     * @return matching paradigm, or REACT as default
     */
    public static AgentParadigm fromCode(String code) {
        if (code == null || code.isBlank()) {
            return REACT;
        }
        for (AgentParadigm paradigm : values()) {
            if (paradigm.code.equalsIgnoreCase(code)) {
                return paradigm;
            }
        }
        return REACT;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
