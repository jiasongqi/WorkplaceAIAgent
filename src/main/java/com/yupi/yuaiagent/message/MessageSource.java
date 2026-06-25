package com.yupi.yuaiagent.message;

/**
 * Message source type — identifies who produced a message.
 *
 * <p>Replaces the implicit role-based assumption ("user" = human, "assistant" = AI).
 * In multi-agent scenarios, multiple agents contribute messages within a single chat,
 * and the frontend needs to distinguish them.
 *
 * @author jsq
 */
public enum MessageSource {

    /** Human user. */
    USER,

    /** AI agent (sub-agent within the orchestrator). */
    AGENT,

    /** System-generated message (e.g., compression summary, status update). */
    SYSTEM,

    /** External tool output (e.g., web search, MCP tool). */
    TOOL,

    /** Result aggregator / synthesizer — the final combined answer. */
    SYNTHESIZER
}
