package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.sandbox.SandboxFactory;
import com.yupi.yuaiagent.sandbox.SandboxPolicy;
import com.yupi.yuaiagent.service.TaskCenterAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/task")
@Tag(name = "任务中心", description = "待确认 HITL 与暂停工作流")
public class TaskController {

    @Resource
    private TaskCenterAppService taskCenterAppService;
    @Resource
    private AuthService authService;
    @Resource
    private SandboxFactory sandboxFactory;

    @GetMapping("/mine")
    @Operation(summary = "我的任务")
    public Response<List<Map<String, Object>>> mine(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(taskCenterAppService.listMine(userId));
    }

    @GetMapping("/sandbox-policy")
    @Operation(summary = "当前沙箱策略（用户可读）")
    public Response<Map<String, Object>> sandboxPolicy(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.authenticate(token, authHeader);
        SandboxPolicy policy = sandboxFactory.getActivePolicy();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("policy", policy.name());
        body.put("label", describe(policy));
        body.put("dockerAvailable", sandboxFactory.isDockerAvailable());
        body.put("lastExec", LastSandboxExecHolder.get());
        return Response.success(body);
    }

    private static String describe(SandboxPolicy policy) {
        return switch (policy) {
            case DOCKER_SANDBOX -> "Docker 沙箱：工作区可写 / 根只读 / 默认无网络";
            case PROCESS_SANDBOX -> "本地进程沙箱（仅开发）：有限隔离";
            case UNSANDBOXED -> "未启用沙箱";
        };
    }
}
