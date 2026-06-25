package com.yupi.yuaiagent.agent.output;

import java.util.List;

/**
 * Generic text output — fallback when agent does not produce typed output.
 *
 * @param text       raw answer text
 * @param artifactIds associated artifacts
 */
public record TextOutput(String text, List<String> artifactIds) implements AgentOutput {
    @Override
    public String summary() { return text; }

    @Override
    public List<String> artifactIds() { return artifactIds != null ? artifactIds : List.of(); }
}
