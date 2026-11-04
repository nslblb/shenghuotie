package com.csust.backend.controller;

import com.csust.backend.common.RateLimit;
import com.csust.backend.common.Result;
import com.csust.backend.security.AuthUtil;
import com.csust.backend.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/favorite")
@RequiredArgsConstructor
@Tag(name = "收藏管理", description = "收藏和取消收藏")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final AuthUtil authUtil;

    @Operation(summary = "收藏笔记")
    @PostMapping
    @RateLimit(capacity = 30, refillCount = 30, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<Void> favorite(@PathVariable Long postId) {
        Long userId = authUtil.getCurrentUserId();
        favoriteService.favorite(userId, postId);
        return Result.success();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping
    @RateLimit(capacity = 30, refillCount = 30, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<Void> unfavorite(@PathVariable Long postId) {
        Long userId = authUtil.getCurrentUserId();
        favoriteService.unfavorite(userId, postId);
        return Result.success();
    }
}
