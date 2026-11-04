package com.csust.backend.controller;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.common.RateLimit;
import com.csust.backend.common.Result;
import com.csust.backend.common.XssUtil;
import com.csust.backend.dto.request.CreateCommentRequest;
import com.csust.backend.dto.response.CommentVO;
import com.csust.backend.dto.response.SystemConfigVO;
import com.csust.backend.security.AuthUtil;
import com.csust.backend.service.CommentService;
import com.csust.backend.service.SensitiveWordService;
import com.csust.backend.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
@Tag(name = "评论管理", description = "评论发布和查询")
public class CommentController {

    private final CommentService commentService;
    private final AuthUtil authUtil;
    private final SystemService systemService;
    private final SensitiveWordService sensitiveWordService;

    @Operation(summary = "发表评论")
    @PostMapping
    @RateLimit(capacity = 10, refillCount = 10, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<CommentVO> createComment(@PathVariable Long postId,
                                           @Valid @RequestBody CreateCommentRequest request) {
        SystemConfigVO.AllConfigs configs = systemService.getAllConfigs();
        if (!Boolean.TRUE.equals(configs.getEnableComment())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "系统暂不允许发表评论");
        }
        Long userId = authUtil.getCurrentUserId();
        request.setContent(XssUtil.clean(request.getContent()));
        return Result.success(commentService.createComment(postId, userId, request));
    }

    @Operation(summary = "获取评论列表")
    @GetMapping
    public Result<Map<String, Object>> getComments(@PathVariable Long postId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<CommentVO> result = commentService.getPostComments(postId, pageable);
        // 保持与原 API 返回结构一致：{ comments, totalPages, totalElements, currentPage }
        return Result.success(Map.of(
                "comments", result.getContent(),
                "totalPages", result.getTotalPages(),
                "totalElements", result.getTotalElements(),
                "currentPage", result.getNumber()
        ));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long postId, @PathVariable Long commentId) {
        Long userId = authUtil.getCurrentUserId();
        commentService.deleteComment(commentId, userId);
        return Result.success();
    }
}
