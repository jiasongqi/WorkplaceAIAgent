package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.common.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RAG 知识库文档管理接口
 */
@RestController
@RequestMapping("/document")
@Slf4j
public class DocumentController {

    @Resource
    private VectorStore aiChatVectorStore;

    /**
     * 上传 Markdown 文档并实时更新向量库
     */
    @PostMapping("/upload")
    public Result<String> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "status", defaultValue = "通用") String status) {
        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".md")) {
            return Result.error(400, "仅支持 Markdown (.md) 文件");
        }
        try {
            byte[] bytes = file.getBytes();
            org.springframework.core.io.Resource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .withAdditionalMetadata("filename", filename)
                    .withAdditionalMetadata("status", status)
                    .build();
            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
            List<Document> documents = reader.get();
            aiChatVectorStore.add(documents);
            log.info("成功上传文档：{}，共 {} 个片段", filename, documents.size());
            return Result.success("文档上传成功，共嵌入 " + documents.size() + " 个片段");
        } catch (IOException e) {
            log.error("文档上传失败", e);
            return Result.error("文档上传失败：" + e.getMessage());
        }
    }

    /**
     * 通过文本内容直接更新知识库
     */
    @PostMapping("/add")
    public Result<String> addDocument(
            @RequestParam("content") String content,
            @RequestParam("filename") String filename,
            @RequestParam(value = "status", defaultValue = "通用") String status) {
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            org.springframework.core.io.Resource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename.endsWith(".md") ? filename : filename + ".md";
                }
            };
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withAdditionalMetadata("filename", filename)
                    .withAdditionalMetadata("status", status)
                    .build();
            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
            List<Document> documents = reader.get();
            aiChatVectorStore.add(documents);
            return Result.success("知识库更新成功，共嵌入 " + documents.size() + " 个片段");
        } catch (Exception e) {
            log.error("知识库更新失败", e);
            return Result.error("知识库更新失败：" + e.getMessage());
        }
    }
}
