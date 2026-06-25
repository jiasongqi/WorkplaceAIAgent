package com.yupi.yuaiagent.workflow.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 工作流实例持久化仓库 — V1 文件存储。
 *
 * @author jsq
 */
@Slf4j
@Repository
public class WorkflowRepository {

    @Value("${workflow.storage.dir:./tmp/workflows}")
    private String storageDir;

    private final ObjectMapper objectMapper;
    private final Map<String, WorkflowInstance> instances = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private File storageFile;

    public WorkflowRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            File dir = new File(storageDir);
            if (!dir.exists()) dir.mkdirs();
            storageFile = new File(dir, "workflow-instances.json");
            loadFromFile();
            log.info("工作流仓库初始化完成，已加载 {} 个实例", instances.size());
        } catch (Exception e) {
            log.error("初始化工作流仓库失败", e);
        }
    }

    public WorkflowInstance save(WorkflowInstance instance) {
        lock.writeLock().lock();
        try {
            instance.setUpdatedAt(java.time.LocalDateTime.now());
            instances.put(instance.getInstanceId(), instance);
            saveToFile();
            return instance;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<WorkflowInstance> findById(String instanceId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(instances.get(instanceId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<WorkflowInstance> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(instances.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<WorkflowInstance> findByStatus(WorkflowStatus status) {
        lock.readLock().lock();
        try {
            return instances.values().stream()
                    .filter(i -> i.getStatus() == status)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    private void loadFromFile() {
        if (storageFile.exists() && storageFile.length() > 0) {
            try {
                Map<String, WorkflowInstance> loaded = objectMapper.readValue(
                        storageFile, new TypeReference<>() {});
                instances.putAll(loaded);
            } catch (IOException e) {
                log.error("加载工作流实例文件失败", e);
            }
        }
    }

    private void saveToFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, instances);
        } catch (IOException e) {
            log.error("保存工作流实例文件失败", e);
        }
    }
}
