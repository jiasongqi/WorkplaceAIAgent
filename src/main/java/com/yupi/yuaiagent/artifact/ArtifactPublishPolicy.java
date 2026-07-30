package com.yupi.yuaiagent.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yupi.yuaiagent.artifact.model.Artifact;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Admission policy for reusable, structured artifacts.
 */
public class ArtifactPublishPolicy {

    private final ArtifactTypeCatalog catalog;
    private final ObjectMapper objectMapper;

    public ArtifactPublishPolicy(ArtifactTypeCatalog catalog, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    public Decision evaluate(Artifact draft, String sourceTraceId) {
        if (draft == null) {
            return Decision.reject("交付物不能为空");
        }
        ArtifactTypeCatalog.TypeDefinition definition = catalog.find(draft.getType()).orElse(null);
        if (definition == null) {
            return Decision.reject("未登记的交付物类型: " + draft.getType());
        }
        if (!definition.reusable()) {
            return Decision.reject("过程记录不可作为可复用交付物发布");
        }
        if (draft.getContent() == null || draft.getContent().isBlank()) {
            return Decision.reject("结构化内容不能为空");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(draft.getContent());
        } catch (Exception e) {
            return Decision.reject("交付物内容必须是合法 JSON");
        }
        if (root == null || !root.isObject()) {
            return Decision.reject("交付物内容必须是 JSON 对象");
        }
        String summary = root.path("summary").asText("").trim();
        if (summary.isEmpty()) {
            return Decision.reject("结构化交付物缺少 summary");
        }
        if (draft.getUserId() == null || draft.getUserId().isBlank()) {
            return Decision.reject("交付物缺少 userId");
        }
        if (definition.scope() == com.yupi.yuaiagent.artifact.model.ArtifactScope.TASK
                && (draft.getChatId() == null || draft.getChatId().isBlank())) {
            return Decision.reject("TASK 交付物缺少 chatId");
        }

        draft.setSummary(summary);
        draft.setReusable(true);
        draft.setScope(definition.scope());
        draft.setTargetAgents(definition.targetAgents());
        draft.setSchemaVersion(definition.schemaVersion());
        draft.setSourceTraceId(sourceTraceId);
        if (definition.ttl() != null) {
            draft.setExpiresAt(LocalDateTime.now(java.time.Clock.systemUTC()).plus(definition.ttl()));
        }
        draft.setDedupKey(dedupKey(draft, canonicalJson(root)));
        return Decision.accept(draft);
    }

    private String canonicalJson(JsonNode root) {
        try {
            Object value = objectMapper.convertValue(root, Object.class);
            return objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法规范化交付物 JSON", e);
        }
    }

    private String dedupKey(Artifact artifact, String canonicalContent) {
        String owner = artifact.getScope() == com.yupi.yuaiagent.artifact.model.ArtifactScope.USER_PROFILE
                ? artifact.getUserId() : artifact.getChatId();
        String source = String.join("\u001f",
                nullToEmpty(owner),
                nullToEmpty(artifact.getType()),
                nullToEmpty(artifact.getProducer()),
                nullToEmpty(canonicalContent));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成交付物去重键", e);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record Decision(boolean accepted, String reason, Artifact artifact) {
        public static Decision accept(Artifact artifact) {
            return new Decision(true, null, artifact);
        }

        public static Decision reject(String reason) {
            return new Decision(false, reason, null);
        }
    }
}
