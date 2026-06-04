package com.yupi.yuaiagent.document;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Document metadata — tracks processing lifecycle and deduplication.
 *
 * @author jsq
 */
@Data
public class DocumentMeta {

    private String docId;
    private String fileName;
    private long fileSize;

    /** SHA-256 of file bytes — file-level dedup. */
    private String fileHash;

    /** SHA-256 of extracted text — content-level dedup (reserved for future). */
    private String contentHash;

    private DocumentStatus status;
    private String failReason;

    /** Number of retry attempts (for FAILED_RETRYABLE). */
    private int retryCount;

    /** Maximum retry attempts. */
    private int maxRetry = 3;

    private LocalDateTime lastRetryAt;
    private LocalDateTime uploadedAt;
    private LocalDateTime indexedAt;
}
