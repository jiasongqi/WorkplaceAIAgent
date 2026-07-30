package com.yupi.yuaiagent.document.pdf;

import java.util.List;

/**
 * A table extracted from a PDF page (rows of cell strings).
 */
public record ExtractedTable(
        int pageNumber,
        int tableIndex,
        List<List<String>> rows,
        String markdown
) {
    public int rowCount() {
        return rows != null ? rows.size() : 0;
    }

    public int columnCount() {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        return rows.stream().mapToInt(List::size).max().orElse(0);
    }
}
