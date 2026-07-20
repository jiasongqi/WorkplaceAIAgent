package com.yupi.yuaiagent.auth;

import cn.hutool.crypto.digest.BCrypt;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-backed user account store (demo-friendly; switchable to JDBC later).
 */
@Slf4j
@Repository
public class UserAccountStore {

    private final Path storePath;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private List<UserAccount> accounts = new ArrayList<>();

    public UserAccountStore(@Value("${app.auth.storage-dir:./tmp/auth}") String storageDir) {
        this.storePath = Path.of(storageDir, "users.json");
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(storePath.getParent());
        if (Files.exists(storePath)) {
            lock.writeLock().lock();
            try {
                accounts = objectMapper.readValue(storePath.toFile(), new TypeReference<>() {});
                if (accounts == null) {
                    accounts = new ArrayList<>();
                }
            } finally {
                lock.writeLock().unlock();
            }
            log.info("Loaded {} user accounts from {}", accounts.size(), storePath);
        }
    }

    public Optional<UserAccount> findByUsername(String username) {
        lock.readLock().lock();
        try {
            return accounts.stream()
                    .filter(a -> a.getUsername() != null && a.getUsername().equalsIgnoreCase(username))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<UserAccount> findByUserId(String userId) {
        lock.readLock().lock();
        try {
            return accounts.stream()
                    .filter(a -> a.getUserId().equals(userId))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean existsUsername(String username) {
        return findByUsername(username).isPresent();
    }

    public UserAccount save(UserAccount account) {
        lock.writeLock().lock();
        try {
            accounts.removeIf(a -> a.getUserId().equals(account.getUserId())
                    || (a.getUsername() != null && a.getUsername().equalsIgnoreCase(account.getUsername())));
            accounts.add(account);
            persist();
            return account;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static String hashPassword(String raw) {
        return BCrypt.hashpw(raw);
    }

    public static boolean matches(String raw, String hash) {
        if (raw == null || hash == null) {
            return false;
        }
        return BCrypt.checkpw(raw, hash);
    }

    private void persist() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), accounts);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist user accounts: " + e.getMessage(), e);
        }
    }

    public UserAccount create(String userId, String username, String rawPassword, UserRole role) {
        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setUsername(username);
        account.setPasswordHash(hashPassword(rawPassword));
        account.setRole(role);
        account.setStatus("ACTIVE");
        Instant now = Instant.now();
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        return save(account);
    }
}
