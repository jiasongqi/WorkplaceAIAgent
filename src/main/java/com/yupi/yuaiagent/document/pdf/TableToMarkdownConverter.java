package com.yupi.yuaiagent.document.pdf;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts row/column data to GitHub-flavored Markdown tables for vector indexing.
 */
@Component
public class TableToMarkdownConverter {

    public String toMarkdown(ExtractedTable table, String titleHint) {
        if (table == null || table.rows() == null || table.rows().isEmpty()) {
            return "";
        }
        List<List<String>> rows = normalizeRows(table.rows());
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(titleHint)) {
            sb.append("【").append(titleHint).append("】\n");
        }
        sb.append(renderMarkdownTable(rows));
        return sb.toString().trim();
    }

    public List<List<String>> normalizeRows(List<List<String>> rows) {
        int maxCols = rows.stream().mapToInt(List::size).max().orElse(0);
        List<List<String>> normalized = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> copy = new ArrayList<>(row);
            while (copy.size() < maxCols) {
                copy.add("");
            }
            normalized.add(copy);
        }
        return normalized;
    }

    public String renderMarkdownTable(List<List<String>> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        List<String> header = rows.get(0);
        sb.append("| ");
        header.forEach(c -> sb.append(escapeCell(c)).append(" | "));
        sb.append('\n');
        sb.append("| ");
        header.forEach(c -> sb.append("--- | "));
        sb.append('\n');
        for (int i = 1; i < rows.size(); i++) {
            sb.append("| ");
            rows.get(i).forEach(c -> sb.append(escapeCell(c)).append(" | "));
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static String escapeCell(String cell) {
        if (cell == null) {
            return "";
        }
        return cell.replace("|", "\\|").replace("\n", " ").trim();
    }
}
