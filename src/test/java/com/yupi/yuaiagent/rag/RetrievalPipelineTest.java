package com.yupi.yuaiagent.rag;

import com.yupi.yuaiagent.demo.rag.MultiQueryExpanderDemo;
import com.yupi.yuaiagent.rag.rerank.RerankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrievalPipelineTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private QueryRewriter queryRewriter;
    @Mock
    private MultiQueryExpanderDemo multiQueryExpanderDemo;
    @Mock
    private HyDERetriever hydeRetriever;

    private RerankService rerankService;
    private RetrievalPipeline pipeline;

    @BeforeEach
    void setUp() {
        rerankService = new RerankService(true, 0.15);
        pipeline = new RetrievalPipeline(
                vectorStore, queryRewriter, rerankService,
                multiQueryExpanderDemo, hydeRetriever,
                true, 5);
    }

    @Test
    void retrieve_directPath_appliesRewriteAndRerank() {
        when(queryRewriter.doQueryRewrite("涨薪")).thenReturn("薪资谈判技巧");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("无关内容", Map.of("filename", "a.md")),
                new Document("涨薪谈判话术与时机", Map.of("filename", "b.md"))
        ));

        RetrievalPipeline.RetrievalResult result =
                pipeline.retrieve("涨薪", RetrievalOptions.toolDefaults());

        assertTrue(result.hasHits());
        assertEquals("薪资谈判技巧", result.rewrittenQuery());
        assertTrue(result.formattedResults().contains("b.md"));
        verify(multiQueryExpanderDemo, never()).expand(anyString());
    }

    @Test
    void retrieve_multiQueryPath_expandsAndMerges() {
        when(queryRewriter.doQueryRewrite("面试")).thenReturn("面试技巧");
        when(multiQueryExpanderDemo.expand("面试技巧")).thenReturn(List.of(
                new Query("面试技巧"),
                new Query("STAR 法则")
        ));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("STAR 回答模板", Map.of("filename", "interview.md"))
        ));

        RetrievalPipeline.RetrievalResult result =
                pipeline.retrieve("面试", RetrievalOptions.chatDefaults());

        assertTrue(result.hasHits());
        verify(multiQueryExpanderDemo).expand("面试技巧");
        assertTrue(result.buildPrompt("面试").contains("STAR 回答模板"));
    }

    @Test
    void retrieve_hydePath_skipsVectorWhenEnabled() {
        when(queryRewriter.doQueryRewrite("offer")).thenReturn("offer 评估");
        when(hydeRetriever.retrieve("offer 评估")).thenReturn(List.of(
                new Document("Offer 对比维度", Map.of("filename", "offer.md"))
        ));

        RetrievalOptions hydeOpts = new RetrievalOptions(null, 3, false, true, 0.5);
        RetrievalPipeline.RetrievalResult result = pipeline.retrieve("offer", hydeOpts);

        assertTrue(result.hasHits());
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }
}
