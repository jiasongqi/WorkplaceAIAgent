package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.JwtUtil;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.registry.AgentDescriptor;
import com.yupi.yuaiagent.service.DigitalEmployeeAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/digital-employee")
@RequiredArgsConstructor
@Tag(name = "数字员工", description = "从模板创建与管理用户数字员工")
public class DigitalEmployeeController {

    private final DigitalEmployeeAppService digitalEmployeeAppService;
    private final JwtUtil jwtUtil;

    @GetMapping("/templates")
    @Operation(summary = "列出系统模板")
    public Response<List<AgentDescriptor>> templates(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = extractUserId(authHeader);
        if (userId == null) {
            return Response.failed(401, "未授权，请先登录");
        }
        return Response.success(digitalEmployeeAppService.listTemplates(userId));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的数字员工")
    public Response<List<DigitalEmployeeAppService.DigitalEmployeeView>> mine(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = extractUserId(authHeader);
        if (userId == null) {
            return Response.failed(401, "未授权，请先登录");
        }
        return Response.success(digitalEmployeeAppService.listMine(userId));
    }

    @PostMapping
    @Operation(summary = "从模板创建")
    public Response<DigitalEmployeeAppService.DigitalEmployeeView> create(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody DigitalEmployeeAppService.CreateRequest request) {
        String userId = extractUserId(authHeader);
        if (userId == null) {
            return Response.failed(401, "未授权，请先登录");
        }
        try {
            return Response.success(digitalEmployeeAppService.createFromTemplate(userId, request));
        } catch (IllegalArgumentException e) {
            return Response.failed(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新人设/技能（版本+1）")
    public Response<DigitalEmployeeAppService.DigitalEmployeeView> update(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id,
            @RequestBody DigitalEmployeeAppService.UpdateRequest request) {
        String userId = extractUserId(authHeader);
        if (userId == null) {
            return Response.failed(401, "未授权，请先登录");
        }
        try {
            return Response.success(digitalEmployeeAppService.updateViaChat(userId, id, request));
        } catch (IllegalArgumentException e) {
            return Response.failed(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "回滚到历史版本内容并生成新版本")
    public Response<DigitalEmployeeAppService.DigitalEmployeeView> rollback(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id,
            @RequestParam int version) {
        String userId = extractUserId(authHeader);
        if (userId == null) {
            return Response.failed(401, "未授权，请先登录");
        }
        try {
            return Response.success(digitalEmployeeAppService.rollback(userId, id, version));
        } catch (IllegalArgumentException e) {
            return Response.failed(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "设为当前委托数字员工")
    public Response<DigitalEmployeeAppService.DigitalEmployeeView> activate(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("id") String id) {
        String userId = extractUserId(authHeader);
        if (userId == null) {
            return Response.failed(401, "未授权，请先登录");
        }
        try {
            return Response.success(digitalEmployeeAppService.activate(userId, id));
        } catch (IllegalArgumentException e) {
            return Response.failed(400, e.getMessage());
        }
    }

    private String extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return jwtUtil.validateToken(authHeader.substring(7));
    }
}
