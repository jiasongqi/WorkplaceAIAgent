package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.auth.AuthService;
import com.yupi.yuaiagent.common.Response;
import com.yupi.yuaiagent.dto.AddFavoriteRequest;
import com.yupi.yuaiagent.favorite.Favorite;
import com.yupi.yuaiagent.service.FavoriteAppService;
import jakarta.annotation.Resource;
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
public class FavoriteController {

    @Resource
    private FavoriteAppService favoriteAppService;

    @Resource
    private AuthService authService;

    @PostMapping
    public Response<Favorite> addFavorite(
            @RequestBody AddFavoriteRequest request,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(favoriteAppService.add(userId, request));
    }

    @DeleteMapping("/{favoriteId}")
    public Response<Void> removeFavorite(
            @PathVariable String favoriteId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        favoriteAppService.remove(userId, favoriteId);
        return Response.success();
    }

    @GetMapping("/list")
    public Response<List<Favorite>> listFavorites(
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = authService.authenticate(token, authHeader);
        return Response.success(favoriteAppService.list(userId));
    }
}
