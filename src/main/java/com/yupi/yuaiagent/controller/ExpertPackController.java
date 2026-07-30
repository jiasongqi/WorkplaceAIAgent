package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.pack.ExpertPackView;
import com.yupi.yuaiagent.service.ExpertPackAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pack")
@Tag(name = "专家包", description = "专家包启用/禁用")
public class ExpertPackController {

    @Resource
    private ExpertPackAppService expertPackAppService;
    @Resource
    private AuthService authService;

    @GetMapping("/list")
    @Operation(summary = "专家包列表")
    public Response<List<ExpertPackView>> list(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(expertPackAppService.listForUser(userId));
    }

    @PostMapping("/{packId}/enabled")
    @Operation(summary = "启用或禁用专家包")
    public Response<Void> setEnabled(
            @PathVariable String packId,
            @RequestBody Map<String, Object> body,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("enabled"));
        expertPackAppService.setEnabled(userId, packId, enabled);
        return Response.success();
    }
}
