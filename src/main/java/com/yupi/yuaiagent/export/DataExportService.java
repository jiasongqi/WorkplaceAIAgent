package com.yupi.yuaiagent.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Data export service — packages all user data into a ZIP backup.
 * <p>
 * ZIP structure:
 * <pre>
 *   backup-v1.zip
 *   ├── metadata.json      { schemaVersion, appVersion, exportedAt }
 *   ├── manifest.json      { sessions, messages, favorites, counts }
 *   ├── sessions/
 *   │   ├── chat-001.json
 *   │   └── chat-002.json
 *   ├── messages/
 *   │   ├── chat-001.json
 *   │   └── chat-002.json
 *   └── favorites/
 *       └── favorites.json
 * </pre>
 *
 * @author jsq
 */
@Slf4j
@Service
public class DataExportService {

    private static final String SCHEMA_VERSION = "1.0.0";
    private static final String APP_VERSION = "0.9.3";

    @Resource
    private SessionManager sessionManager;

    @Resource
    private PersistentMessageRepository messageRepository;

    @Resource
    private FavoriteRepository favoriteRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Exports all data for a user as a ZIP stream.
     */
    public void exportUser(String userId, OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            List<SessionInfo> sessions = sessionManager.getSessionsByStatus(
                    userId, com.yupi.yuaiagent.session.SessionStatus.ACTIVE);
            List<Favorite> favorites = favoriteRepository.findByUserId(userId);

            int totalMessages = 0;

            // metadata.json
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("schemaVersion", SCHEMA_VERSION);
            metadata.put("appVersion", APP_VERSION);
            metadata.put("exportedAt", LocalDateTime.now().atOffset(ZoneOffset.UTC).toString());
            writeJsonEntry(zip, "metadata.json", metadata);

            // sessions/
            for (SessionInfo session : sessions) {
                writeJsonEntry(zip, "sessions/" + session.getChatId() + ".json", session);
            }

            // messages/
            for (SessionInfo session : sessions) {
                List<PersistentChatMessage> messages = messageRepository.findByChatId(session.getChatId());
                writeJsonEntry(zip, "messages/" + session.getChatId() + ".json", messages);
                totalMessages += messages.size();
            }

            // favorites/
            writeJsonEntry(zip, "favorites/favorites.json", favorites);

            // manifest.json
            Map<String, Object> manifest = new HashMap<>();
            manifest.put("sessions", sessions.size());
            manifest.put("messages", totalMessages);
            manifest.put("favorites", favorites.size());
            manifest.put("exportedAt", LocalDateTime.now().atOffset(ZoneOffset.UTC).toString());
            writeJsonEntry(zip, "manifest.json", manifest);

            log.info("[export] user={}, sessions={}, messages={}, favorites={}",
                    userId, sessions.size(), totalMessages, favorites.size());
        }
    }

    private void writeJsonEntry(ZipOutputStream zip, String path, Object data) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(objectMapper.writeValueAsBytes(data));
        zip.closeEntry();
    }
}
