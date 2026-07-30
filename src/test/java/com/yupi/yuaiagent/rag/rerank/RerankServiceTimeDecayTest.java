package com.yupi.yuaiagent.rag.rerank;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RerankServiceTimeDecayTest {

    @Test
    void newerDocumentRanksHigherWithSameKeywords() {
        RerankService service = new RerankService(true, 0.3);
        Document oldDoc = new Document("涨薪谈判技巧详细说明", Map.of(
                "indexedAt", LocalDateTime.now().minusDays(400).toString()));
        Document newDoc = new Document("涨薪谈判技巧详细说明", Map.of(
                "indexedAt", LocalDateTime.now().minusDays(10).toString()));

        List<Document> reranked = service.rerank("涨薪谈判", List.of(oldDoc, newDoc));
        assertEquals(newDoc.getText(), reranked.get(0).getText());
    }
}
