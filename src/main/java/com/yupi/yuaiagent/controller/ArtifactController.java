package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactQuery;
import com.yupi.yuaiagent.artifact.model.ArtifactSummary;
import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.auth.JwtUtil;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.service.ArtifactAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 交付物 REST：用户侧 mine 接口 + 管理员 list/detail。
 *
 * @author jsq
 */
@RestController
@RequestMapping("/artifact")
@Slf4j
@Tag(name = "产物管理", description = "Agent生成产物的查询（用户侧 mine + 管理员）")
public class ArtifactController {

    @Resource
    private ArtifactShelf artifactShelf;

    @Resource
    private ArtifactAppService artifactAppService;

    @Resource
    private AuthService authService;

    @Resource
    private JwtUtil jwtUtil;

    /**
     * 管理员用户名（约定该 username 视为管理员）。可通过配置项 {@code admin.username} 覆盖，默认 {@code admin}。
     */
    @Value("${admin.username:admin}")
    private String adminUsername;

    @GetMapping("/mine")
    @Operation(summary = "我的产物列表", description = "按当前用户 + 可选 chatId 查询交付物摘要")
    public Response<List<ArtifactSummary>> listMine(
            @RequestParam(required = false) String chatId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(artifactAppService.listMine(userId, chatId));
    }

    @GetMapping("/mine/{artifactId}")
    @Operation(summary = "我的产物详情", description = "校验归属后返回完整交付物")
    public Response<Artifact> mineDetail(
            @PathVariable String artifactId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(artifactAppService.getMine(userId, artifactId));
    }

    /**
     * 管理员按 userId / chatId / type 查询交付物列表，返回轻量摘要（Req 17.1 / 17.2）。
     * 查询参数均可选，为空则不参与约束。
     * 非管理员返回 403 且不返回任何交付物数据（Req 17.4 / 17.5）。
     */
    @GetMapping("/list")
    @Operation(summary = "产物列表", description = "管理员按条件查询Agent生成的产物列表")
    public Response<List<ArtifactSummary>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String chatId,
            @RequestParam(required = false) String type,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!isAdmin(authHeader)) {
            return Response.failed(403, "需要管理员权限");
        }
        List<Artifact> list = artifactShelf.query(ArtifactQuery.builder()
                .userId(userId)
                .chatId(chatId)
                .type(type)
                .build());
        List<ArtifactSummary> summaries = list.stream()
                .map(ArtifactSummary::from)
                .toList();
        return Response.success(summaries);
    }

    /**
     * 管理员查看某交付物的完整内容（Req 17.3）。
     * 非管理员返回 403 且不返回任何交付物数据（Req 17.5）。
     */
    @GetMapping("/{artifactId}")
    @Operation(summary = "产物详情", description = "管理员查看指定产物的完整内容")
    public Response<Artifact> detail(
            @PathVariable String artifactId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!isAdmin(authHeader)) {
            return Response.failed(403, "需要管理员权限");
        }
        return Response.success(artifactShelf.get(artifactId).orElse(null));
    }

    /**
     * 管理员权限校验：
     * <ol>
     *     <li>请求头须为 {@code Bearer <token>} 格式</li>
     *     <li>JWT 须通过 {@link JwtUtil#validateToken(String)} 校验（有效且未过期）</li>
     *     <li>JWT 中的 username 须等于约定的管理员用户名</li>
     * </ol>
     * 任一条件不满足均视为非管理员。
     */
    private boolean isAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7);
        // 校验 JWT 有效性（无效或过期返回 null）
        if (jwtUtil.validateToken(token) == null) {
            return false;
        }
        String username = jwtUtil.getUsername(token);
        return adminUsername.equals(username);
    }
}
