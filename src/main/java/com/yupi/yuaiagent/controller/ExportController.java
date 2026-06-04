package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.dto.ImportResult;
import com.yupi.yuaiagent.service.ExportAppService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Export/Import controller — thin HTTP adapter.
 * All business logic is in {@link ExportAppService}.
 *
 * @author jsq
 */
@RestController
@RequestMapping("/export")
public class ExportController {

    @Resource
    private ExportAppService exportAppService;

    @Resource
    private AuthService authService;

    @GetMapping("/all")
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
    public Response<ImportResult> importData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) throws IOException {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(exportAppService.importData(userId, file));
    }
}
