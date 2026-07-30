package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.service.SkillAppService;
import com.yupi.yuaiagent.skill.SkillDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/skill")
@Tag(name = "技能", description = "技能列表与 Trace 沉淀")
public class SkillController {

    @Resource
    private SkillAppService skillAppService;
    @Resource
    private AuthService authService;

    @GetMapping("/list")
    @Operation(summary = "技能列表")
    public Response<List<Map<String, Object>>> list(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        return Response.success(skillAppService.listSummaries());
    }

    @PostMapping("/draft-from-trace")
    @Operation(summary = "从 Trace 生成技能草稿")
    public Response<SkillDefinition> draftFromTrace(
            @RequestBody Map<String, String> body,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        String traceId = body != null ? body.get("traceId") : null;
        return Response.success(skillAppService.draftFromTrace(traceId));
    }

    @PostMapping("/save")
    @Operation(summary = "保存技能草稿到注册中心")
    public Response<SkillDefinition> save(
            @RequestBody SkillDefinition draft,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        return Response.success(skillAppService.saveDraft(draft));
    }
}
