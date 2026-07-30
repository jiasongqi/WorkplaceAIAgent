package com.yupi.yuaiagent.document.pdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PDF → text chunks + structured table chunks for knowledge base vector indexing.
 */
@Slf4j
@Service
public class PdfKnowledgeIngestionService {

    private final PdfTableHeuristicExtractor tableExtractor;
    private final TableJsonSidecarStore sidecarStore;
    private final boolean sidecarEnabled;
    private final int textChunkSize;

    public PdfKnowledgeIngestionService(PdfTableHeuristicExtractor tableExtractor,
                                        TableJsonSidecarStore sidecarStore,
                                        @Value("${knowledge.pdf.sidecar-enabled:true}") boolean sidecarEnabled,
                                        @Value("${knowledge.pdf.text-chunk-size:200}") int textChunkSize) {
        this.tableExtractor = tableExtractor;
        this.sidecarStore = sidecarStore;
        this.sidecarEnabled = sidecarEnabled;
        this.textChunkSize = Math.max(100, textChunkSize);
    }

    public List<Document> ingest(byte[] pdfBytes, String filename, String status, String docId)
            throws IOException {
        List<ExtractedTable> tables = tableExtractor.extract(pdfBytes);
        Map<Integer, List<ExtractedTable>> byPage = tables.stream()
                .collect(Collectors.groupingBy(ExtractedTable::pageNumber, LinkedHashMap::new, Collectors.toList()));

        List<Document> documents = new ArrayList<>();
        Map<String, Object> baseMeta = baseMetadata(filename, status, docId);

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            int pages = doc.getNumberOfPages();
            StringBuilder fullBody = new StringBuilder();

            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(doc);
                List<ExtractedTable> pageTables = byPage.getOrDefault(page, List.of());
                String body = tableExtractor.removeTableLines(pageText, pageTables);
                if (StringUtils.hasText(body)) {
                    fullBody.append("【第 ").append(page).append(" 页】\n").append(body).append("\n\n");
                }
            }

            if (fullBody.length() > 0) {
                Document bodyDoc = new Document(fullBody.toString().trim(), withChunkType(baseMeta, "text"));
                documents.addAll(splitTextChunks(bodyDoc));
            }

            for (ExtractedTable table : tables) {
                Map<String, Object> meta = withChunkType(baseMeta, "table");
                meta.put("pageNumber", String.valueOf(table.pageNumber()));
                meta.put("tableIndex", String.valueOf(table.tableIndex()));
                if (sidecarEnabled) {
                    try {
                        List<String> header = table.rows().isEmpty() ? List.of() : table.rows().get(0);
                        String ref = sidecarStore.save(docId, table, header);
                        if (ref != null) {
                            meta.put("structuredRef", ref);
                        }
                    } catch (IOException e) {
                        log.warn("[PdfIngest] sidecar save failed docId={} page={}: {}",
                                docId, table.pageNumber(), e.getMessage());
                    }
                }
                documents.add(new Document(table.markdown(), meta));
            }
        }

        if (documents.isEmpty()) {
            documents.add(new Document("（PDF 未提取到可索引文本）", withChunkType(baseMeta, "text")));
        }

        log.info("[PdfIngest] docId={} filename={} chunks={} tables={}",
                docId, filename, documents.size(), tables.size());
        return documents;
    }

    private List<Document> splitTextChunks(Document doc) {
        TokenTextSplitter splitter = new TokenTextSplitter(textChunkSize, textChunkSize / 2, 10, 5000, true);
        return splitter.apply(List.of(doc));
    }

    private static Map<String, Object> baseMetadata(String filename, String status, String docId) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("filename", filename);
        meta.put("status", status);
        meta.put("docId", docId);
        meta.put("sourceType", "pdf");
        meta.put("indexedAt", LocalDateTime.now().toString());
        return meta;
    }

    private static Map<String, Object> withChunkType(Map<String, Object> base, String chunkType) {
        Map<String, Object> meta = new HashMap<>(base);
        meta.put("chunkType", chunkType);
        return meta;
    }
}
