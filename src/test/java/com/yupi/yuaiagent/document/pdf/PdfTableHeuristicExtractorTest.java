package com.yupi.yuaiagent.document.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfTableHeuristicExtractorTest {

    private PdfTableHeuristicExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new PdfTableHeuristicExtractor(new TableToMarkdownConverter(), 2, 2);
    }

    @Test
    void detectsMultiColumnTableBlock() {
        String page = """
                职级薪资对照表
                
                职级    年限    薪资范围
                P6      3-5     35-45
                P7      5-8     45-60
                
                以上为参考数据。
                """;
        List<ExtractedTable> tables = extractor.extractFromPageText(1, page);
        assertEquals(1, tables.size());
        ExtractedTable t = tables.get(0);
        assertTrue(t.rowCount() >= 2);
        assertTrue(t.columnCount() >= 2);
        assertTrue(t.markdown().contains("|"));
        assertTrue(t.markdown().contains("P7"));
    }

    @Test
    void removeTableLinesStripsTableRows() {
        String page = """
                前言
                
                职级    年限    薪资
                P6      3-5     35-45
                
                说明文字
                """;
        List<ExtractedTable> tables = extractor.extractFromPageText(1, page);
        String body = extractor.removeTableLines(page, tables);
        assertTrue(body.contains("前言"));
        assertTrue(body.contains("说明文字"));
        assertFalse(body.contains("P6") && body.contains("35-45"));
    }
}
