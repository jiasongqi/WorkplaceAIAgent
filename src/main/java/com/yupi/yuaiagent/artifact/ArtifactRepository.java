package com.yupi.yuaiagent.artifact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 交付物存储
 * 基于文件的持久化存储（可替换为数据库实现），存储风格与 {@code AppointmentRepository} 一致。
 *
 * @author jsq
 */
@Slf4j
@Repository
public class ArtifactRepository {

    @Value("${artifact.storage.dir:./tmp/artifacts}")
    private String storageDir;

    private final ObjectMapper objectMapper;
    private final Map<String, Artifact> artifacts = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private File storageFile;

    public ArtifactRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            File dir = new File(storageDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            storageFile = new File(dir, "artifacts.json");
            loadFromFile();
            log.info("交付物存储初始化完成，存储路径：{}", storageFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("初始化交付物存储失败", e);
        }
    }

    /**
     * 保存或更新交付物
     * <ul>
     *     <li>未指定 artifactId 时生成全局唯一 UUID</li>
     *     <li>新建时设置 createdAt 与 updatedAt</li>
     *     <li>更新已存在记录时保留原 createdAt 并刷新 updatedAt</li>
     * </ul>
     */
    public Artifact save(Artifact artifact) {
        lock.writeLock().lock();
        try {
            if (artifact.getArtifactId() == null || artifact.getArtifactId().isEmpty()) {
                artifact.setArtifactId(UUID.randomUUID().toString());
            }
            LocalDateTime now = LocalDateTime.now();
            Artifact existing = artifacts.get(artifact.getArtifactId());
            if (existing != null) {
                // 更新已存在记录：保留原 createdAt
                artifact.setCreatedAt(existing.getCreatedAt());
            } else if (artifact.getCreatedAt() == null) {
                // 新建记录：设置 createdAt
                artifact.setCreatedAt(now);
            }
            artifact.setUpdatedAt(now);

            artifacts.put(artifact.getArtifactId(), artifact);
            saveToFile();

            log.info("保存交付物：{}", artifact.getArtifactId());
            return artifact;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 根据 ID 查找交付物
     */
    public Optional<Artifact> findById(String artifactId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(artifacts.get(artifactId));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 查找所有交付物
     */
    public List<Artifact> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(artifacts.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 更新交付物状态：更新状态并刷新 updatedAt，写盘
     */
    public Optional<Artifact> updateStatus(String artifactId, ArtifactStatus status) {
        lock.writeLock().lock();
        try {
            Artifact artifact = artifacts.get(artifactId);
            if (artifact != null) {
                artifact.setStatus(status);
                artifact.setUpdatedAt(LocalDateTime.now());
                saveToFile();
                return Optional.of(artifact);
            }
            return Optional.empty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 从文件加载
     */
    private void loadFromFile() {
        if (storageFile.exists() && storageFile.length() > 0) {
            try {
                Map<String, Artifact> loaded = objectMapper.readValue(
                        storageFile,
                        new TypeReference<Map<String, Artifact>>() {}
                );
                artifacts.putAll(loaded);
                log.info("从文件加载交付物：{} 条", loaded.size());
            } catch (IOException e) {
                log.error("加载交付物文件失败", e);
            }
        }
    }

    /**
     * 保存到文件
     */
    private void saveToFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, artifacts);
        } catch (IOException e) {
            log.error("保存交付物文件失败", e);
        }
    }
}
