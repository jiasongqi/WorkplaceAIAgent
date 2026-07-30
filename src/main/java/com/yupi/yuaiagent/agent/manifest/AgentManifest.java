package com.yupi.yuaiagent.agent.manifest;

import com.yupi.yuaiagent.agent.AgentIntent;

import java.util.List;

/**
 * Capability manifest for semantic / secondary routing (tutorial Q6 style).
 */
public record AgentManifest(
        AgentIntent intent,
        String displayName,
        String description,
        List<String> keywords,
        List<String> requiredInputs
) {
}
