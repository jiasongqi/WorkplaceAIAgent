package com.yupi.yuaiagent.trace;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.trace.model.ExecutionTrace;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Trace persistence repository (Req 5.1 / 5.2).
 * <p>
 * File-based storage following the same pattern as {@link com.yupi.yuaiagent.artifact.ArtifactRepository}.
 * Supports {@code save}, {@code findById}, {@code findByChatId}, {@code findByUserId},
 * and automatic per-user retention enforcement.
 *
 * @author jsq
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "app.storage.type", havingValue = "file", matchIfMissing = true)
public class FileTraceStore implements TraceStore {

    @Value("${trace.storage.dir:./tmp/traces}")
    private String storageDir;

    @Resource
    private TraceProperties traceProperties;

    private final ObjectMapper objectMapper;
    private final Map<String, ExecutionTrace> traces = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private File storageFile;

    public FileTraceStore() {
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
            storageFile = new File(dir, "traces.json");
            loadFromFile();
            log.info("[trace:file] initialized, storage path: {}", storageFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("[trace:file] failed to initialize repository", e);
        }
    }

    /**
     * Saves or updates a trace.
     */
    public ExecutionTrace save(ExecutionTrace trace) {
        lock.writeLock().lock();
        try {
            traces.put(trace.getTraceId(), trace);
            saveToFile();
            log.debug("[trace:file] saved traceId={}", trace.getTraceId());
            return trace;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Finds a trace by its ID.
     */
    public Optional<ExecutionTrace> findById(String traceId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(traces.get(traceId));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Finds all traces for a given chatId, sorted by startTime descending (newest first).
     */
    public List<ExecutionTrace> findByChatId(String chatId) {
        if (!StringUtils.hasText(chatId)) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            return traces.values().stream()
                    .filter(t -> chatId.equals(t.getChatId()))
                    .sorted(Comparator.comparing(ExecutionTrace::getStartTime).reversed())
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Paginated version of findByChatId.
     *
     * @param chatId   the chat session ID
     * @param pageNum  1-based page number
     * @param pageSize number of items per page
     * @return sublist for the requested page
     */
    public List<ExecutionTrace> findByChatId(String chatId, int pageNum, int pageSize) {
        return paginate(findByChatId(chatId), pageNum, pageSize);
    }

    /**
     * Finds all traces for a given userId, sorted by startTime descending (newest first).
     */
    public List<ExecutionTrace> findByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            return traces.values().stream()
                    .filter(t -> userId.equals(t.getUserId()))
                    .sorted(Comparator.comparing(ExecutionTrace::getStartTime).reversed())
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Paginated version of findByUserId.
     *
     * @param userId   the user ID
     * @param pageNum  1-based page number
     * @param pageSize number of items per page
     * @return sublist for the requested page
     */
    public List<ExecutionTrace> findByUserId(String userId, int pageNum, int pageSize) {
        return paginate(findByUserId(userId), pageNum, pageSize);
    }

    /**
     * Enforces per-user retention policy: keeps at most {@code trace.maxTracesPerUser}
     * traces per userId, deleting the oldest ones (Req 5.2).
     * <p>
     * Should be called after saving a new trace.
     */
    public void enforceRetentionPolicy(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        lock.writeLock().lock();
        try {
            int maxTraces = traceProperties.getMaxTracesPerUser();
            List<ExecutionTrace> userTraces = traces.values().stream()
                    .filter(t -> userId.equals(t.getUserId()))
                    .sorted(Comparator.comparing(ExecutionTrace::getStartTime))
                    .collect(Collectors.toList());

            if (userTraces.size() <= maxTraces) {
                return;
            }

            int toRemove = userTraces.size() - maxTraces;
            for (int i = 0; i < toRemove; i++) {
                ExecutionTrace oldest = userTraces.get(i);
                traces.remove(oldest.getTraceId());
                log.debug("[trace:file] evicted oldest traceId={} for userId={}", oldest.getTraceId(), userId);
            }

            saveToFile();
            log.info("[trace:file] retention policy enforced for userId={}, removed {} traces", userId, toRemove);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the total number of stored traces.
     */
    public int count() {
        lock.readLock().lock();
        try {
            return traces.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    // --- pagination helper ---

    private static <T> List<T> paginate(List<T> full, int pageNum, int pageSize) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100;
        int from = (pageNum - 1) * pageSize;
        if (from >= full.size()) {
            return List.of();
        }
        int to = Math.min(from + pageSize, full.size());
        return full.subList(from, to);
    }

    // --- file I/O ---

    private void loadFromFile() {
        if (storageFile.exists() && storageFile.length() > 0) {
            try {
                Map<String, ExecutionTrace> loaded = objectMapper.readValue(
                        storageFile,
                        new TypeReference<Map<String, ExecutionTrace>>() {}
                );
                traces.putAll(loaded);
                log.info("[trace:file] loaded {} traces from file", loaded.size());
            } catch (IOException e) {
                log.error("[trace:file] failed to load traces file", e);
            }
        }
    }

    private void saveToFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, traces);
        } catch (IOException e) {
            log.error("[trace:file] failed to save traces file", e);
        }
    }
}
