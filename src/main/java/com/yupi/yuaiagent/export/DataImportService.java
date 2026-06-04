package com.yupi.yuaiagent.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.favorite.Favorite;
import com.yupi.yuaiagent.favorite.FavoriteRepository;
import com.yupi.yuaiagent.message.PersistentChatMessage;
import com.yupi.yuaiagent.message.PersistentMessageRepository;
import com.yupi.yuaiagent.session.SessionManager;
import com.yupi.yuaiagent.session.SessionManager.SessionInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Data import service — restores user data from a ZIP backup.
 * <p>
 * Merge strategy: chatId conflicts → generate new chatId, update internal references.
 *
 * @author jsq
 */
@Slf4j
@Service
public class DataImportService {

    @Resource
    private SessionManager sessionManager;

    @Resource
    private PersistentMessageRepository messageRepository;

    @Resource
    private FavoriteRepository favoriteRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Imports data from a ZIP stream. Returns a summary of what was imported.
     */
    public ImportResult importFromZip(String userId, InputStream zipStream) throws IOException {
        int sessionsImported = 0;
        int sessionsSkipped = 0;
        int messagesImported = 0;
        int favoritesImported = 0;

        Map<String, String> chatIdMapping = new HashMap<>(); // oldChatId → newChatId

        try (ZipInputStream zip = new ZipInputStream(zipStream)) {
            Map<String, byte[]> entries = readAllEntries(zip);

            // Parse sessions
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (!entry.getKey().startsWith("sessions/") || !entry.getKey().endsWith(".json")) continue;

                List<SessionInfo> sessionList = objectMapper.readValue(entry.getValue(),
                        new TypeReference<List<SessionInfo>>() {});
                // Handle both single session and list format
                List<SessionInfo> toImport = sessionList != null ? sessionList : List.of();

                for (SessionInfo session : toImport) {
                    String oldChatId = session.getChatId();
                    // Check if chatId already exists
                    SessionInfo existing = sessionManager.findByChatId(oldChatId);
                    if (existing != null) {
                        // Generate new chatId
                        String newChatId = UUID.randomUUID().toString();
                        chatIdMapping.put(oldChatId, newChatId);
                        session.setChatId(newChatId);
                        sessionsImported++;
                    } else {
                        sessionsImported++;
                    }
                    // Create session via SessionManager
                    sessionManager.createSession(userId, session.getTitle());
                }
            }

            // Parse messages
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                if (!entry.getKey().startsWith("messages/") || !entry.getKey().endsWith(".json")) continue;

                List<PersistentChatMessage> messages = objectMapper.readValue(entry.getValue(),
                        new TypeReference<List<PersistentChatMessage>>() {});

                for (PersistentChatMessage msg : messages) {
                    String chatId = msg.getChatId();
                    // Apply chatId mapping if needed
                    if (chatIdMapping.containsKey(chatId)) {
                        chatId = chatIdMapping.get(chatId);
                    }
                    messageRepository.save(chatId, msg.getRole(), msg.getContent());
                    messagesImported++;
                }
            }

            // Parse favorites
            byte[] favBytes = entries.get("favorites/favorites.json");
            if (favBytes != null) {
                List<Favorite> favorites = objectMapper.readValue(favBytes,
                        new TypeReference<List<Favorite>>() {});
                for (Favorite fav : favorites) {
                    fav.setUserId(userId);
                    // Apply chatId mapping
                    if (chatIdMapping.containsKey(fav.getChatId())) {
                        fav.setChatId(chatIdMapping.get(fav.getChatId()));
                    }
                    favoriteRepository.add(fav);
                    favoritesImported++;
                }
            }
        }

        ImportResult result = new ImportResult();
        result.setSessionsImported(sessionsImported);
        result.setSessionsSkipped(sessionsSkipped);
        result.setMessagesImported(messagesImported);
        result.setFavoritesImported(favoritesImported);

        log.info("[import] user={}, sessions={}, messages={}, favorites={}",
                userId, sessionsImported, messagesImported, favoritesImported);
        return result;
    }

    private Map<String, byte[]> readAllEntries(ZipInputStream zip) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            entries.put(entry.getName(), zip.readAllBytes());
            zip.closeEntry();
        }
        return entries;
    }

    @Data
    public static class ImportResult {
        private int sessionsImported;
        private int sessionsSkipped;
        private int messagesImported;
        private int favoritesImported;
    }
}
