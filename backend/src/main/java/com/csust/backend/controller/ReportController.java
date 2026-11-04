package com.csust.backend.controller;

import com.csust.backend.common.RateLimit;
import com.csust.backend.common.Result;
import com.csust.backend.security.AuthUtil;
import com.csust.backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "举报", description = "举报违规内容")
public class ReportController {

    private final ReportService reportService;
    private final AuthUtil authUtil;

    @Operation(summary = "提交举报")
    @PostMapping
    @RateLimit(capacity = 10, refillCount = 10, duration = 1, timeUnit = TimeUnit.HOURS, type = RateLimit.RateLimitType.USER)
    public Result<Void> createReport(@RequestBody CreateReportRequest request) {
        Long userId = authUtil.getCurrentUserId();
        if (userId == null) return Result.fail(401, "请先登录");
        reportService.createReport(userId, request.targetId(), request.targetType(), request.reason());
        return Result.success();
    }

    public record CreateReportRequest(Long targetId, String targetType, String reason) {}
}
