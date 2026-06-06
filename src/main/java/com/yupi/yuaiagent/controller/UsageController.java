package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.usage.UsageTracker;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * Usage statistics controller.
 *
 * @author jsq
 */
@RestController
@RequestMapping("/usage")
public class UsageController {

    @Resource
    private UsageTracker usageTracker;

    @Resource
    private AuthService authService;

    @GetMapping("/stats")
    public Response<UsageTracker.UsageStats> getStats(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(usageTracker.getStats(userId));
    }
}
