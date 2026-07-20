package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.usage.UsageTracker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * Usage statistics controller.
 *
 * @author jsq
 */
@RestController
@RequestMapping("/usage")
@Tag(name = "用量统计", description = "API使用量与Agent调用统计")
public class UsageController {

    @Resource
    private UsageTracker usageTracker;

    @Resource
    private AuthService authService;

    @GetMapping("/stats")
    @Operation(summary = "用量统计", description = "获取当前用户的API使用量与Agent调用统计")
    public Response<UsageTracker.UsageStats> getStats(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(usageTracker.getStats(userId));
    }
}
