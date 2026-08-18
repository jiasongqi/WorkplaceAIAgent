package com.yupi.yuaiagent.permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolNameMatcherTest {

    @Test
    void starMatchesEverything() {
        assertTrue(ToolNameMatcher.matches("*", "searchWeb"));
        assertTrue(ToolNameMatcher.matches("*", "executeTerminalCommand"));
    }

    @Test
    void exactMatchIsCaseSensitive() {
        assertTrue(ToolNameMatcher.matches("searchWeb", "searchWeb"));
        assertFalse(ToolNameMatcher.matches("SearchWeb", "searchWeb"));
        assertFalse(ToolNameMatcher.matches("searchweb", "searchWeb"));
        assertFalse(ToolNameMatcher.matches("searchWeb", "searchKnowledgeBase"));
    }

    @ParameterizedTest
    @CsvSource({
            "rag.query, searchKnowledgeBase",
            "web.search, searchWeb",
            "file.read, readFile",
            "file.read, readFileChunk",
            "file.write, writeFile",
            "pdf.generate, generatePDF",
            "pdf.generate, startGeneratePDF",
            "terminal.execute, executeTerminalCommand",
            "download.resource, downloadResource",
            "download.resource, startDownloadResource",
            "scrape.page, scrapeWebPage",
            "async.status, checkAsyncToolTask"
    })
    void legacyYamlAliasesMatchRealTools(String pattern, String toolName) {
        assertTrue(ToolNameMatcher.matches(pattern, toolName),
                pattern + " should allow " + toolName);
    }

    @ParameterizedTest
    @CsvSource({
            "file.*, readFile",
            "file.*, writeFile",
            "pdf.*, startGeneratePDF",
            "web.*, searchWeb"
    })
    void categoryWildcardMatchesCatalog(String pattern, String toolName) {
        assertTrue(ToolNameMatcher.matches(pattern, toolName));
    }

    @Test
    void namespacedDotStarStillMatchesDottedToolNames() {
        assertTrue(ToolNameMatcher.matches("calendar.*", "calendar.create"));
        assertFalse(ToolNameMatcher.matches("calendar.*", "searchWeb"));
    }

    @Test
    void fileReadDoesNotGrantWriteOrTerminal() {
        assertFalse(ToolNameMatcher.matches("file.read", "writeFile"));
        assertFalse(ToolNameMatcher.matches("file.read", "executeTerminalCommand"));
        assertFalse(ToolNameMatcher.matches("rag.query", "searchWeb"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"doTerminate", "checkAsyncToolTask"})
    void controlToolsAreAlwaysAllowed(String toolName) {
        assertTrue(ToolNameMatcher.isAlwaysAllowed(toolName));
    }

    @Test
    void sideEffectToolsAreNotAlwaysAllowed() {
        assertFalse(ToolNameMatcher.isAlwaysAllowed("writeFile"));
        assertFalse(ToolNameMatcher.isAlwaysAllowed("executeTerminalCommand"));
    }

    @Test
    void nullAndBlankAreDenied() {
        assertFalse(ToolNameMatcher.matches("*", null));
        assertFalse(ToolNameMatcher.matches(null, "searchWeb"));
        assertFalse(ToolNameMatcher.matches("  ", "searchWeb"));
        assertFalse(ToolNameMatcher.isAlwaysAllowed(null));
    }
}
