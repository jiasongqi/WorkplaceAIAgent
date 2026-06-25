package com.yupi.yuaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 职场顾问向量数据库配置（初始化基于内存的向量数据库 Bean）
 */
@Slf4j
@Configuration
public class AiChatVectorStoreConfig {

    @Resource
    private AiChatDocumentLoader aiChatDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Bean
    VectorStore aiChatVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        // 加载文档
        List<Document> documentList = aiChatDocumentLoader.loadMarkdowns();
        // 关键词增强：启动时可能失败（API 不可用），降级为直接使用原文档
        try {
            List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documentList);
            simpleVectorStore.add(enrichedDocuments);
        } catch (Exception e) {
            log.warn("关键词增强失败，使用原始文档: {}", e.getMessage());
            simpleVectorStore.add(documentList);
        }
        return simpleVectorStore;
    }
}
