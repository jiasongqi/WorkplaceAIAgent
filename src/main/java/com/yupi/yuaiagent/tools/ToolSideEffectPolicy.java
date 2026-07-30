package com.yupi.yuaiagent.tools;

import java.util.Locale;
import java.util.Set;

/**
 * Classifies tools for timeout retry and side-effect idempotency.
 * <p>Read-only tools are safe to auto-retry on timeout; mutating tools must not be blindly retried.</p>
 */
public final class ToolSideEffectPolicy {

    private static final Set<String> READ_ONLY = Set.of(
            "searchWeb",
            "scrapeWebPage",
            "readFile",
            "readFileChunk",
            "searchKnowledgeBase",
            "checkAsyncToolTask",
            "doTerminate"
    );

    private static final Set<String> SIDE_EFFECT = Set.of(
            "writeFile",
            "downloadResource",
            "generatePDF",
            "executeTerminalCommand",
            "startScrapeWebPage",
            "startDownloadResource",
            "startGeneratePDF"
    );

    private ToolSideEffectPolicy() {
    }

    public static boolean isRetryableOnTimeout(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        String name = toolName.trim();
        if (READ_ONLY.contains(name)) {
            return true;
        }
        if (SIDE_EFFECT.contains(name)) {
            return false;
        }
        // Default: treat unknown as non-retryable (safer for side effects)
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("search") || lower.startsWith("read") || lower.startsWith("get")
                || lower.startsWith("check") || lower.startsWith("list");
    }

    public static boolean isSideEffect(String toolName) {
        return toolName != null && SIDE_EFFECT.contains(toolName.trim());
    }
}
