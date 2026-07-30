package com.yupi.yuaiagent.document.pdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Heuristic PDF table detector using aligned multi-column lines (PDFBox 3, no Tabula).
 * Works best on text-layer PDFs with space/tab separated columns.
 */
@Slf4j
@Component
public class PdfTableHeuristicExtractor {

    private static final Pattern COL_SPLIT = Pattern.compile("\\s{2,}|\\t+");

    private final TableToMarkdownConverter markdownConverter;
    private final int minTableRows;
    private final int minTableCols;

    public PdfTableHeuristicExtractor(TableToMarkdownConverter markdownConverter,
                                      @Value("${knowledge.pdf.min-table-rows:2}") int minTableRows,
                                      @Value("${knowledge.pdf.min-table-cols:2}") int minTableCols) {
        this.markdownConverter = markdownConverter;
        this.minTableRows = Math.max(2, minTableRows);
        this.minTableCols = Math.max(2, minTableCols);
    }

    public List<ExtractedTable> extract(byte[] pdfBytes) throws IOException {
        List<ExtractedTable> tables = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            int pages = doc.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(doc);
                tables.addAll(extractFromPageText(page, pageText));
            }
        }
        log.info("[PdfTable] extracted {} tables from PDF ({} bytes)", tables.size(), pdfBytes.length);
        return tables;
    }

    /** Package-visible for unit tests. */
    List<ExtractedTable> extractFromPageText(int pageNumber, String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return List.of();
        }
        String[] lines = pageText.split("\\R");
        List<ExtractedTable> tables = new ArrayList<>();
        List<List<String>> current = new ArrayList<>();
        int currentCols = 0;
        int tableIndex = 0;

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                if (flushTable(pageNumber, tableIndex, current, tables)) {
                    tableIndex++;
                }
                current = new ArrayList<>();
                currentCols = 0;
                continue;
            }
            List<String> cols = splitColumns(line);
            if (cols.size() >= minTableCols && (current.isEmpty() || cols.size() == currentCols)) {
                current.add(cols);
                currentCols = cols.size();
            } else {
                if (flushTable(pageNumber, tableIndex, current, tables)) {
                    tableIndex++;
                }
                current = new ArrayList<>();
                currentCols = 0;
                if (cols.size() >= minTableCols) {
                    current.add(cols);
                    currentCols = cols.size();
                }
            }
        }
        flushTable(pageNumber, tableIndex, current, tables);
        return tables;
    }

    /**
     * Returns page text with detected table lines removed (for body chunking).
     */
    public String removeTableLines(String pageText, List<ExtractedTable> pageTables) {
        if (pageText == null || pageTables == null || pageTables.isEmpty()) {
            return pageText != null ? pageText : "";
        }
        Set<String> tableRowKeys = new LinkedHashSet<>();
        for (ExtractedTable table : pageTables) {
            for (List<String> row : table.rows()) {
                tableRowKeys.add(rowKey(row));
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String line : pageText.split("\\R")) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                sb.append('\n');
                continue;
            }
            if (!tableRowKeys.contains(rowKey(splitColumns(stripped)))) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private boolean flushTable(int pageNumber, int tableIndex,
                               List<List<String>> rows, List<ExtractedTable> out) {
        if (rows == null || rows.size() < minTableRows) {
            return false;
        }
        String title = "表 · 第 " + pageNumber + " 页 · #" + (tableIndex + 1);
        String markdown = markdownConverter.toMarkdown(
                new ExtractedTable(pageNumber, tableIndex, List.copyOf(rows), ""), title);
        out.add(new ExtractedTable(pageNumber, tableIndex, List.copyOf(rows), markdown));
        return true;
    }

    static String rowKey(List<String> cols) {
        return String.join("\u0001", cols);
    }

    static List<String> splitColumns(String line) {
        String[] parts = COL_SPLIT.split(line.trim());
        List<String> cols = new ArrayList<>();
        for (String p : parts) {
            if (!p.isBlank()) {
                cols.add(p.trim());
            }
        }
        return cols;
    }
}
