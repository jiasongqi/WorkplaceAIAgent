package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactQuery;
import com.yupi.yuaiagent.artifact.model.ArtifactSummary;
import com.yupi.yuaiagent.auth.JwtUtil;
import com.yupi.yuaiagent.common.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员交付物 REST 接口（Req 17）。
 * <p>
 * 供管理界面查询与查看各数据员工产出的交付物。所有接口均做管理员权限校验：
 * 从 {@code Authorization: Bearer <token>} 头取出 JWT，先用 {@link JwtUtil#validateToken(String)}
 * 校验有效性，再用 {@link JwtUtil#getUsername(String)} 取 username，约定 username 等于
 * 配置项 {@code admin.username}（默认 {@code admin}）才视为管理员。
 * <p>
 * 当前项目尚无完整角色体系，故采用上述简化的"管理员用户名"约定。无有效 JWT 或非管理员
 * 一律返回 403 且不返回任何交付物数据（Req 17.4 / 17.5）。
 *
 * @author jsq
 */
@RestController
@RequestMapping("/artifact")
@Slf4j
public class ArtifactController {

    @Resource
    private ArtifactShelf artifactShelf;

    @Resource
    private JwtUtil jwtUtil;

    /**
     * 管理员用户名（约定该 username 视为管理员）。可通过配置项 {@code admin.username} 覆盖，默认 {@code admin}。
     */
    @Value("${admin.username:admin}")
    private String adminUsername;

    /**
     * 管理员按 userId / chatId / type 查询交付物列表，返回轻量摘要（Req 17.1 / 17.2）。
     * 查询参数均可选，为空则不参与约束。
     * 非管理员返回 403 且不返回任何交付物数据（Req 17.4 / 17.5）。
     */
    @GetMapping("/list")
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
