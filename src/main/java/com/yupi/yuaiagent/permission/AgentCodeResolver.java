package com.yupi.yuaiagent.permission;

import java.util.Locale;
import java.util.Map;

/**
 * Maps runtime agent names ({@code YuManus#getName()}, memory keys) onto
 * {@code permissions/*.yaml} {@code agentCode} values.
 */
public final class AgentCodeResolver {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("yumanus", "yu-manus"),
            Map.entry("yu-manus", "yu-manus"),
            Map.entry("manus", "yu-manus"),
            Map.entry("resume", "resume-agent"),
            Map.entry("resumeagent", "resume-agent"),
            Map.entry("resume-agent", "resume-agent"),
            Map.entry("negotiation", "negotiation-agent"),
            Map.entry("negotiationagent", "negotiation-agent"),
            Map.entry("negotiation-agent", "negotiation-agent"),
            Map.entry("escape", "escape-agent"),
            Map.entry("escapeagent", "escape-agent"),
            Map.entry("escape-agent", "escape-agent"),
            Map.entry("consultation", "consultation-agent"),
            Map.entry("consultationagent", "consultation-agent"),
            Map.entry("consultation-agent", "consultation-agent"),
            Map.entry("general", "general-agent"),
            Map.entry("generalcareeragent", "general-agent"),
            Map.entry("general-agent", "general-agent"),
            Map.entry("digital-employee", "digital-employee"),
            Map.entry("admin-agent", "admin-agent"),
            Map.entry("data-agent", "data-agent"),
            Map.entry("data", "data-agent")
    );

    private AgentCodeResolver() {
    }

    /**
     * @return permission profile code, or {@code null} when the runtime name is blank
     */
    public static String resolve(String runtimeName) {
        if (runtimeName == null || runtimeName.isBlank()) {
            return null;
        }
        String trimmed = runtimeName.trim();
        String key = trimmed.toLowerCase(Locale.ROOT).replace("_", "-");
        String mapped = ALIASES.get(key);
        if (mapped != null) {
            return mapped;
        }
        // "ResumeAgent" → resumeagent after stripping non-alnum
        String compact = key.replace("-", "");
        mapped = ALIASES.get(compact);
        return mapped != null ? mapped : trimmed;
    }
}
