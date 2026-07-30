package com.yupi.yuaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency ledger for side-effect tools.
 * Prevents duplicate writes/downloads/commands when the agent layer retries after timeout.
 */
@Slf4j
@Component
public class ToolIdempotencyStore {

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Value("${app.tools.idempotency-ttl-seconds:600}")
    private long ttlSeconds = 600;

    public record Entry(String result, Instant expiresAt) {
    }

    public String key(String toolName, String payloadFingerprint) {
        return toolName + "::" + sha256(payloadFingerprint == null ? "" : payloadFingerprint);
    }

    public Optional<String> find(String key) {
        purgeExpired();
        Entry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    public void remember(String key, String result) {
        if (key == null || key.isBlank() || result == null) {
            return;
        }
        Instant expires = Instant.now().plusSeconds(Math.max(30, ttlSeconds));
        store.put(key, new Entry(result, expires));
        log.debug("[Idempotency] remembered key={}", key);
    }

    public Optional<String> findOrRemember(String key, java.util.function.Supplier<String> executor) {
        Optional<String> cached = find(key);
        if (cached.isPresent()) {
            log.info("[Idempotency] hit key={} — returning cached side-effect result", key);
            return Optional.of(cached.get() + "\n[System Note: idempotent replay — side effect was not re-executed]");
        }
        String result = executor.get();
        // Only cache successful-looking outcomes (avoid caching pending-approval / hard errors forever)
        if (result != null && !result.contains("pending-approval") && !result.startsWith("Error ")
                && !result.startsWith("错误") && !result.startsWith("拒绝")) {
            remember(key, result);
        }
        return Optional.ofNullable(result);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> e = it.next();
            if (e.getValue().expiresAt().isBefore(now)) {
                it.remove();
            }
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
