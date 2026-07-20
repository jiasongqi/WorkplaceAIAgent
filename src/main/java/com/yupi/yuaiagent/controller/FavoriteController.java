package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.dto.AddFavoriteRequest;
import com.yupi.yuaiagent.favorite.Favorite;
import com.yupi.yuaiagent.service.FavoriteAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Favorite controller — thin HTTP adapter.
 * All business logic is in {@link FavoriteAppService}.
 *
 * @author jsq
 */
@RestController
@RequestMapping("/favorite")
@Validated
@Tag(name = "收藏", description = "消息收藏管理")
public class FavoriteController {

    @Resource
    private FavoriteAppService favoriteAppService;

    @Resource
    private AuthService authService;

    @PostMapping
    @Operation(summary = "添加收藏", description = "收藏一条消息")
    public Response<Favorite> addFavorite(
            @Valid @RequestBody AddFavoriteRequest request,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(favoriteAppService.add(userId, request));
    }

    @DeleteMapping("/{favoriteId}")
    @Operation(summary = "取消收藏", description = "移除一条收藏")
    public Response<Void> removeFavorite(
            @PathVariable String favoriteId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        favoriteAppService.remove(userId, favoriteId);
        return Response.success();
    }

    @GetMapping("/list")
    @Operation(summary = "收藏列表", description = "获取当前用户的收藏列表")
    public Response<List<Favorite>> listFavorites(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(favoriteAppService.list(userId));
    }
}
