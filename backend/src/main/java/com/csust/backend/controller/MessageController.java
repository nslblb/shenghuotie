package com.csust.backend.controller;

import com.csust.backend.common.RateLimit;
import com.csust.backend.common.Result;
import com.csust.backend.dto.request.SendMessageRequest;
import com.csust.backend.dto.response.ConversationVO;
import com.csust.backend.dto.response.MessageVO;
import com.csust.backend.security.AuthUtil;
import com.csust.backend.service.MessageService;
import com.csust.backend.service.OnlineStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Tag(name = "私信管理", description = "用户间一对一私信")
public class MessageController {

    private final MessageService messageService;
    private final OnlineStatusService onlineStatusService;
    private final AuthUtil authUtil;

    @Operation(summary = "发送私信")
    @PostMapping
    @RateLimit(capacity = 3, refillCount = 3, duration = 1, timeUnit = TimeUnit.MINUTES, type = RateLimit.RateLimitType.USER)
    public Result<MessageVO> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        Long userId = authUtil.getCurrentUserId();
        return Result.success(messageService.sendMessage(userId, request.getReceiverId(), request.getContent(), request.getMessageType()));
    }

    @Operation(summary = "获取与某用户的聊天记录")
    @GetMapping("/conversation/{partnerId}")
    public Result<Page<MessageVO>> getConversation(
            @PathVariable Long partnerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = authUtil.getCurrentUserId();
        return Result.success(messageService.getConversation(userId, partnerId, page, size));
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> getConversations() {
        Long userId = authUtil.getCurrentUserId();
        return Result.success(messageService.getConversations(userId));
    }

    @Operation(summary = "标记会话已读")
    @PutMapping("/conversation/{partnerId}/read")
    public Result<Void> markConversationRead(@PathVariable Long partnerId) {
        Long userId = authUtil.getCurrentUserId();
        messageService.markConversationRead(userId, partnerId);
        return Result.success();
    }

    @Operation(summary = "获取未读私信数")
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        Long userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return Result.success(0L);
        }
        return Result.success(messageService.getUnreadCount(userId));
    }

    @Operation(summary = "查询用户在线状态")
    @GetMapping("/online/{userId}")
    public Result<Boolean> isOnline(@PathVariable Long userId) {
        return Result.success(onlineStatusService.isOnline(userId));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/conversation/{partnerId}")
    public Result<Void> deleteConversation(@PathVariable Long partnerId) {
        Long userId = authUtil.getCurrentUserId();
        messageService.deleteConversation(userId, partnerId);
        return Result.success();
    }

    @Operation(summary = "批量删除消息")
    @DeleteMapping("/batch")
    public Result<Void> deleteMessages(@RequestBody Map<String, List<Long>> body) {
        Long userId = authUtil.getCurrentUserId();
        List<Long> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return Result.fail(400, "请选择要删除的消息");
        }
        messageService.deleteMessages(ids, userId);
        return Result.success();
    }

    @Operation(summary = "撤回消息")
    @PutMapping("/{id}/recall")
    public Result<Void> recallMessage(@PathVariable Long id) {
        Long userId = authUtil.getCurrentUserId();
        messageService.recallMessage(id, userId);
        return Result.success();
    }
}
