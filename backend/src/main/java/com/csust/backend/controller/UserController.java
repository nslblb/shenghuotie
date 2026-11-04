package com.csust.backend.controller;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.common.RateLimit;
import com.csust.backend.common.Result;
import com.csust.backend.dto.request.ChangePasswordRequest;
import com.csust.backend.dto.request.DeleteAccountRequest;
import com.csust.backend.dto.request.ResetPasswordRequest;
import com.csust.backend.dto.request.UpdateUserRequest;
import com.csust.backend.dto.response.UserProfileVO;
import com.csust.backend.dto.response.UserVO;
import com.csust.backend.security.AuthUtil;
import com.csust.backend.service.FollowService;
import com.csust.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户资料、关注/取关、粉丝列表")
public class UserController {

    private final UserService userService;
    private final FollowService followService;
    private final AuthUtil authUtil;

    @Operation(summary = "获取用户资料")
    @GetMapping("/{id}")
    public Result<UserProfileVO> getUserProfile(@PathVariable Long id) {
        Long currentUserId = authUtil.getCurrentUserId();
        return Result.success(userService.getUserProfile(id, currentUserId));
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/{id}")
    public Result<UserVO> updateUser(@PathVariable Long id,
                                     @Valid @RequestBody UpdateUserRequest request) {
        Long currentUserId = authUtil.getCurrentUserId();
        if (!currentUserId.equals(id)) {
            return Result.fail(403, "无权修改他人信息");
        }
        return Result.success(userService.updateUserInfo(id, request));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/{id}/password")
    @RateLimit(capacity = 5, refillCount = 5, duration = 1, timeUnit = TimeUnit.HOURS, type = RateLimit.RateLimitType.USER)
    public Result<Void> changePassword(@PathVariable Long id,
                                         @Valid @RequestBody ChangePasswordRequest request) {
        Long currentUserId = authUtil.getCurrentUserId();
        if (!currentUserId.equals(id)) {
            return Result.fail(403, "无权修改他人密码");
        }
        userService.changePassword(id, request.getOldPassword(), request.getNewPassword());
        return Result.success();
    }

    @Operation(summary = "关注用户")
    @PostMapping("/{id}/follow")
    @RateLimit(capacity = 20, refillCount = 20, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<Void> follow(@PathVariable Long id) {
        Long currentUserId = authUtil.getCurrentUserId();
        followService.follow(currentUserId, id);
        return Result.success();
    }

    @Operation(summary = "取消关注")
    @DeleteMapping("/{id}/follow")
    @RateLimit(capacity = 20, refillCount = 20, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<Void> unfollow(@PathVariable Long id) {
        Long currentUserId = authUtil.getCurrentUserId();
        followService.unfollow(currentUserId, id);
        return Result.success();
    }

    @Operation(summary = "获取粉丝列表")
    @GetMapping("/{id}/followers")
    public Result<Page<UserVO>> getFollowers(@PathVariable Long id,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return Result.success(followService.getFollowers(id, pageable));
    }

    @Operation(summary = "获取关注列表")
    @GetMapping("/{id}/followings")
    public Result<Page<UserVO>> getFollowings(@PathVariable Long id,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return Result.success(followService.getFollowings(id, pageable));
    }

    @Operation(summary = "搜索用户")
    @GetMapping("/search")
    public Result<Page<UserVO>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(userService.searchUsers(keyword, pageable));
    }

    @Operation(summary = "发送重置密码验证码")
    @PostMapping("/reset-password/code")
    @RateLimit(capacity = 1, refillCount = 1, duration = 1, type = RateLimit.RateLimitType.IP)
    public Result<Void> sendResetCode(@RequestParam String phone) {
        userService.sendResetCode(phone);
        return Result.success();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.getPhone(), request.getCode(), request.getNewPassword());
        return Result.success();
    }

    @Operation(summary = "注销账号")
    @DeleteMapping("/account")
    public Result<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        Long currentUserId = authUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        userService.deleteAccount(currentUserId, request.getCode());
        return Result.success();
    }

    @Operation(summary = "发送注销验证码")
    @PostMapping("/delete-account/send-code")
    @RateLimit(capacity = 1, refillCount = 1, duration = 1, type = RateLimit.RateLimitType.USER)
    public Result<Void> sendDeleteCode() {
        Long currentUserId = authUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        userService.sendDeleteCode(currentUserId);
        return Result.success();
    }
}
