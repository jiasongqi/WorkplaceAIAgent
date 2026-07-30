package com.yupi.yuaiagent.perception;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

/**
 * Session-scoped upload storage with TTL (Ch5 privacy lifecycle).
 */
@Slf4j
@Service
public class SessionUploadStore {

    private final Path storageDir;
    private final long ttlDays;

    public SessionUploadStore(
            @Value("${perception.upload.storage-dir:./tmp/session-uploads}") String storageDir,
            @Value("${perception.upload.ttl-days:7}") long ttlDays) {
        this.storageDir = Path.of(storageDir);
        this.ttlDays = Math.max(1, ttlDays);
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(storageDir);
    }

    /**
     * Persist upload bytes; returns relative ref for SharedState facts.
     */
    public String save(String chatId, String userId, byte[] bytes, String filename) throws IOException {
        if (!StringUtils.hasText(chatId) || bytes == null || bytes.length == 0) {
            return null;
        }
        String safeName = sanitizeFilename(filename);
        Path chatDir = storageDir.resolve(chatId);
        Files.createDirectories(chatDir);
        Path target = chatDir.resolve(System.currentTimeMillis() + "_" + safeName);
        Files.write(target, bytes);
        log.info("[SessionUpload] saved chatId={} userId={} file={} bytes={}",
                chatId, userId, target.getFileName(), bytes.length);
        return target.toString();
    }

    @Scheduled(cron = "${perception.upload.cleanup-cron:0 0 3 * * *}")
    public void cleanupExpired() {
        Instant cutoff = Instant.now().minus(ttlDays, ChronoUnit.DAYS);
        try (Stream<Path> dirs = Files.list(storageDir)) {
            dirs.filter(Files::isDirectory).forEach(chatDir -> cleanupDirectory(chatDir, cutoff));
        } catch (IOException e) {
            log.warn("[SessionUpload] cleanup scan failed: {}", e.getMessage());
        }
    }

    private void cleanupDirectory(Path chatDir, Instant cutoff) {
        try (Stream<Path> files = Files.list(chatDir)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Instant modified = Files.getLastModifiedTime(file).toInstant();
                    if (modified.isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                        log.info("[SessionUpload] deleted expired {}", file.getFileName());
                    }
                } catch (IOException e) {
                    log.debug("[SessionUpload] skip {}: {}", file, e.getMessage());
                }
            });
            try (Stream<Path> remaining = Files.list(chatDir)) {
                if (remaining.findAny().isEmpty()) {
                    Files.deleteIfExists(chatDir);
                }
            }
        } catch (IOException e) {
            log.debug("[SessionUpload] cleanup chatDir {} failed: {}", chatDir, e.getMessage());
        }
    }

    private static String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "upload.bin";
        }
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
