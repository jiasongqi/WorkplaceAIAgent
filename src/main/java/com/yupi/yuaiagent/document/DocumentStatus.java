package com.yupi.yuaiagent.document;

/**
 * Document processing lifecycle status.
 * <p>
 * State machine:
 * <pre>
 *   UPLOADING → PARSING → EMBEDDING → INDEXING → INDEXED
 *       ↓         ↓         ↓          ↓
 *     FAILED    FAILED    FAILED     FAILED_RETRYABLE
 *                                            ↓
 *                                        FAILED_FINAL
 *
 *   Any state → DELETED (soft delete)
 * </pre>
 *
 * @author jsq
 */
public enum DocumentStatus {

    /** File upload in progress. */
    UPLOADING("上传中"),

    /** Document parsing (PDF/MD → text extraction). */
    PARSING("解析中"),

    /** Vectorization in progress. */
    EMBEDDING("向量化中"),

    /** Writing to vector store. */
    INDEXING("索引中"),

    /** Ready — RAG can use this document. */
    INDEXED("已就绪"),

    /** Retriable failure (network timeout, rate limit). Will be auto-retried. */
    FAILED_RETRYABLE("可重试失败"),

    /** Final failure (corrupted file, unsupported format). No more retries. */
    FAILED_FINAL("最终失败"),

    /** Soft-deleted. */
    DELETED("已删除");

    private final String displayName;

    DocumentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isFailed() {
        return this == FAILED_RETRYABLE || this == FAILED_FINAL;
    }

    public boolean isTerminal() {
        return this == INDEXED || this == FAILED_FINAL || this == DELETED;
    }
}
