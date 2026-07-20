package com.yupi.yuaiagent.favorite;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Favorite repository — file-based persistence.
 *
 * <p>NOTE: @Transactional is NOT applicable — file-based storage is not managed by
 * Spring's transaction manager. Consider adding a ReadWriteLock for concurrency safety
 * if concurrent writes are expected.
 *
 * @author jsq
 */
@Slf4j
@Repository
public class FavoriteRepository {

    @Value("${artifact.storage.dir:./tmp/artifacts}")
    private String storageDir;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** userId → List<Favorite> */
    private final Map<String, List<Favorite>> userFavorites = new ConcurrentHashMap<>();

    private File storageFile;

    @PostConstruct
    public void init() {
        File dir = new File(storageDir);
        if (!dir.exists()) dir.mkdirs();
        storageFile = new File(dir, "favorites.json");
        loadFromFile();
        log.info("[favorite] repository initialized, users: {}", userFavorites.size());
    }

    public Favorite add(Favorite fav) {
        fav.setFavoriteId(UUID.randomUUID().toString().replace("-", ""));
        fav.setCreatedAt(LocalDateTime.now());
        userFavorites.computeIfAbsent(fav.getUserId(), k -> new ArrayList<>()).add(fav);
        saveToFile();
        log.debug("[favorite] added: userId={}, chatId={}, messageId={}", fav.getUserId(), fav.getChatId(), fav.getMessageId());
        return fav;
    }

    public boolean remove(String userId, String favoriteId) {
        List<Favorite> favs = userFavorites.get(userId);
        if (favs == null) return false;
        boolean removed = favs.removeIf(f -> f.getFavoriteId().equals(favoriteId));
        if (removed) saveToFile();
        return removed;
    }

    public List<Favorite> findByUserId(String userId) {
        return userFavorites.getOrDefault(userId, List.of());
    }

    /**
     * Marks all favorites from a given chatId as orphaned.
     * Called when a session is deleted.
     */
    public void markOrphanedByChatId(String chatId) {
        for (List<Favorite> favs : userFavorites.values()) {
            for (Favorite fav : favs) {
                if (chatId.equals(fav.getChatId())) {
                    fav.setOrphaned(true);
                }
            }
        }
        saveToFile();
    }

    // ─── File I/O ───

    private void loadFromFile() {
        if (storageFile.exists() && storageFile.length() > 0) {
            try {
                Map<String, List<Favorite>> loaded = objectMapper.readValue(storageFile,
                        new TypeReference<Map<String, List<Favorite>>>() {});
                userFavorites.putAll(loaded);
            } catch (IOException e) {
                log.error("[favorite] failed to load file", e);
            }
        }
    }

    private void saveToFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, userFavorites);
        } catch (IOException e) {
            log.error("[favorite] failed to save file", e);
        }
    }
}
