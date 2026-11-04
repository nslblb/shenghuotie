package com.csust.backend.controller;

import com.csust.backend.common.RateLimit;
import com.csust.backend.common.Result;
import com.csust.backend.security.AuthUtil;
import com.csust.backend.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/like")
@RequiredArgsConstructor
@Tag(name = "点赞管理", description = "点赞和取消点赞")
public class LikeController {

    private final LikeService likeService;
    private final AuthUtil authUtil;

    @Operation(summary = "点赞")
    @PostMapping
    @RateLimit(capacity = 30, refillCount = 30, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<Void> like(@PathVariable Long postId) {
        Long userId = authUtil.getCurrentUserId();
        likeService.like(userId, postId);
        return Result.success();
    }

    @Operation(summary = "取消点赞")
    @DeleteMapping
    @RateLimit(capacity = 30, refillCount = 30, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<Void> unlike(@PathVariable Long postId) {
        Long userId = authUtil.getCurrentUserId();
        likeService.unlike(userId, postId);
        return Result.success();
    }
}
