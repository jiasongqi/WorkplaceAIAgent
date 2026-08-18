package com.yupi.yuaiagent.access;

import com.yupi.yuaiagent.pack.PackPreferenceMode;
import com.yupi.yuaiagent.permission.PermissionNarrowingService;
import com.yupi.yuaiagent.permission.ToolNameMatcher;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Request-time tool view for Negotiation/Escape. Flag-gated by callers.
 */
public final class RuntimeToolFilter {

    private RuntimeToolFilter() {
    }

    public static ToolCallback[] filter(
            ToolCallback[] tools,
            Set<String> effectivePatterns,
            PackPreferenceMode preferenceMode
    ) {
        if (tools == null || tools.length == 0) {
            return tools;
        }
        List<ToolCallback> kept = new ArrayList<>();
        for (ToolCallback tool : tools) {
            if (tool == null || tool.getToolDefinition() == null) {
                continue;
            }
            String name = tool.getToolDefinition().name();
            if (name == null || name.isBlank()) {
                continue;
            }
            boolean allowed = ToolNameMatcher.isAlwaysAllowed(name)
                    || PermissionNarrowingService.allows(effectivePatterns, name);
            if (preferenceMode == PackPreferenceMode.EXPLICIT_ALL_DISABLED) {
                allowed = ToolNameMatcher.isAlwaysAllowed(name);
            }
            if (allowed) {
                kept.add(tool);
            }
        }
        return kept.toArray(ToolCallback[]::new);
    }

    public static boolean executionAllowed(String toolName, Set<String> effectivePatterns) {
        return PermissionNarrowingService.allows(effectivePatterns, toolName);
    }
}
