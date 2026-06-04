package com.yupi.yuaiagent.dto;

import com.yupi.yuaiagent.document.DocumentStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Document metadata response.
 */
@Data
public class DocumentResponse {
    private String docId;
    private String fileName;
    private long fileSize;
    private String fileHash;
    private DocumentStatus status;
    private String failReason;
    private int retryCount;
    private LocalDateTime uploadedAt;
    private LocalDateTime indexedAt;
}
