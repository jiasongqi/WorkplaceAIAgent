package com.yupi.yuaiagent.permission;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Matches permission YAML patterns against Spring AI {@code @Tool} method names.
 * <p>
 * Historical YAML used dotted namespaces ({@code rag.query}, {@code file.read}) that
 * never matched real tool names ({@code searchKnowledgeBase}, {@code readFile}).
 * This matcher keeps those aliases so existing profiles stay valid, and also
 * accepts exact method names and category wildcards ({@code file.*}).
 */
public final class ToolNameMatcher {

    public static final String TERMINATE_TOOL = "doTerminate";
    public static final String ASYNC_STATUS_TOOL = "checkAsyncToolTask";

    private static final Set<String> ALWAYS_ALLOWED = Set.of(TERMINATE_TOOL, ASYNC_STATUS_TOOL);

    private static final Set<String> FILE_TOOLS = Set.of("readFile", "readFileChunk", "writeFile");
    private static final Set<String> PDF_TOOLS = Set.of("generatePDF", "startGeneratePDF");
    private static final Set<String> WEB_TOOLS = Set.of("searchWeb");
    private static final Set<String> DOWNLOAD_TOOLS = Set.of("downloadResource", "startDownloadResource");
    private static final Set<String> SCRAPE_TOOLS = Set.of("scrapeWebPage", "startScrapeWebPage");
    private static final Set<String> TERMINAL_TOOLS = Set.of("executeTerminalCommand");
    private static final Set<String> RAG_TOOLS = Set.of("searchKnowledgeBase");

    private static final Map<String, Set<String>> CATEGORY_WILDCARDS = Map.of(
            "file.*", FILE_TOOLS,
            "pdf.*", PDF_TOOLS,
            "web.*", WEB_TOOLS,
            "download.*", DOWNLOAD_TOOLS,
            "scrape.*", SCRAPE_TOOLS,
            "terminal.*", TERMINAL_TOOLS,
            "rag.*", RAG_TOOLS
    );

    private static final Map<String, Set<String>> LEGACY_ALIASES = Map.ofEntries(
            Map.entry("rag.query", RAG_TOOLS),
            Map.entry("web.search", WEB_TOOLS),
            Map.entry("file.read", Set.of("readFile", "readFileChunk")),
            Map.entry("file.write", Set.of("writeFile")),
            Map.entry("pdf.generate", PDF_TOOLS),
            Map.entry("terminal.execute", TERMINAL_TOOLS),
            Map.entry("download.resource", DOWNLOAD_TOOLS),
            Map.entry("scrape.page", SCRAPE_TOOLS),
            Map.entry("async.status", Set.of(ASYNC_STATUS_TOOL))
    );

    private ToolNameMatcher() {
    }

    /**
     * Control tools that every agent may call: terminate the loop, and poll async tasks
     * started by a previously allowed tool.
     */
    public static boolean isAlwaysAllowed(String toolName) {
        return toolName != null && ALWAYS_ALLOWED.contains(toolName);
    }

    public static boolean matches(String pattern, String toolName) {
        if (pattern == null || pattern.isBlank() || toolName == null || toolName.isBlank()) {
            return false;
        }
        if ("*".equals(pattern) || pattern.equals(toolName)) {
            return true;
        }
        Set<String> aliasTargets = LEGACY_ALIASES.getOrDefault(pattern, Collections.emptySet());
        if (aliasTargets.contains(toolName)) {
            return true;
        }
        Set<String> category = CATEGORY_WILDCARDS.get(pattern);
        if (category != null && category.contains(toolName)) {
            return true;
        }
        if (pattern.endsWith(".*")) {
            String namespace = pattern.substring(0, pattern.length() - 2);
            return toolName.startsWith(namespace + ".");
        }
        return false;
    }
}
