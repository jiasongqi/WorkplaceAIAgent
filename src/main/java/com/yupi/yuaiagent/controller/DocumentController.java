package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.dto.DocumentResponse;
import com.yupi.yuaiagent.service.DocumentAppService;
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
public class DocumentController {

    @Resource
    private DocumentAppService documentAppService;

    @PostMapping("/upload")
    public Response<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "status", defaultValue = "通用") String status) throws IOException {
        return Response.success(documentAppService.upload(file, status));
    }

    @PostMapping("/add")
    public Response<DocumentResponse> addDocument(
            @RequestParam("content") String content,
            @RequestParam("filename") String filename,
            @RequestParam(value = "status", defaultValue = "通用") String status) throws IOException {
        return Response.success(documentAppService.addText(content, filename, status));
    }

    @GetMapping("/list")
    public Response<List<DocumentResponse>> listDocuments() {
        return Response.success(documentAppService.listAll());
    }

    @DeleteMapping("/{docId}")
    public Response<Void> deleteDocument(@PathVariable String docId) {
        documentAppService.delete(docId);
        return Response.success();
    }
}
