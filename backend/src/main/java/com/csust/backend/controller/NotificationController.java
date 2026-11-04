package com.csust.backend.controller;

import com.csust.backend.common.Result;
import com.csust.backend.dto.response.NotificationVO;
import com.csust.backend.security.AuthUtil;
import com.csust.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "通知管理", description = "消息通知查询和已读标记")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthUtil authUtil;

    @Operation(summary = "获取通知列表")
    @GetMapping
    public Result<Page<NotificationVO>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type) {
        Long userId = authUtil.getCurrentUserId();
        return Result.success(notificationService.getUserNotifications(userId, type, PageRequest.of(page, size)));
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        Long userId = authUtil.getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return Result.success();
    }

    @Operation(summary = "单条标记已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = authUtil.getCurrentUserId();
        notificationService.markAsRead(id, userId);
        return Result.success();
    }

    @Operation(summary = "获取未读通知数")
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        Long userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return Result.success(0L);
        }
        return Result.success(notificationService.getUnreadCount(userId));
    }

    @Operation(summary = "获取各类型未读通知数")
    @GetMapping("/unread-count-by-type")
    public Result<Map<String, Long>> getUnreadCountByType() {
        Long userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return Result.success(Map.of());
        }
        return Result.success(notificationService.getUnreadCountByType(userId));
    }

    @Operation(summary = "批量删除通知")
    @DeleteMapping("/batch")
    public Result<Void> deleteNotifications(@RequestBody Map<String, List<Long>> body) {
        Long userId = authUtil.getCurrentUserId();
        List<Long> ids = body.get("ids");
        notificationService.deleteNotifications(ids, userId);
        return Result.success();
    }

    @Operation(summary = "清空所有通知")
    @DeleteMapping("/all")
    public Result<Void> deleteAllNotifications() {
        Long userId = authUtil.getCurrentUserId();
        notificationService.deleteAllNotifications(userId);
        return Result.success();
    }
}
