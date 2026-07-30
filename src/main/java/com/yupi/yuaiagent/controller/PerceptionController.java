package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.perception.PerceptionCrossValidator;
import com.yupi.yuaiagent.service.PerceptionAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Perception API — preprocess resume/offer docs before chat injection.
 */
@RestController
@RequestMapping("/perception")
@Tag(name = "感知预处理", description = "文档/图片感知降维，供职场 Agent 注入")
public class PerceptionController {

    private final PerceptionAppService perceptionAppService;
    private final AuthService authService;

    public PerceptionController(PerceptionAppService perceptionAppService, AuthService authService) {
        this.perceptionAppService = perceptionAppService;
        this.authService = authService;
    }

    @PostMapping("/preprocess")
    @Operation(summary = "预处理文档/图片", description = "提取文本+结构化字段；不绑定会话（适合调试）")
    public Response<Map<String, Object>> preprocess(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "hint", required = false, defaultValue = "resume") String hint)
            throws IOException {
        return Response.success(perceptionAppService.preprocess(file, hint));
    }

    @PostMapping("/preprocess-and-bind")
    @Operation(summary = "预处理并绑定到会话",
            description = "结果写入 Shared State，聊天只需发短句，避免 SSE URL 过长")
    public Response<Map<String, Object>> preprocessAndBind(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "hint", required = false, defaultValue = "resume") String hint,
            @RequestParam String chatId,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestHeader(value = "Authorization", required = false) String authHeader)
            throws IOException {
        var principal = authService.authenticatePrincipal(tokenParam, authHeader);
        return Response.success(perceptionAppService.preprocessAndBind(
                file, hint, chatId, principal.userId()));
    }

    @PostMapping("/cross-check")
    @Operation(summary = "感知假设 vs 工具观测交叉验证")
    public Response<PerceptionCrossValidator.CrossCheckResult> crossCheck(
            @RequestParam String hypothesis,
            @RequestParam String observed) {
        return Response.success(perceptionAppService.crossCheck(hypothesis, observed));
    }
}
