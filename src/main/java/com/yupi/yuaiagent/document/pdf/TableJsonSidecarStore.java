package com.yupi.yuaiagent.document.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional JSON sidecar for structured table lookup (Ch5 table-as-truth-source).
 */
@Slf4j
@Component
public class TableJsonSidecarStore {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path storageDir;

    public TableJsonSidecarStore(
            @Value("${knowledge.pdf.table-sidecar-dir:./tmp/knowledge/tables}") String storageDir) {
        this.storageDir = Path.of(storageDir);
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(storageDir);
    }

    public String save(String docId, ExtractedTable table, List<String> headerGuess) throws IOException {
        if (docId == null || table == null || table.rows().isEmpty()) {
            return null;
        }
        List<Map<String, String>> rows = toJsonRows(table.rows(), headerGuess);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("docId", docId);
        payload.put("pageNumber", table.pageNumber());
        payload.put("tableIndex", table.tableIndex());
        payload.put("rows", rows);

        String filename = docId + "_p" + table.pageNumber() + "_t" + table.tableIndex() + ".json";
        Path target = storageDir.resolve(filename);
        objectMapper.writeValue(target.toFile(), payload);
        log.debug("[TableSidecar] wrote {}", target);
        return target.toString();
    }

    private static List<Map<String, String>> toJsonRows(List<List<String>> rows, List<String> headers) {
        List<Map<String, String>> jsonRows = new ArrayList<>();
        if (rows.isEmpty()) {
            return jsonRows;
        }
        List<String> hdr = headers != null && !headers.isEmpty()
                ? headers
                : defaultHeaders(rows.get(0).size());
        int start = headers != null && !headers.isEmpty() ? 0 : 1;
        for (int i = start; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            Map<String, String> obj = new LinkedHashMap<>();
            for (int c = 0; c < hdr.size(); c++) {
                String key = hdr.get(c);
                String val = c < row.size() ? row.get(c) : "";
                obj.put(sanitizeKey(key, c), val);
            }
            jsonRows.add(obj);
        }
        return jsonRows;
    }

    private static List<String> defaultHeaders(int cols) {
        List<String> hdr = new ArrayList<>();
        for (int i = 0; i < cols; i++) {
            hdr.add("col" + (i + 1));
        }
        return hdr;
    }

    private static String sanitizeKey(String key, int index) {
        if (key == null || key.isBlank()) {
            return "col" + (index + 1);
        }
        return key.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_");
    }
}
