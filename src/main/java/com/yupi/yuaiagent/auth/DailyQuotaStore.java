package com.yupi.yuaiagent.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Daily chat + token counters per user (file-backed for demo).
 */
@Slf4j
@Repository
public class DailyQuotaStore {

    private final Path storePath;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private List<DailyQuotaRecord> records = new ArrayList<>();

    public DailyQuotaStore(@Value("${app.auth.storage-dir:./tmp/auth}") String storageDir) {
        this.storePath = Path.of(storageDir, "daily-quota.json");
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(storePath.getParent());
        if (Files.exists(storePath)) {
            lock.writeLock().lock();
            try {
                records = objectMapper.readValue(storePath.toFile(), new TypeReference<>() {});
                if (records == null) {
                    records = new ArrayList<>();
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    public DailyQuotaRecord getOrCreate(String userId, LocalDate day) {
        lock.writeLock().lock();
        try {
            Optional<DailyQuotaRecord> existing = records.stream()
                    .filter(r -> r.getUserId().equals(userId) && r.getDay().equals(day))
                    .findFirst();
            if (existing.isPresent()) {
                return existing.get();
            }
            DailyQuotaRecord r = new DailyQuotaRecord();
            r.setUserId(userId);
            r.setDay(day);
            r.setChatCount(0);
            r.setTokenUsed(0);
            records.add(r);
            persist();
            return r;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void save(DailyQuotaRecord record) {
        lock.writeLock().lock();
        try {
            records.removeIf(r -> r.getUserId().equals(record.getUserId()) && r.getDay().equals(record.getDay()));
            records.add(record);
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void persist() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), records);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist daily quota", e);
        }
    }

    @Data
    public static class DailyQuotaRecord {
        private String userId;
        private LocalDate day;
        private int chatCount;
        private int tokenUsed;
    }
}
