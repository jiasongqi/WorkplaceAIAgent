package com.yupi.yuaiagent.memory.experience;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * L4 Experience Store 专用向量数据库配置
 * 使用独立的 SimpleVectorStore 实例，与 RAG 文档向量库隔离。
 * 通过文件持久化确保经验数据在应用重启后不丢失。
 */
@Slf4j
@Configuration
public class ExperienceVectorStoreConfig {

    @Value("${memory.layers.experience.storage-dir:./tmp/memory/experience}")
    private String storageDir;

    @Bean
    VectorStore experienceVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        File dir = new File(storageDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File storeFile = new File(dir, "experience-vectors.json");
        FileSystemResource resource = new FileSystemResource(storeFile);

        SimpleVectorStore store = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();

        // 从文件加载已有向量数据（如果存在）
        if (storeFile.exists() && storeFile.length() > 0) {
            try {
                store.load(resource);
                log.info("[ExperienceVectorStore] loaded persisted vectors from {}", storeFile.getAbsolutePath());
            } catch (Exception e) {
                log.warn("[ExperienceVectorStore] failed to load persisted vectors, starting fresh: {}", e.getMessage());
            }
        }

        return store;
    }
}
