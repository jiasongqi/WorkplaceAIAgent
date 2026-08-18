package com.yupi.yuaiagent.access;

import com.yupi.yuaiagent.permission.ToolNameMatcher;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Filters the LLM-visible tool list so DENY tools are never advertised.
 * Control tools ({@code doTerminate}, {@code checkAsyncToolTask}) always remain.
 */
public final class PermittedToolFilter {

    private PermittedToolFilter() {
    }

    public static ToolCallback[] filter(AccessDecisionService decisionService,
                                        String agentCode,
                                        ToolCallback[] tools) {
        if (tools == null || tools.length == 0) {
            return tools;
        }
        List<ToolCallback> kept = new ArrayList<>(tools.length);
        for (ToolCallback tool : tools) {
            if (tool == null || tool.getToolDefinition() == null) {
                continue;
            }
            String name = tool.getToolDefinition().name();
            if (name == null || name.isBlank()) {
                continue;
            }
            boolean allowed = ToolNameMatcher.isAlwaysAllowed(name)
                    || (decisionService != null
                    && agentCode != null
                    && !agentCode.isBlank()
                    && decisionService.checkAgentTool(agentCode, name));
            if (allowed) {
                kept.add(tool);
            }
        }
        return kept.toArray(ToolCallback[]::new);
    }
}
