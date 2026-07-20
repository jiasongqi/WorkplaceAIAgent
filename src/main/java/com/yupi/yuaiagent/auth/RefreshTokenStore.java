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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Refresh tokens stored as SHA-256 hashes (raw token never persisted).
 */
@Slf4j
@Repository
public class RefreshTokenStore {

    private final Path storePath;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private List<RefreshTokenRecord> tokens = new ArrayList<>();

    public RefreshTokenStore(@Value("${app.auth.storage-dir:./tmp/auth}") String storageDir) {
        this.storePath = Path.of(storageDir, "refresh-tokens.json");
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(storePath.getParent());
        if (Files.exists(storePath)) {
            lock.writeLock().lock();
            try {
                tokens = objectMapper.readValue(storePath.toFile(), new TypeReference<>() {});
                if (tokens == null) {
                    tokens = new ArrayList<>();
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    public String issue(String userId, Instant expiresAt) {
        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshTokenRecord record = new RefreshTokenRecord();
        record.setId(UUID.randomUUID().toString());
        record.setUserId(userId);
        record.setTokenHash(sha256(raw));
        record.setExpiresAt(expiresAt);
        record.setRevoked(false);
        record.setCreatedAt(Instant.now());

        lock.writeLock().lock();
        try {
            tokens.removeIf(t -> t.getUserId().equals(userId) && (t.isRevoked() || t.getExpiresAt().isBefore(Instant.now())));
            tokens.add(record);
            persist();
        } finally {
            lock.writeLock().unlock();
        }
        return raw;
    }

    public Optional<RefreshTokenRecord> findValid(String rawToken) {
        String hash = sha256(rawToken);
        Instant now = Instant.now();
        lock.readLock().lock();
        try {
            return tokens.stream()
                    .filter(t -> !t.isRevoked())
                    .filter(t -> t.getExpiresAt().isAfter(now))
                    .filter(t -> t.getTokenHash().equals(hash))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void revoke(String rawToken) {
        String hash = sha256(rawToken);
        lock.writeLock().lock();
        try {
            for (RefreshTokenRecord t : tokens) {
                if (t.getTokenHash().equals(hash)) {
                    t.setRevoked(true);
                }
            }
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void revokeAllForUser(String userId) {
        lock.writeLock().lock();
        try {
            for (RefreshTokenRecord t : tokens) {
                if (t.getUserId().equals(userId)) {
                    t.setRevoked(true);
                }
            }
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Rotate: revoke old, issue new. */
    public String rotate(String oldRaw, String userId, Instant expiresAt) {
        revoke(oldRaw);
        return issue(userId, expiresAt);
    }

    private void persist() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), tokens);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist refresh tokens", e);
        }
    }

    static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Data
    public static class RefreshTokenRecord {
        private String id;
        private String userId;
        private String tokenHash;
        private Instant expiresAt;
        private boolean revoked;
        private Instant createdAt;
    }
}
