package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.document.DocumentMeta;
import com.yupi.yuaiagent.document.DocumentMetadataManager;
import com.yupi.yuaiagent.document.DocumentStatus;
import com.yupi.yuaiagent.usage.UsageEventType;
import com.yupi.yuaiagent.usage.UsageTracker;
import com.yupi.yuaiagent.dto.DocumentResponse;
import com.yupi.yuaiagent.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Document application service — upload, list, delete documents.
 *
 * @author jsq
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAppService {

    @org.springframework.beans.factory.annotation.Qualifier("aiChatVectorStore")
    private final VectorStore aiChatVectorStore;
    private final DocumentMetadataManager documentMetadataManager;
    private final UsageTracker usageTracker;

    public DocumentResponse upload(MultipartFile file, String status) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".md")) {
            throw new BusinessException(400, "仅支持 Markdown (.md) 文件");
        }

        byte[] bytes = file.getBytes();

        // Record upload + dedup check
        DocumentMeta meta = documentMetadataManager.recordUpload(filename, bytes.length, bytes);
        if (meta.getStatus() == DocumentStatus.INDEXED) {
            return toResponse(meta);
        }

        // Parse and index
        documentMetadataManager.updateStatus(meta.getDocId(), DocumentStatus.EMBEDDING);

        org.springframework.core.io.Resource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() { return filename; }
        };
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(false)
                .withIncludeBlockquote(false)
                .withAdditionalMetadata("filename", filename)
                .withAdditionalMetadata("status", status)
                .withAdditionalMetadata("docId", meta.getDocId())
                .build();
        MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
        List<Document> documents = reader.get();

        documentMetadataManager.updateStatus(meta.getDocId(), DocumentStatus.INDEXING);
        aiChatVectorStore.add(documents);

        documentMetadataManager.updateStatus(meta.getDocId(), DocumentStatus.INDEXED);
        usageTracker.track("system", UsageEventType.DOCUMENT_UPLOAD, null, 0);
        log.info("Document uploaded: docId={}, file={}, fragments={}", meta.getDocId(), filename, documents.size());
        return toResponse(meta);
    }

    public DocumentResponse addText(String content, String filename, String status) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        DocumentMeta meta = documentMetadataManager.recordUpload(filename, bytes.length, bytes);
        if (meta.getStatus() == DocumentStatus.INDEXED) {
            return toResponse(meta);
        }

        documentMetadataManager.updateStatus(meta.getDocId(), DocumentStatus.EMBEDDING);

        org.springframework.core.io.Resource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() { return filename.endsWith(".md") ? filename : filename + ".md"; }
        };
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withAdditionalMetadata("filename", filename)
                .withAdditionalMetadata("status", status)
                .withAdditionalMetadata("docId", meta.getDocId())
                .build();
        MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
        List<Document> documents = reader.get();

        documentMetadataManager.updateStatus(meta.getDocId(), DocumentStatus.INDEXING);
        aiChatVectorStore.add(documents);

        documentMetadataManager.updateStatus(meta.getDocId(), DocumentStatus.INDEXED);
        return toResponse(meta);
    }

    public List<DocumentResponse> listAll() {
        return documentMetadataManager.listAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public void delete(String docId) {
        DocumentMeta meta = documentMetadataManager.findByDocId(docId)
                .orElseThrow(() -> BusinessException.notFound("文档"));
        documentMetadataManager.updateStatus(docId, DocumentStatus.DELETED);
        log.info("Document soft-deleted: docId={}", docId);
    }

    private DocumentResponse toResponse(DocumentMeta meta) {
        DocumentResponse resp = new DocumentResponse();
        resp.setDocId(meta.getDocId());
        resp.setFileName(meta.getFileName());
        resp.setFileSize(meta.getFileSize());
        resp.setFileHash(meta.getFileHash());
        resp.setStatus(meta.getStatus());
        resp.setFailReason(meta.getFailReason());
        resp.setRetryCount(meta.getRetryCount());
        resp.setUploadedAt(meta.getUploadedAt());
        resp.setIndexedAt(meta.getIndexedAt());
        return resp;
    }
}
