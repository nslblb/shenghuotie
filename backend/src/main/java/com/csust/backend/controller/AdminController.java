package com.csust.backend.controller;

import com.csust.backend.annotation.Log;
import com.csust.backend.common.Result;
import com.csust.backend.common.RateLimit;
import com.csust.backend.dto.response.AdminLoginLogVO;
import com.csust.backend.dto.response.AppealVO;
import com.csust.backend.dto.response.CacheStatusVO;
import com.csust.backend.dto.response.CommentAdminVO;
import com.csust.backend.dto.response.DashboardStatsVO;
import com.csust.backend.dto.response.DashboardVO;
import com.csust.backend.dto.response.HotTopicVO;
import com.csust.backend.dto.response.OperationLogVO;
import com.csust.backend.dto.response.ReportDetailVO;
import com.csust.backend.dto.response.ReportGroupVO;
import com.csust.backend.dto.response.ReportReasonStatsVO;
import com.csust.backend.dto.response.ReportVO;
import com.csust.backend.dto.response.SystemConfigVO;
import com.csust.backend.dto.response.TopicVO;
import com.csust.backend.dto.response.TrendDataVO;
import com.csust.backend.dto.response.UserDetailVO;
import com.csust.backend.dto.response.UserVO;
import com.csust.backend.entity.SensitiveWord;
import com.csust.backend.entity.Post;
import com.csust.backend.entity.OperationLog;
import com.csust.backend.repository.OperationLogRepository;
import com.csust.backend.service.AdminDashboardService;
import com.csust.backend.service.AdminPostService;
import com.csust.backend.service.AdminUserService;
import com.csust.backend.service.AppealService;
import com.csust.backend.service.ReportService;
import com.csust.backend.service.SensitiveWordService;
import com.csust.backend.service.SystemService;
import com.csust.backend.service.TopicService;
import com.csust.backend.common.XssUtil;
import com.csust.backend.security.AuthUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "后台管理", description = "管理员仪表盘、用户管理、笔记管理")
public class AdminController {

    private final AdminDashboardService adminDashboardService;
    private final AdminUserService adminUserService;
    private final AdminPostService adminPostService;
    private final ReportService reportService;
    private final AppealService appealService;
    private final SystemService systemService;
    private final TopicService topicService;
    private final SensitiveWordService sensitiveWordService;
    private final OperationLogRepository operationLogRepository;
    private final AuthUtil authUtil;

    // ==================== 仪表盘 ====================

    @Operation(summary = "获取仪表盘数据")
    @GetMapping("/dashboard")
    public Result<DashboardVO> getDashboard() {
        return Result.success(adminDashboardService.getDashboard());
    }

    @Operation(summary = "获取趋势数据")
    @GetMapping("/dashboard/trends")
    public Result<TrendDataVO> getTrends(@RequestParam(defaultValue = "7") int days) {
        return Result.success(adminDashboardService.getTrends(days));
    }

    @Operation(summary = "获取热门话题TOP10")
    @GetMapping("/dashboard/hot-topics")
    public Result<List<HotTopicVO>> getHotTopics() {
        return Result.success(adminDashboardService.getHotTopics());
    }

    @Operation(summary = "获取今日统计和待处理任务")
    @GetMapping("/dashboard/stats")
    public Result<DashboardStatsVO> getDashboardStats() {
        return Result.success(adminDashboardService.getDashboardStats());
    }

    // ==================== 用户管理 ====================

    @Operation(summary = "获取用户列表")
    @GetMapping("/users")
    public Result<Page<UserVO>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(adminUserService.getUsers(keyword, role, status, startTime, endTime, pageable));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/users/{id}")
    public Result<UserDetailVO> getUserDetail(@PathVariable Long id) {
        return Result.success(adminUserService.getUserDetail(id));
    }

    @Operation(summary = "修改用户状态")
    @Log("修改用户状态")
    @PutMapping("/users/{id}/status")
    @RateLimit(capacity = 30, refillCount = 30, duration = 1, timeUnit = TimeUnit.HOURS, type = RateLimit.RateLimitType.USER)
    public Result<Void> updateUserStatus(@PathVariable Long id,
                                         @RequestParam String status,
                                         @RequestParam(required = false) String banReason) {
        adminUserService.updateUserStatus(id, status, banReason);
        return Result.success();
    }

    @Operation(summary = "重置用户密码")
    @Log("重置用户密码")
    @PutMapping("/users/{id}/reset-password")
    @RateLimit(capacity = 10, refillCount = 10, duration = 1, timeUnit = TimeUnit.HOURS, type = RateLimit.RateLimitType.USER)
    public Result<Void> resetUserPassword(@PathVariable Long id) {
        adminUserService.resetUserPassword(id);
        return Result.success();
    }

    @Operation(summary = "修改用户角色")
    @Log("修改用户角色")
    @PutMapping("/users/{id}/role")
    public Result<Void> updateUserRole(@PathVariable Long id,
                                       @RequestParam String role) {
        adminUserService.updateUserRole(id, role);
        return Result.success();
    }

    @Operation(summary = "批量修改用户状态")
    @Log("批量修改用户状态")
    @PutMapping("/users/batch/status")
    public Result<Void> batchUpdateUserStatus(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) params.get("ids");
        String status = (String) params.get("status");
        String banReason = (String) params.get("banReason");
        adminUserService.batchUpdateUserStatus(ids, status, banReason);
        return Result.success();
    }

    // ==================== 帖子管理 ====================

    @Operation(summary = "获取笔记列表")
    @GetMapping("/posts")
    public Result<Page<Post>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String topic) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(adminPostService.getPosts(keyword, status, topic, pageable));
    }

    @Operation(summary = "获取笔记话题列表")
    @GetMapping("/posts/topics")
    public Result<List<String>> getPostTopics() {
        return Result.success(adminPostService.getPostTopics());
    }

    @Operation(summary = "获取笔记详情")
    @GetMapping("/posts/{id}")
    public Result<Post> getPostDetail(@PathVariable Long id) {
        return Result.success(adminPostService.getPostDetail(id));
    }

    @Operation(summary = "删除笔记")
    @Log("删除笔记")
    @DeleteMapping("/posts/{id}")
    @RateLimit(capacity = 30, refillCount = 30, duration = 1, timeUnit = TimeUnit.HOURS, type = RateLimit.RateLimitType.USER)
    public Result<Void> deletePost(@PathVariable Long id) {
        adminPostService.deletePost(id);
        return Result.success();
    }

    @Operation(summary = "批量删除笔记")
    @Log("批量删除笔记")
    @DeleteMapping("/posts/batch")
    public Result<Void> batchDeletePosts(@RequestBody List<Long> ids) {
        adminPostService.batchDeletePosts(ids);
        return Result.success();
    }

    @Operation(summary = "批量禁用笔记")
    @Log("批量禁用笔记")
    @PutMapping("/posts/batch/disable")
    public Result<Void> batchDisablePosts(@RequestBody List<Long> ids) {
        adminPostService.batchDisablePosts(ids);
        return Result.success();
    }

    @Operation(summary = "批量解禁笔记")
    @Log("批量解禁笔记")
    @PutMapping("/posts/batch/enable")
    public Result<Void> batchEnablePosts(@RequestBody List<Long> ids) {
        adminPostService.batchEnablePosts(ids);
        return Result.success();
    }

    @Operation(summary = "禁用笔记")
    @Log("禁用笔记")
    @PutMapping("/posts/{id}/disable")
    public Result<Void> disablePost(@PathVariable Long id) {
        adminPostService.disablePost(id);
        return Result.success();
    }

    @Operation(summary = "解禁笔记")
    @Log("解禁笔记")
    @PutMapping("/posts/{id}/enable")
    public Result<Void> enablePost(@PathVariable Long id) {
        adminPostService.enablePost(id);
        return Result.success();
    }

    // ==================== 操作日志 ====================

    @Operation(summary = "获取操作日志")
    @GetMapping("/logs")
    public Result<Page<OperationLog>> getOperationLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OperationLog> logs;
        if (keyword != null && !keyword.isBlank()) {
            logs = operationLogRepository.findByOperationContaining(keyword, pageable);
        } else {
            logs = operationLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return Result.success(logs);
    }

    // ==================== 申诉管理 ====================

    @Operation(summary = "获取申诉列表")
    @GetMapping("/appeals")
    public Result<Page<AppealVO>> getAppeals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(appealService.getAppeals(pageable, status));
    }

    @Operation(summary = "获取申诉详情")
    @GetMapping("/appeals/{id}")
    public Result<AppealVO> getAppealDetail(@PathVariable Long id) {
        return Result.success(appealService.getAppealDetail(id));
    }

    @Operation(summary = "批准申诉（解禁）")
    @Log("批准申诉")
    @PutMapping("/appeals/{id}/approve")
    public Result<Void> approveAppeal(
            @PathVariable Long id,
            @RequestParam(required = false) String handleNote) {
        appealService.handleAppeal(id, authUtil.getCurrentUserId(), true, handleNote);
        return Result.success();
    }

    @Operation(summary = "驳回申诉")
    @Log("驳回申诉")
    @PutMapping("/appeals/{id}/reject")
    public Result<Void> rejectAppeal(
            @PathVariable Long id,
            @RequestParam(required = false) String handleNote) {
        appealService.handleAppeal(id, authUtil.getCurrentUserId(), false, handleNote);
        return Result.success();
    }

    @Operation(summary = "获取笔记申诉历史")
    @GetMapping("/appeals/post/{postId}/history")
    public Result<Page<AppealVO>> getAppealHistory(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(appealService.getAppealHistory(postId, pageable));
    }

    @Operation(summary = "删除申诉记录")
    @Log("删除申诉记录")
    @DeleteMapping("/appeals/{id}")
    public Result<Void> deleteAppeal(@PathVariable Long id) {
        appealService.deleteAppeal(id);
        return Result.success();
    }

    // ==================== 评论管理 ====================

    @Operation(summary = "获取评论列表")
    @GetMapping("/comments")
    public Result<Page<CommentAdminVO>> getComments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) Boolean hasSensitiveWord) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(adminPostService.getComments(keyword, userId, postId, hasSensitiveWord, pageable));
    }

    @Operation(summary = "获取评论详情")
    @GetMapping("/comments/{id}")
    public Result<CommentAdminVO> getCommentDetail(@PathVariable Long id) {
        return Result.success(adminPostService.getCommentDetail(id));
    }

    @Operation(summary = "删除评论")
    @Log("删除评论")
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        adminPostService.deleteComment(id);
        return Result.success();
    }

    // ==================== 举报管理 ====================

    @Operation(summary = "获取举报列表")
    @GetMapping("/reports")
    public Result<Page<ReportVO>> getReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(reportService.getReports(pageable, status));
    }

    @Operation(summary = "获取举报详情")
    @GetMapping("/reports/{id}")
    public Result<ReportDetailVO> getReportDetail(@PathVariable Long id) {
        return Result.success(reportService.getReportDetail(id));
    }

    @Operation(summary = "处理举报（支持一键处理）")
    @Log("处理举报")
    @PutMapping("/reports/{id}/handle")
    @RateLimit(capacity = 30, refillCount = 30, duration = 1, timeUnit = TimeUnit.HOURS, type = RateLimit.RateLimitType.USER)
    public Result<Void> handleReport(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String handleResult,
            @RequestParam(required = false) String action) {
        Long handlerId = authUtil.getCurrentUserId();
        if (handlerId == null) handlerId = 0L;
        reportService.handleReportWithAction(id, handlerId, status, handleResult, action);
        return Result.success();
    }

    @Operation(summary = "删除举报记录")
    @Log("删除举报记录")
    @DeleteMapping("/reports/{id}")
    public Result<Void> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);
        return Result.success();
    }

    @Operation(summary = "获取合并举报列表")
    @GetMapping("/reports/grouped")
    public Result<Page<ReportGroupVO>> getGroupedReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType) {
        PageRequest pageable = PageRequest.of(page, size);
        return Result.success(reportService.getGroupedReports(pageable, status, targetType));
    }

    @Operation(summary = "获取举报统计")
    @GetMapping("/reports/stats")
    public Result<ReportReasonStatsVO.StatsResponse> getReportStats() {
        return Result.success(reportService.getReportStats());
    }

    @Operation(summary = "获取自动处理规则")
    @GetMapping("/reports/rules")
    public Result<List<ReportService.AutoHandleRuleVO>> getAutoHandleRules() {
        return Result.success(reportService.getAutoHandleRules());
    }

    @Operation(summary = "创建自动处理规则")
    @Log("创建自动处理规则")
    @PostMapping("/reports/rules")
    public Result<ReportService.AutoHandleRuleVO> createAutoHandleRule(
            @RequestBody ReportService.CreateAutoHandleRuleRequest request) {
        return Result.success(reportService.createAutoHandleRule(request));
    }

    @Operation(summary = "更新自动处理规则")
    @Log("更新自动处理规则")
    @PutMapping("/reports/rules/{id}")
    public Result<ReportService.AutoHandleRuleVO> updateAutoHandleRule(
            @PathVariable Long id,
            @RequestBody ReportService.CreateAutoHandleRuleRequest request) {
        return Result.success(reportService.updateAutoHandleRule(id, request));
    }

    @Operation(summary = "删除自动处理规则")
    @Log("删除自动处理规则")
    @DeleteMapping("/reports/rules/{id}")
    public Result<Void> deleteAutoHandleRule(@PathVariable Long id) {
        reportService.deleteAutoHandleRule(id);
        return Result.success();
    }

    @Operation(summary = "手动触发自动处理规则")
    @Log("手动触发自动处理规则")
    @PostMapping("/reports/rules/execute")
    public Result<Void> executeAutoHandleRules() {
        reportService.processAutoHandleRules();
        return Result.success();
    }

    // ==================== 系统管理 ====================

    @Operation(summary = "获取管理员登录日志")
    @GetMapping("/logs/login")
    public Result<Page<AdminLoginLogVO>> getAdminLoginLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        LocalDateTime start = startTime != null ? LocalDateTime.parse(startTime) : null;
        LocalDateTime end = endTime != null ? LocalDateTime.parse(endTime) : null;
        return Result.success(systemService.getAdminLoginLogs(pageable, userId, status, start, end));
    }

    @Operation(summary = "获取操作日志（增强）")
    @GetMapping("/logs/operations")
    public Result<Page<OperationLogVO>> getOperationLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        LocalDateTime start = startTime != null ? LocalDateTime.parse(startTime) : null;
        LocalDateTime end = endTime != null ? LocalDateTime.parse(endTime) : null;
        return Result.success(systemService.getOperationLogs(pageable, userId, operation, status, start, end));
    }

    @Operation(summary = "获取操作日志统计")
    @GetMapping("/logs/operations/stats")
    public Result<OperationLogVO.Stats> getOperationLogStats() {
        return Result.success(systemService.getOperationLogStats());
    }

    @Operation(summary = "获取系统配置")
    @GetMapping("/configs")
    public Result<SystemConfigVO.AllConfigs> getSystemConfigs() {
        return Result.success(systemService.getAllConfigs());
    }

    @Operation(summary = "更新系统配置")
    @Log("更新系统配置")
    @PutMapping("/configs")
    public Result<Void> updateSystemConfigs(@RequestBody Map<String, String> configs) {
        systemService.updateConfigs(configs, 0L);
        return Result.success();
    }

    @Operation(summary = "更新单个配置")
    @Log("更新系统配置")
    @PutMapping("/configs/{key}")
    public Result<Void> updateSystemConfig(@PathVariable String key, @RequestBody Map<String, String> body) {
        systemService.updateConfig(key, body.get("value"), 0L);
        return Result.success();
    }

    @Operation(summary = "获取缓存状态")
    @GetMapping("/cache/status")
    public Result<CacheStatusVO> getCacheStatus() {
        return Result.success(systemService.getCacheStatus());
    }

    @Operation(summary = "清除缓存")
    @Log("清除缓存")
    @DeleteMapping("/cache")
    public Result<Void> clearCache(@RequestParam(required = false) String pattern) {
        if (pattern != null && !pattern.isBlank()) {
            systemService.clearCache(pattern);
        } else {
            systemService.clearAllCache();
        }
        return Result.success();
    }

    // ==================== 话题管理 ====================

    @Operation(summary = "获取所有话题")
    @GetMapping("/topics")
    public Result<List<TopicVO>> getAllTopics() {
        return Result.success(topicService.getAllTopics());
    }

    @Operation(summary = "获取启用的话题")
    @GetMapping("/topics/enabled")
    public Result<List<TopicVO>> getEnabledTopics() {
        return Result.success(topicService.getEnabledTopics());
    }

    @Operation(summary = "分页获取话题")
    @GetMapping("/topics/page")
    public Result<Page<TopicVO>> getTopicsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean enabled) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("sortOrder").ascending());
        return Result.success(topicService.getTopics(pageable, enabled));
    }

    @Operation(summary = "获取话题详情")
    @GetMapping("/topics/{id}")
    public Result<TopicVO> getTopic(@PathVariable Long id) {
        return Result.success(topicService.getTopic(id));
    }

    @Operation(summary = "创建话题")
    @Log("创建话题")
    @PostMapping("/topics")
    public Result<TopicVO> createTopic(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Integer sortOrder) {
        return Result.success(topicService.createTopic(XssUtil.clean(name), XssUtil.clean(description), sortOrder));
    }

    @Operation(summary = "更新话题")
    @Log("更新话题")
    @PutMapping("/topics/{id}")
    public Result<TopicVO> updateTopic(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Integer sortOrder,
            @RequestParam(required = false) Boolean enabled) {
        return Result.success(topicService.updateTopic(id, XssUtil.clean(name), XssUtil.clean(description), sortOrder, enabled));
    }

    @Operation(summary = "删除话题")
    @Log("删除话题")
    @DeleteMapping("/topics/{id}")
    public Result<Void> deleteTopic(@PathVariable Long id) {
        topicService.deleteTopic(id);
        return Result.success();
    }

    // ==================== 敏感词管理 ====================

    @Operation(summary = "获取敏感词列表")
    @GetMapping("/sensitive-words")
    public Result<Page<SensitiveWord>> getSensitiveWords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return Result.success(sensitiveWordService.getSensitiveWords(keyword, category, enabled, pageable));
    }

    @Operation(summary = "创建敏感词")
    @Log("创建敏感词")
    @PostMapping("/sensitive-words")
    public Result<SensitiveWord> createSensitiveWord(
            @RequestParam String word,
            @RequestParam(required = false) String category) {
        return Result.success(sensitiveWordService.createSensitiveWord(XssUtil.clean(word), category));
    }

    @Operation(summary = "更新敏感词")
    @Log("更新敏感词")
    @PutMapping("/sensitive-words/{id}")
    public Result<SensitiveWord> updateSensitiveWord(
            @PathVariable Long id,
            @RequestParam(required = false) String word,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled) {
        return Result.success(sensitiveWordService.updateSensitiveWord(id, XssUtil.clean(word), category, enabled));
    }

    @Operation(summary = "删除敏感词")
    @Log("删除敏感词")
    @DeleteMapping("/sensitive-words/{id}")
    public Result<Void> deleteSensitiveWord(@PathVariable Long id) {
        sensitiveWordService.deleteSensitiveWord(id);
        return Result.success();
    }

    @Operation(summary = "批量删除敏感词")
    @Log("批量删除敏感词")
    @DeleteMapping("/sensitive-words/batch")
    public Result<Void> batchDeleteSensitiveWords(@RequestBody List<Long> ids) {
        sensitiveWordService.batchDeleteSensitiveWords(ids);
        return Result.success();
    }

    @Operation(summary = "刷新敏感词缓存")
    @Log("刷新敏感词缓存")
    @PostMapping("/sensitive-words/refresh")
    public Result<Void> refreshSensitiveWordCache() {
        sensitiveWordService.refreshCache();
        return Result.success();
    }
}
