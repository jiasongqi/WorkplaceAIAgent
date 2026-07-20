package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.dto.DocumentResponse;
import com.yupi.yuaiagent.service.DocumentAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Document management controller — thin HTTP adapter.
 * All business logic is in {@link DocumentAppService}.
 *
 * @author jsq
 */
@RestController
@RequestMapping("/document")
@Tag(name = "知识库文档", description = "知识库文档上传、列表、删除管理")
public class DocumentController {

    @Resource
    private DocumentAppService documentAppService;

    @Resource
    private AuthService authService;

    @PostMapping("/upload")
    @Operation(summary = "上传文档", description = "上传文件到知识库")
    public Response<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "status", defaultValue = "通用") String status,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) throws IOException {
        authService.authenticate(token, authHeader);
        return Response.success(documentAppService.upload(file, status));
    }

    @PostMapping("/add")
    @Operation(summary = "添加文本文档", description = "直接添加文本内容到知识库")
    public Response<DocumentResponse> addDocument(
            @RequestParam("content") String content,
            @RequestParam("filename") String filename,
            @RequestParam(value = "status", defaultValue = "通用") String status,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) throws IOException {
        authService.authenticate(token, authHeader);
        return Response.success(documentAppService.addText(content, filename, status));
    }

    @GetMapping("/list")
    @Operation(summary = "文档列表", description = "获取知识库中的所有文档列表")
    public Response<List<DocumentResponse>> listDocuments(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        return Response.success(documentAppService.listAll());
    }

    @DeleteMapping("/{docId}")
    @Operation(summary = "删除文档", description = "从知识库中删除指定文档")
    public Response<Void> deleteDocument(
            @PathVariable String docId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        documentAppService.delete(docId);
        return Response.success();
    }
}
