package com.yupi.yuaiagent.agent.output;

/**
 * Formatter interface for typed AgentOutput.
 * Converts structured output to human-readable text.
 *
 * @param <T> concrete AgentOutput type
 */
public interface AgentOutputFormatter<T extends AgentOutput> {
    String format(T output);
}
