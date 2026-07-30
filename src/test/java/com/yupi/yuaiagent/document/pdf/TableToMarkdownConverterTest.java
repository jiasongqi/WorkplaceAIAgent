package com.yupi.yuaiagent.document.pdf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableToMarkdownConverterTest {

    private final TableToMarkdownConverter converter = new TableToMarkdownConverter();

    @Test
    void rendersGitHubMarkdownTable() {
        List<List<String>> rows = List.of(
                List.of("职级", "薪资"),
                List.of("P7", "45-60")
        );
        String md = converter.renderMarkdownTable(rows);
        assertTrue(md.contains("| 职级 |"));
        assertTrue(md.contains("| --- |"));
        assertTrue(md.contains("| P7 |"));
    }
}
