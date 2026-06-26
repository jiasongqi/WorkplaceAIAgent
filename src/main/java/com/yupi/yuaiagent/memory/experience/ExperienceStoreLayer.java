package com.yupi.yuaiagent.memory.experience;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * L4 Experience Store Layer - 历史经验/案例向量化检索层
 * <p>
 * 将 ExperienceDocument 存入独立的向量数据库中，支持基于语义相似度的检索。
 * 通过 userId 元数据过滤实现用户隔离。
 */
@Slf4j
@Component
public class ExperienceStoreLayer {

    @Resource
    private VectorStore experienceVectorStore;

    @Resource
    private EmbeddingModel dashscopeEmbeddingModel;

    @Value("${memory.layers.experience.top-k:3}")
    private int defaultTopK;

    @Value("${memory.layers.experience.similarity-threshold:0.7}")
    private double defaultSimilarityThreshold;

    @Value("${memory.layers.experience.storage-dir:./tmp/memory/experience}")
    private String storageDir;

    private FileSystemResource persistenceResource;

    @PostConstruct
    public void init() {
        File dir = new File(storageDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        persistenceResource = new FileSystemResource(new File(dir, "experience-vectors.json"));
    }

    /**
     * 将经验向量持久化到磁盘文件（fail-safe）
     */
    private void persistVectors() {
        if (experienceVectorStore instanceof SimpleVectorStore simpleStore) {
            try {
                simpleStore.save(persistenceResource.getFile());
            } catch (Exception e) {
                log.warn("Failed to persist experience vectors to disk: {}", e.getMessage());
            }
        }
    }

    /**
     * 存储经验文档到向量数据库
     * 将 ExperienceDocument 转换为 Spring AI Document 并添加元数据用于后续过滤
     *
     * @param document 经验文档
     */
    public void store(ExperienceDocument document) {
        Objects.requireNonNull(document, "ExperienceDocument must not be null");
        Objects.requireNonNull(document.userId(), "userId must not be null");
        Objects.requireNonNull(document.content(), "content must not be null");

        try {
            // 构建元数据 map，用于后续 userId 过滤和信息还原
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("userId", document.userId());
            metadata.put("agentType", document.agentType() != null ? document.agentType() : "");
            metadata.put("outcome", document.outcome() != null ? document.outcome() : "");
            metadata.put("createdAt", document.createdAt() != null ? document.createdAt().toString() : Instant.now().toString());

            // 将自定义元数据也存入
            if (document.metadata() != null) {
                document.metadata().forEach((key, value) -> {
                    if (value != null) {
                        metadata.put("custom_" + key, value);
                    }
                });
            }

            // 创建 Spring AI Document，id 使用 ExperienceDocument 的 id
            String docId = document.id() != null ? document.id() : UUID.randomUUID().toString();
            Document vectorDoc = new Document(docId, document.content(), metadata);

            experienceVectorStore.add(List.of(vectorDoc));
            persistVectors(); // 写入磁盘，确保重启不丢失
            log.debug("Experience document stored: id={}, userId={}, agentType={}", docId, document.userId(), document.agentType());
        } catch (Exception e) {
            log.error("Failed to store experience document for userId={}: {}", document.userId(), e.getMessage(), e);
        }
    }

    /**
     * 基于语义相似度搜索用户的历史经验文档
     * 使用默认 topK 和阈值
     *
     * @param userId 用户ID
     * @param query  查询文本
     * @return 匹配的经验文档列表（按相似度降序）
     */
    public List<ExperienceDocument> searchSimilar(String userId, String query) {
        return searchSimilar(userId, query, defaultTopK, defaultSimilarityThreshold);
    }

    /**
     * 基于语义相似度搜索用户的历史经验文档
     *
     * @param userId    用户ID，用于过滤确保用户隔离
     * @param query     查询文本（语义搜索）
     * @param topK      返回最多 K 条结果
     * @param threshold 相似度阈值，低于此值的结果不返回
     * @return 匹配的经验文档列表（按相似度降序）
     */
    public List<ExperienceDocument> searchSimilar(String userId, String query, int topK, double threshold) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(query, "query must not be null");

        if (query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            // 构建 userId 过滤表达式，确保用户隔离
            Filter.Expression filterExpression = new FilterExpressionBuilder()
                    .eq("userId", userId)
                    .build();

            // 构建搜索请求
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(threshold)
                    .filterExpression(filterExpression)
                    .build();

            List<Document> results = experienceVectorStore.similaritySearch(searchRequest);

            if (results == null || results.isEmpty()) {
                log.debug("No similar experiences found for userId={}, query='{}' (threshold={})", userId, query, threshold);
                return Collections.emptyList();
            }

            // 将 Spring AI Document 转回 ExperienceDocument
            List<ExperienceDocument> experienceDocuments = results.stream()
                    .map(this::fromVectorDocument)
                    .collect(Collectors.toList());

            log.debug("Found {} similar experiences for userId={}", experienceDocuments.size(), userId);
            return experienceDocuments;
        } catch (Exception e) {
            log.error("Failed to search experiences for userId={}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 将 Spring AI Document 转换回 ExperienceDocument
     */
    private ExperienceDocument fromVectorDocument(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();

        String userId = getMetadataString(metadata, "userId");
        String agentType = getMetadataString(metadata, "agentType");
        String outcome = getMetadataString(metadata, "outcome");
        String createdAtStr = getMetadataString(metadata, "createdAt");

        Instant createdAt = null;
        if (createdAtStr != null && !createdAtStr.isEmpty()) {
            try {
                createdAt = Instant.parse(createdAtStr);
            } catch (Exception e) {
                log.warn("Failed to parse createdAt '{}', using null", createdAtStr);
            }
        }

        // 提取自定义元数据（去掉 "custom_" 前缀）
        Map<String, String> customMetadata = new HashMap<>();
        metadata.forEach((key, value) -> {
            if (key.startsWith("custom_") && value != null) {
                customMetadata.put(key.substring("custom_".length()), value.toString());
            }
        });

        return new ExperienceDocument(
                doc.getId(),
                userId,
                agentType,
                doc.getText(),
                outcome,
                createdAt,
                customMetadata.isEmpty() ? null : customMetadata
        );
    }

    /**
     * 安全获取元数据中的字符串值
     */
    private String getMetadataString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }
}
