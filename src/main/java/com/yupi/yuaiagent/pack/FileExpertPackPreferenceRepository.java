package com.yupi.yuaiagent.pack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-backed preferences with an in-memory version stamp to reject stale writes.
 */
public class FileExpertPackPreferenceRepository implements ExpertPackPreferenceRepository {

    private final Path storageFile;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, UserPackPreference> cache = new ConcurrentHashMap<>();

    public FileExpertPackPreferenceRepository(Path storageFile, ObjectMapper objectMapper) {
        this.storageFile = storageFile;
        this.objectMapper = objectMapper;
        load();
    }

    @Override
    public Optional<UserPackPreference> find(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(userId));
    }

    @Override
    public synchronized UserPackPreference save(UserPackPreference preference) {
        if (preference == null || preference.userId() == null || preference.userId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        UserPackPreference current = cache.get(preference.userId());
        if (current != null && preference.version() != current.version()) {
            throw new IllegalStateException("stale pack preference version for " + preference.userId());
        }
        UserPackPreference stored = new UserPackPreference(
                preference.userId(),
                preference.mode(),
                preference.packs(),
                current == null ? 1L : current.version() + 1
        );
        cache.put(preference.userId(), stored);
        persist();
        return stored;
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (storageFile == null || !Files.exists(storageFile)) {
            return;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(storageFile.toFile(), new TypeReference<>() {});
            if (raw == null) {
                return;
            }
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                cache.put(entry.getKey(), coerce(entry.getKey(), entry.getValue()));
            }
        } catch (IOException ignored) {
            // keep empty cache; callers still function with UNSET
        }
    }

    private UserPackPreference coerce(String userId, Object value) {
        if (value instanceof Map<?, ?> map && map.containsKey("mode")) {
            PackPreferenceMode mode = PackPreferenceMode.valueOf(String.valueOf(map.get("mode")));
            Map<String, Boolean> packs = new HashMap<>();
            Object packsRaw = map.get("packs");
            if (packsRaw instanceof Map<?, ?> packMap) {
                packMap.forEach((k, v) -> packs.put(String.valueOf(k), Boolean.TRUE.equals(v)));
            }
            long version = map.get("version") instanceof Number n ? n.longValue() : 1L;
            return new UserPackPreference(userId, mode, packs, version);
        }
        if (value instanceof Map<?, ?> legacy) {
            Map<String, Boolean> packs = new HashMap<>();
            legacy.forEach((k, v) -> packs.put(String.valueOf(k), Boolean.TRUE.equals(v)));
            PackPreferenceMode mode = packs.isEmpty()
                    ? PackPreferenceMode.UNSET
                    : packs.values().stream().allMatch(enabled -> !Boolean.TRUE.equals(enabled))
                    ? PackPreferenceMode.EXPLICIT_ALL_DISABLED
                    : PackPreferenceMode.EXPLICIT_PARTIAL;
            return new UserPackPreference(userId, mode, packs, 1L);
        }
        return new UserPackPreference(userId, PackPreferenceMode.UNSET, Map.of(), 1L);
    }

    private void persist() {
        try {
            if (storageFile.getParent() != null) {
                Files.createDirectories(storageFile.getParent());
            }
            Map<String, Object> payload = new HashMap<>();
            cache.forEach((userId, pref) -> payload.put(userId, Map.of(
                    "mode", pref.mode().name(),
                    "packs", pref.packs(),
                    "version", pref.version()
            )));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile.toFile(), payload);
        } catch (IOException ignored) {
            // repository remains usable in-memory
        }
    }
}
