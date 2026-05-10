package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * 创建自定义的 RAG 检索增强顾问的工厂模式
 * 包括相似度阈值，返回文档数量和过滤规则
 * 
 * @author jsq
 */
@Slf4j
public class AiChatRagCustomAdvisorFactory {

    // 默认配置
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;
    private static final int DEFAULT_TOP_K = 3;

    /**
     * 创建自定义的 RAG 检索增强顾问（使用默认配置）
     *
     * @param vectorStore 向量存储
     * @param status      状态过滤值
     * @return 自定义的 RAG 检索增强顾问
     */
    public static Advisor createAiChatRagCustomAdvisor(VectorStore vectorStore, String status) {
        return createAiChatRagCustomAdvisor(vectorStore, status, DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_TOP_K);
    }

    /**
     * 创建自定义的 RAG 检索增强顾问（自定义相似度阈值和 topK）
     *
     * @param vectorStore        向量存储
     * @param status             状态过滤值
     * @param similarityThreshold 相似度阈值（0.0-1.0）
     * @param topK               返回文档数量
     * @return 自定义的 RAG 检索增强顾问
     */
    public static Advisor createAiChatRagCustomAdvisor(VectorStore vectorStore, String status,
                                                       double similarityThreshold, int topK) {
        log.info("创建 RAG Advisor：状态过滤={}, 相似度阈值={}, topK={}", status, similarityThreshold, topK);
        
        // 过滤特定状态的文档
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status)
                .build();
        
        // 创建文档检索器
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                // 过滤条件，限定过滤的规则
                .filterExpression(expression)
                // 相似度阈值
                .similarityThreshold(similarityThreshold)
                // 返回文档数量
                .topK(topK)
                .build();
        
        return RetrievalAugmentationAdvisor.builder()
                // documentRetriever 文档检索器
                .documentRetriever(documentRetriever)
                .queryAugmenter(AiChatContextualQueryAugmenterFactory.createInstance())
                .build();
    }

    /**
     * 创建不带状态过滤的 RAG 检索增强顾问
     * 适用于不需要按状态过滤的场景
     *
     * @param vectorStore        向量存储
     * @param similarityThreshold 相似度阈值（0.0-1.0）
     * @param topK               返回文档数量
     * @return 自定义的 RAG 检索增强顾问
     */
    public static Advisor createAiChatRagCustomAdvisor(VectorStore vectorStore,
                                                       double similarityThreshold, int topK) {
        log.info("创建 RAG Advisor（无过滤）：相似度阈值={}, topK={}", similarityThreshold, topK);
        
        // 创建文档检索器（无过滤条件）
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(similarityThreshold)
                .topK(topK)
                .build();
        
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(AiChatContextualQueryAugmenterFactory.createInstance())
                .build();
    }
}
