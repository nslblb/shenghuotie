package com.csust.backend.controller;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.common.RateLimit;
import com.csust.backend.common.Result;
import com.csust.backend.common.XssUtil;
import com.csust.backend.dto.request.CreatePostRequest;
import com.csust.backend.dto.response.PostDetailVO;
import com.csust.backend.dto.response.PostVO;
import com.csust.backend.dto.response.SystemConfigVO;
import com.csust.backend.dto.response.TopicVO;
import com.csust.backend.security.AuthUtil;
import com.csust.backend.service.PostService;
import com.csust.backend.service.SensitiveWordService;
import com.csust.backend.service.SystemService;
import com.csust.backend.service.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "笔记管理", description = "笔记发布、编辑、草稿、查询、搜索、删除")
public class PostController {

    private final PostService postService;
    private final AuthUtil authUtil;
    private final SystemService systemService;
    private final TopicService topicService;
    private final SensitiveWordService sensitiveWordService;

    @Operation(summary = "获取话题列表")
    @GetMapping("/topics")
    public Result<java.util.List<TopicVO>> getTopics() {
        return Result.success(topicService.getEnabledTopics());
    }

    @Operation(summary = "发布笔记")
    @PostMapping
    @RateLimit(capacity = 10, refillCount = 10, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<PostDetailVO> createPost(@Valid @RequestBody CreatePostRequest request) {
        SystemConfigVO.AllConfigs configs = systemService.getAllConfigs();
        if (!Boolean.TRUE.equals(configs.getEnablePost())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "系统暂不允许发布笔记");
        }
        Long userId = authUtil.getCurrentUserId();
        request.setTitle(XssUtil.clean(request.getTitle()));
        request.setContent(XssUtil.clean(request.getContent()));
        return Result.success(postService.createPost(userId, request));
    }

    @Operation(summary = "编辑笔记")
    @PutMapping("/{id}")
    @RateLimit(capacity = 10, refillCount = 10, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<PostDetailVO> updatePost(@PathVariable Long id, @Valid @RequestBody CreatePostRequest request) {
        Long userId = authUtil.getCurrentUserId();
        request.setTitle(XssUtil.clean(request.getTitle()));
        request.setContent(XssUtil.clean(request.getContent()));
        return Result.success(postService.updatePost(userId, id, request));
    }

    @Operation(summary = "获取笔记详情")
    @GetMapping("/{id}")
    public Result<PostDetailVO> getPost(@PathVariable Long id) {
        Long currentUserId = authUtil.getCurrentUserId();
        return Result.success(postService.getPostById(id, currentUserId));
    }

    @Operation(summary = "获取笔记列表")
    @GetMapping
    public Result<Page<PostVO>> getPostList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String sort) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Long currentUserId = authUtil.getCurrentUserId();
        return Result.success(postService.getPostList(pageable, topic, sort, currentUserId));
    }

    @Operation(summary = "获取用户笔记")
    @GetMapping("/user/{userId}")
    public Result<Page<PostVO>> getUserPosts(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Long currentUserId = authUtil.getCurrentUserId();
        return Result.success(postService.getUserPosts(userId, pageable, currentUserId));
    }

    @Operation(summary = "删除笔记")
    @DeleteMapping("/{id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        Long userId = authUtil.getCurrentUserId();
        postService.deletePost(id, userId);
        return Result.success();
    }

    @Operation(summary = "批量删除笔记")
    @DeleteMapping("/batch")
    public Result<Void> batchDeletePosts(@RequestBody List<Long> postIds) {
        Long userId = authUtil.getCurrentUserId();
        postService.batchDeletePosts(postIds, userId);
        return Result.success();
    }

    @Operation(summary = "请求解封笔记")
    @PutMapping("/{id}/appeal")
    public Result<Void> appealPost(
            @PathVariable Long id,
            @RequestBody(required = false) AppealRequest request) {
        Long userId = authUtil.getCurrentUserId();
        String reason = request != null ? request.reason() : "";
        postService.appealPost(id, userId, reason);
        return Result.success();
    }

    public record AppealRequest(String reason) {}

    @Operation(summary = "搜索笔记")
    @GetMapping("/search")
    public Result<Page<PostVO>> searchPosts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(postService.searchPosts(keyword, pageable));
    }

    @Operation(summary = "保存草稿")
    @PostMapping("/draft")
    @RateLimit(capacity = 20, refillCount = 20, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<PostDetailVO> saveDraft(@RequestBody CreatePostRequest request) {
        Long userId = authUtil.getCurrentUserId();
        if (request.getTitle() != null) {
            request.setTitle(XssUtil.clean(request.getTitle()));
        }
        if (request.getContent() != null) {
            request.setContent(XssUtil.clean(request.getContent()));
        }
        return Result.success(postService.saveDraft(userId, request));
    }

    @Operation(summary = "更新草稿")
    @PutMapping("/draft/{id}")
    @RateLimit(capacity = 20, refillCount = 20, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<PostDetailVO> updateDraft(@PathVariable Long id, @RequestBody CreatePostRequest request) {
        Long userId = authUtil.getCurrentUserId();
        if (request.getTitle() != null) {
            request.setTitle(XssUtil.clean(request.getTitle()));
        }
        if (request.getContent() != null) {
            request.setContent(XssUtil.clean(request.getContent()));
        }
        return Result.success(postService.updateDraft(userId, id, request));
    }

    @Operation(summary = "获取草稿列表")
    @GetMapping("/drafts")
    public Result<Page<PostVO>> getUserDrafts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = authUtil.getCurrentUserId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        return Result.success(postService.getUserDrafts(userId, pageable));
    }

    @Operation(summary = "获取用户收藏列表")
    @GetMapping("/favorites")
    public Result<Page<PostVO>> getUserFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = authUtil.getCurrentUserId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(postService.getUserFavorites(userId, pageable));
    }

    @Operation(summary = "获取用户点赞列表")
    @GetMapping("/likes")
    public Result<Page<PostVO>> getUserLikes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = authUtil.getCurrentUserId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(postService.getUserLikes(userId, pageable));
    }
}
