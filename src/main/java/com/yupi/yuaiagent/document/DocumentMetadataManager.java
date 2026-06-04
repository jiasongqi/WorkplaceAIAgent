package com.yupi.yuaiagent.document;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Unified document metadata manager — single source of truth for document lifecycle.
 * <p>
 * Tracks: upload → parse → embed → index → ready (or failed).
 * Handles file-level deduplication via SHA-256 hash.
 * Supports retriable failures with configurable max retries.
 *
 * @author jsq
 */
@Slf4j
@Service
public class DocumentMetadataManager {

    @Value("${artifact.storage.dir:./tmp/artifacts}")
    private String storageDir;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** docId → DocumentMeta */
    private final Map<String, DocumentMeta> metaIndex = new ConcurrentHashMap<>();

    /** fileHash → docId (dedup index) */
    private final Map<String, String> hashIndex = new ConcurrentHashMap<>();

    private File storageFile;

    @PostConstruct
    public void init() {
        File dir = new File(storageDir);
        if (!dir.exists()) dir.mkdirs();
        storageFile = new File(dir, "documents.json");
        loadFromFile();
        log.info("[document] metadata manager initialized, docs: {}", metaIndex.size());
    }

    // ─── Lifecycle ───

    /**
     * Records a new document upload. Checks for file-hash dedup first.
     *
     * @return existing meta if duplicate, or newly created meta
     */
    public DocumentMeta recordUpload(String fileName, long fileSize, byte[] fileBytes) {
        String fileHash = sha256(fileBytes);

        // Dedup check
        String existingDocId = hashIndex.get(fileHash);
        if (existingDocId != null) {
            DocumentMeta existing = metaIndex.get(existingDocId);
            if (existing != null && existing.getStatus() == DocumentStatus.INDEXED) {
                log.info("[document] duplicate detected: {} (hash={}), returning existing", fileName, fileHash);
                return existing;
            }
        }

        DocumentMeta meta = new DocumentMeta();
        meta.setDocId(IdUtil.fastSimpleUUID());
        meta.setFileName(fileName);
        meta.setFileSize(fileSize);
        meta.setFileHash(fileHash);
        meta.setStatus(DocumentStatus.UPLOADING);
        meta.setUploadedAt(LocalDateTime.now());

        metaIndex.put(meta.getDocId(), meta);
        hashIndex.put(fileHash, meta.getDocId());
        saveToFile();

        log.info("[document] recorded upload: docId={}, file={}, size={}", meta.getDocId(), fileName, fileSize);
        return meta;
    }

    public void updateStatus(String docId, DocumentStatus newStatus) {
        DocumentMeta meta = metaIndex.get(docId);
        if (meta == null) return;
        meta.setStatus(newStatus);
        if (newStatus == DocumentStatus.INDEXED) {
            meta.setIndexedAt(LocalDateTime.now());
        }
        saveToFile();
    }

    public void markFailed(String docId, String reason, boolean retryable) {
        DocumentMeta meta = metaIndex.get(docId);
        if (meta == null) return;

        if (retryable && meta.getRetryCount() < meta.getMaxRetry()) {
            meta.setStatus(DocumentStatus.FAILED_RETRYABLE);
            meta.setRetryCount(meta.getRetryCount() + 1);
            meta.setLastRetryAt(LocalDateTime.now());
        } else {
            meta.setStatus(DocumentStatus.FAILED_FINAL);
        }
        meta.setFailReason(reason);
        saveToFile();

        log.warn("[document] docId={} failed: {} (retryable={}, retryCount={})",
                docId, reason, retryable, meta.getRetryCount());
    }

    // ─── Query ───

    public List<DocumentMeta> listAll() {
        return new ArrayList<>(metaIndex.values());
    }

    public List<DocumentMeta> listByStatus(DocumentStatus status) {
        return metaIndex.values().stream()
                .filter(m -> m.getStatus() == status)
                .collect(Collectors.toList());
    }

    public Optional<DocumentMeta> findByDocId(String docId) {
        return Optional.ofNullable(metaIndex.get(docId));
    }

    public Optional<DocumentMeta> findByFileHash(String fileHash) {
        String docId = hashIndex.get(fileHash);
        return docId != null ? Optional.ofNullable(metaIndex.get(docId)) : Optional.empty();
    }

    // ─── Hash ───

    public static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ─── File I/O ───

    private void loadFromFile() {
        if (storageFile.exists() && storageFile.length() > 0) {
            try {
                List<DocumentMeta> list = objectMapper.readValue(storageFile,
                        new TypeReference<List<DocumentMeta>>() {});
                for (DocumentMeta meta : list) {
                    metaIndex.put(meta.getDocId(), meta);
                    if (meta.getFileHash() != null) {
                        hashIndex.put(meta.getFileHash(), meta.getDocId());
                    }
                }
            } catch (IOException e) {
                log.error("[document] failed to load metadata file", e);
            }
        }
    }

    private void saveToFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, new ArrayList<>(metaIndex.values()));
        } catch (IOException e) {
            log.error("[document] failed to save metadata file", e);
        }
    }
}
