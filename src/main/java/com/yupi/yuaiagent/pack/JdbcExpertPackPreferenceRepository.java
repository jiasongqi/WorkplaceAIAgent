package com.yupi.yuaiagent.pack;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC-backed preferences. Optimistic locking via {@code version}.
 */
public class JdbcExpertPackPreferenceRepository implements ExpertPackPreferenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcExpertPackPreferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserPackPreference> find(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "SELECT user_id, mode, packs_json, version FROM t_expert_pack_preference WHERE user_id = ?",
                rs -> rs.next() ? Optional.of(mapRow(rs)) : Optional.empty(),
                userId);
    }

    @Override
    @Transactional
    public UserPackPreference save(UserPackPreference preference) {
        if (preference == null || preference.userId() == null || preference.userId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        Optional<UserPackPreference> current = find(preference.userId());
        if (current.isPresent() && preference.version() != current.get().version()) {
            throw new IllegalStateException("stale pack preference version for " + preference.userId());
        }
        long nextVersion = current.map(p -> p.version() + 1).orElse(1L);
        String packsJson = packsToJson(preference.packs());
        if (current.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO t_expert_pack_preference(user_id, mode, packs_json, version) VALUES (?,?,?,?)",
                    preference.userId(), preference.mode().name(), packsJson, nextVersion);
        } else {
            int updated = jdbcTemplate.update(
                    "UPDATE t_expert_pack_preference SET mode = ?, packs_json = ?, version = ? WHERE user_id = ? AND version = ?",
                    preference.mode().name(), packsJson, nextVersion, preference.userId(), preference.version());
            if (updated != 1) {
                throw new IllegalStateException("stale pack preference version for " + preference.userId());
            }
        }
        return new UserPackPreference(preference.userId(), preference.mode(), preference.packs(), nextVersion);
    }

    private static UserPackPreference mapRow(ResultSet rs) throws SQLException {
        Map<String, Boolean> packs = jsonToPacks(rs.getString("packs_json"));
        return new UserPackPreference(
                rs.getString("user_id"),
                PackPreferenceMode.valueOf(rs.getString("mode")),
                packs,
                rs.getLong("version"));
    }

    private static String packsToJson(Map<String, Boolean> packs) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Boolean> entry : packs.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey().replace("\"", "")).append("\":").append(Boolean.TRUE.equals(entry.getValue()));
        }
        return sb.append('}').toString();
    }

    private static Map<String, Boolean> jsonToPacks(String json) {
        Map<String, Boolean> packs = new HashMap<>();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return packs;
        }
        String body = json.trim();
        if (body.startsWith("{")) {
            body = body.substring(1, body.endsWith("}") ? body.length() - 1 : body.length());
        }
        if (body.isBlank()) {
            return packs;
        }
        for (String part : body.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                packs.put(key, Boolean.parseBoolean(kv[1].trim()));
            }
        }
        return packs;
    }
}
