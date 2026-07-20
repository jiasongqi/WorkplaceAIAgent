package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.dto.ImportResult;
import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.service.ExportAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * Export/Import controller — thin HTTP adapter.
 * All business logic is in {@link ExportAppService}.
 *
 * @author jsq
 */
@RestController
@RequestMapping("/export")
@Tag(name = "数据导出", description = "用户数据备份与导入")
public class ExportController {

    private static final Set<String> ALLOWED_IMPORT_TYPES = Set.of(
            "application/zip", "application/x-zip-compressed", "application/octet-stream");

    @Resource
    private ExportAppService exportAppService;

    @Resource
    private AuthService authService;

    @GetMapping("/all")
    @Operation(summary = "导出全部数据", description = "导出用户全部数据为ZIP备份文件")
    public void exportAll(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletResponse response) throws IOException {
        String userId = authService.authenticate(token, authHeader);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"backup-v1.zip\"");
        exportAppService.exportAll(userId, response.getOutputStream());
        response.getOutputStream().flush();
    }

    @PostMapping("/import")
    @Operation(summary = "导入数据", description = "从ZIP备份文件导入用户数据")
    public Response<ImportResult> importData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) throws IOException {
        String userId = authService.authenticate(token, authHeader);
        // Validate file type
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        boolean validType = (contentType != null && ALLOWED_IMPORT_TYPES.contains(contentType))
                || (originalFilename != null && originalFilename.toLowerCase().endsWith(".zip"));
        if (!validType) {
            throw BusinessException.badRequest("仅支持导入ZIP格式的备份文件");
        }
        return Response.success(exportAppService.importData(userId, file));
    }
}
