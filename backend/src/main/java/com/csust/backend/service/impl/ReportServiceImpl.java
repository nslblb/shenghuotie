package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.common.XssUtil;
import com.csust.backend.dto.response.ReportDetailVO;
import com.csust.backend.dto.response.ReportGroupVO;
import com.csust.backend.dto.response.ReportReasonStatsVO;
import com.csust.backend.dto.response.ReportVO;
import com.csust.backend.entity.*;
import com.csust.backend.repository.*;
import com.csust.backend.service.AntiSpamService;
import com.csust.backend.service.RedisCacheService;
import com.csust.backend.service.NotificationService;
import com.csust.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final AutoHandleRuleRepository autoHandleRuleRepository;
    private final RedisCacheService redisCacheService;
    private final NotificationService notificationService;
    private final AntiSpamService antiSpamService;

    @Override
    @Transactional
    public void createReport(Long reporterId, Long targetId, String targetType, String reason) {
        // 举报防护：虚假举报惩罚
        antiSpamService.checkFalseReportLimit(reporterId);

        // 数据库检查：同一用户对同一内容有未处理的举报时不允许重复提交
        if (reportRepository.existsByReporterIdAndTargetIdAndTargetTypeAndStatus(reporterId, targetId, targetType, "PENDING")) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetId(targetId);
        report.setTargetType(targetType);
        report.setReason(XssUtil.clean(reason));
        report.setStatus("PENDING");
        reportRepository.save(report);
    }

    @Override
    public Page<ReportVO> getReports(Pageable pageable, String status) {
        Page<Report> reports;
        if (status != null && !status.isBlank()) {
            reports = reportRepository.findByStatus(status, pageable);
        } else {
            reports = reportRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return reports.map(this::toVO);
    }

    @Override
    @Transactional
    public void handleReport(Long reportId, Long handlerId, String status, String handleResult) {
        handleReportWithAction(reportId, handlerId, status, handleResult, null);
    }

    @Override
    @Transactional
    public void handleReportWithAction(Long reportId, Long handlerId, String status, String handleResult, String action) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        if (!"PENDING".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_HANDLED);
        }

        String statusText = "RESOLVED".equals(status) ? "举报成立" : "举报被驳回";

        report.setStatus(status);
        report.setHandlerId(handlerId);
        report.setHandleResult(handleResult != null && !handleResult.isBlank() ? handleResult : statusText);
        report.setHandledAt(LocalDateTime.now());
        reportRepository.save(report);

        String targetTitle = getTargetTitle(report.getTargetType(), report.getTargetId());

        notificationService.createNotification(
                report.getReporterId(), 0L, "REPORT", report.getTargetId(),
                "你举报的「" + targetTitle + "」已处理：" + statusText
                        + (handleResult != null && !handleResult.isBlank() ? "，原因：" + handleResult : "")
        );

        if ("RESOLVED".equals(status)) {
            executeAction(report.getTargetType(), report.getTargetId(), action, targetTitle, report.getReason());
        }

        // 举报被驳回时记录虚假举报
        if ("REJECTED".equals(status)) {
            antiSpamService.recordFalseReport(report.getReporterId());
        }
    }

    private void executeAction(String targetType, Long targetId, String action, String targetTitle, String reportReason) {
        final String finalAction = action != null ? action : "DISABLE";

        if ("POST".equals(targetType)) {
            postRepository.findById(targetId).ifPresent(post -> {
                if ("DELETE".equals(finalAction)) {
                    postRepository.delete(post);
                    redisCacheService.evict("shenghuotie:cache:post:detail:" + post.getId());
                    redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
                    notificationService.createNotification(
                            post.getUserId(), 0L, "REPORT", post.getId(),
                            "你的笔记「" + post.getTitle() + "」因违规已被删除"
                                    + (reportReason != null && !reportReason.isBlank() ? "，原因：" + reportReason : "")
                    );
                } else {
                    post.setStatus("DISABLED");
                    postRepository.save(post);
                    redisCacheService.evict("shenghuotie:cache:post:detail:" + post.getId());
                    redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
                    notificationService.createNotification(
                            post.getUserId(), 0L, "REPORT", post.getId(),
                            "你的笔记「" + post.getTitle() + "」因违规已被封禁"
                                    + (reportReason != null && !reportReason.isBlank() ? "，原因：" + reportReason : "")
                    );
                }
            });
        } else if ("COMMENT".equals(targetType)) {
            if ("DELETE".equals(finalAction)) {
                commentRepository.findById(targetId).ifPresent(comment -> {
                    deleteCommentRecursively(comment.getId());
                });
            }
        }
    }

    private void deleteCommentRecursively(Long commentId) {
        List<Comment> childComments = commentRepository.findAllByParentId(commentId);
        for (Comment child : childComments) {
            deleteCommentRecursively(child.getId());
        }
        commentRepository.deleteById(commentId);
    }

    @Override
    public ReportDetailVO getReportDetail(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        ReportDetailVO vo = new ReportDetailVO();
        vo.setId(report.getId());
        vo.setReporterId(report.getReporterId());
        vo.setTargetId(report.getTargetId());
        vo.setTargetType(report.getTargetType());
        vo.setReason(report.getReason());
        vo.setStatus(report.getStatus());
        vo.setHandlerId(report.getHandlerId());
        vo.setHandleResult(report.getHandleResult());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setHandledAt(report.getHandledAt());

        userRepository.findById(report.getReporterId()).ifPresent(user -> {
            vo.setReporterName(user.getNickname());
            vo.setReporterAvatar(user.getAvatarUrl());
        });

        if (report.getHandlerId() != null) {
            userRepository.findById(report.getHandlerId()).ifPresent(user -> {
                vo.setHandlerName(user.getNickname());
            });
        }

        fillTargetInfo(vo, report.getTargetType(), report.getTargetId());

        long reportCount = reportRepository.countByTargetIdAndTargetType(report.getTargetId(), report.getTargetType());
        vo.setReportCount((int) reportCount);

        List<Report> relatedReports = reportRepository.findByTargetIdAndTargetTypeOrderByCreatedAtDesc(
                report.getTargetId(), report.getTargetType());
        List<ReportDetailVO.ReportHistoryItem> history = relatedReports.stream()
                .map(this::toHistoryItem)
                .collect(Collectors.toList());
        vo.setReportHistory(history);

        return vo;
    }

    private void fillTargetInfo(ReportDetailVO vo, String targetType, Long targetId) {
        if ("POST".equals(targetType)) {
            postRepository.findById(targetId).ifPresent(post -> {
                vo.setTargetTitle(post.getTitle());
                vo.setTargetContent(post.getContent());
                vo.setTargetStatus(post.getStatus());
                vo.setTargetCoverImage(post.getCoverImage());
                vo.setTargetAuthorId(post.getUserId());
                userRepository.findById(post.getUserId()).ifPresent(user -> {
                    vo.setTargetAuthorName(user.getNickname());
                });
            });
        } else if ("COMMENT".equals(targetType)) {
            commentRepository.findById(targetId).ifPresent(comment -> {
                vo.setTargetContent(comment.getContent());
                vo.setTargetTitle("评论#" + targetId);
                vo.setTargetAuthorId(comment.getUserId());
                userRepository.findById(comment.getUserId()).ifPresent(user -> {
                    vo.setTargetAuthorName(user.getNickname());
                });
                postRepository.findById(comment.getPostId()).ifPresent(post -> {
                    vo.setTargetTitle("评论 - " + post.getTitle());
                });
            });
        }
    }

    private ReportDetailVO.ReportHistoryItem toHistoryItem(Report report) {
        ReportDetailVO.ReportHistoryItem item = new ReportDetailVO.ReportHistoryItem();
        item.setId(report.getId());
        item.setReporterId(report.getReporterId());
        item.setReason(report.getReason());
        item.setCreatedAt(report.getCreatedAt());
        userRepository.findById(report.getReporterId()).ifPresent(user -> {
            item.setReporterName(user.getNickname());
        });
        return item;
    }

    @Override
    public Page<ReportGroupVO> getGroupedReports(Pageable pageable, String status, String targetType) {
        List<Object[]> targets = reportRepository.findDistinctPendingTargets();
        
        List<ReportGroupVO> groups = new ArrayList<>();
        for (Object[] target : targets) {
            Long targetId = (Long) target[0];
            String type = (String) target[1];
            
            if (targetType != null && !targetType.isBlank() && !targetType.equals(type)) {
                continue;
            }
            
            ReportGroupVO group = buildReportGroup(targetId, type, status);
            if (group != null && group.getReportCount() > 0) {
                groups.add(group);
            }
        }

        groups.sort((a, b) -> b.getLatestReportTime().compareTo(a.getLatestReportTime()));

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), groups.size());
        List<ReportGroupVO> pageContent = groups.subList(start, end);

        return new PageImpl<>(pageContent, pageable, groups.size());
    }

    private ReportGroupVO buildReportGroup(Long targetId, String targetType, String status) {
        List<Report> reports;
        if (status != null && !status.isBlank()) {
            reports = reportRepository.findByTargetIdAndTargetTypeAndStatus(targetId, targetType, status);
        } else {
            reports = reportRepository.findByTargetIdAndTargetTypeOrderByCreatedAtDesc(targetId, targetType);
        }

        if (reports.isEmpty()) {
            return null;
        }

        ReportGroupVO group = new ReportGroupVO();
        group.setTargetId(targetId);
        group.setTargetType(targetType);
        group.setReportCount(reports.size());

        LocalDateTime latest = reports.stream()
                .map(Report::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime earliest = reports.stream()
                .map(Report::getCreatedAt)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        group.setLatestReportTime(latest);
        group.setEarliestReportTime(earliest);

        List<String> reasons = reports.stream()
                .map(Report::getReason)
                .distinct()
                .collect(Collectors.toList());
        group.setReasons(reasons);

        List<ReportGroupVO.ReportHistoryItem> items = reports.stream()
                .map(this::toGroupHistoryItem)
                .collect(Collectors.toList());
        group.setReports(items);

        fillGroupTargetInfo(group, targetType, targetId);

        return group;
    }

    private void fillGroupTargetInfo(ReportGroupVO group, String targetType, Long targetId) {
        if ("POST".equals(targetType)) {
            postRepository.findById(targetId).ifPresent(post -> {
                group.setTargetTitle(post.getTitle());
                group.setTargetContent(post.getContent());
                group.setTargetStatus(post.getStatus());
                userRepository.findById(post.getUserId()).ifPresent(user -> {
                    group.setTargetAuthorName(user.getNickname());
                });
            });
        } else if ("COMMENT".equals(targetType)) {
            commentRepository.findById(targetId).ifPresent(comment -> {
                group.setTargetContent(comment.getContent());
                group.setTargetTitle("评论#" + targetId);
                userRepository.findById(comment.getUserId()).ifPresent(user -> {
                    group.setTargetAuthorName(user.getNickname());
                });
            });
        }
    }

    private ReportGroupVO.ReportHistoryItem toGroupHistoryItem(Report report) {
        ReportGroupVO.ReportHistoryItem item = new ReportGroupVO.ReportHistoryItem();
        item.setId(report.getId());
        item.setReporterId(report.getReporterId());
        item.setReason(report.getReason());
        item.setStatus(report.getStatus());
        item.setCreatedAt(report.getCreatedAt());
        userRepository.findById(report.getReporterId()).ifPresent(user -> {
            item.setReporterName(user.getNickname());
            item.setReporterAvatar(user.getAvatarUrl());
        });
        return item;
    }

    @Override
    public ReportReasonStatsVO.StatsResponse getReportStats() {
        ReportReasonStatsVO.StatsResponse response = new ReportReasonStatsVO.StatsResponse();

        long total = reportRepository.count();
        long pending = reportRepository.countByStatus("PENDING");
        long resolved = reportRepository.countByStatus("RESOLVED");
        long rejected = reportRepository.countByStatus("REJECTED");

        response.setTotalReports((int) total);
        response.setPendingReports((int) pending);
        response.setResolvedReports((int) resolved);
        response.setRejectedReports((int) rejected);

        List<Object[]> reasonCounts = reportRepository.countByReason();
        List<ReportReasonStatsVO> statsList = new ArrayList<>();

        for (Object[] row : reasonCounts) {
            ReportReasonStatsVO stats = new ReportReasonStatsVO();
            stats.setReason((String) row[0]);
            stats.setCount(((Number) row[1]).intValue());
            stats.setPercentage(total > 0 ? (stats.getCount() * 100.0 / total) : 0);
            statsList.add(stats);
        }

        response.setReasonStats(statsList);
        return response;
    }

    @Override
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void processAutoHandleRules() {
        List<AutoHandleRule> rules = autoHandleRuleRepository.findByEnabledTrue();
        
        for (AutoHandleRule rule : rules) {
            try {
                processRule(rule);
            } catch (Exception e) {
                log.error("处理自动规则失败: ruleId={}, error={}", rule.getId(), e.getMessage());
            }
        }
    }

    private void processRule(AutoHandleRule rule) {
        LocalDateTime startTime = null;
        if (rule.getTimeWindowHours() != null && rule.getTimeWindowHours() > 0) {
            startTime = LocalDateTime.now().minusHours(rule.getTimeWindowHours());
        }

        List<Object[]> targets;
        if (startTime != null) {
            targets = reportRepository.findTargetsWithReportCount("PENDING", rule.getReportThreshold());
        } else {
            targets = reportRepository.findTargetsWithReportCount("PENDING", rule.getReportThreshold());
        }

        for (Object[] target : targets) {
            Long targetId = (Long) target[0];
            String targetType = (String) target[1];

            if (!rule.getTargetType().equals(targetType)) {
                continue;
            }

            if (startTime != null) {
                List<Report> recentReports = reportRepository.findByTargetIdAndTargetTypeAndCreatedAtAfter(
                        targetId, targetType, startTime);
                if (recentReports.size() < rule.getReportThreshold()) {
                    continue;
                }
            }

            executeAutoAction(targetType, targetId, rule.getAction());
            markReportsAsResolved(targetId, targetType);
        }
    }

    private void executeAutoAction(String targetType, Long targetId, String action) {
        String targetTitle = getTargetTitle(targetType, targetId);

        if ("POST".equals(targetType)) {
            postRepository.findById(targetId).ifPresent(post -> {
                if ("DELETE".equals(action)) {
                    postRepository.delete(post);
                } else {
                    post.setStatus("DISABLED");
                    postRepository.save(post);
                }
                redisCacheService.evict("shenghuotie:cache:post:detail:" + post.getId());
                redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
                notificationService.createNotification(
                        post.getUserId(), 0L, "SYSTEM", post.getId(),
                        "你的笔记「" + post.getTitle() + "」因被多次举报已被系统自动" + ("DELETE".equals(action) ? "删除" : "禁用")
                );
            });
        } else if ("COMMENT".equals(targetType) && "DELETE".equals(action)) {
            commentRepository.findById(targetId).ifPresent(comment -> {
                deleteCommentRecursively(comment.getId());
            });
        }
    }

    private void markReportsAsResolved(Long targetId, String targetType) {
        List<Report> reports = reportRepository.findByTargetIdAndTargetTypeAndStatus(targetId, targetType, "PENDING");
        for (Report report : reports) {
            report.setStatus("RESOLVED");
            report.setHandleResult("系统自动处理：达到举报阈值");
            report.setHandledAt(LocalDateTime.now());
        }
        reportRepository.saveAll(reports);
    }

    private String getTargetTitle(String targetType, Long targetId) {
        if ("POST".equals(targetType)) {
            return postRepository.findById(targetId)
                    .map(Post::getTitle)
                    .orElse("该笔记");
        } else if ("COMMENT".equals(targetType)) {
            return "该评论";
        }
        return "该内容";
    }

    @Override
    public List<AutoHandleRuleVO> getAutoHandleRules() {
        return autoHandleRuleRepository.findAll().stream()
                .map(this::toRuleVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AutoHandleRuleVO createAutoHandleRule(CreateAutoHandleRuleRequest request) {
        AutoHandleRule rule = new AutoHandleRule();
        rule.setTargetType(request.targetType());
        rule.setReportThreshold(request.reportThreshold());
        rule.setAction(request.action());
        rule.setTimeWindowHours(request.timeWindowHours());
        rule.setEnabled(request.enabled() != null ? request.enabled() : true);
        autoHandleRuleRepository.save(rule);
        return toRuleVO(rule);
    }

    @Override
    @Transactional
    public AutoHandleRuleVO updateAutoHandleRule(Long id, CreateAutoHandleRuleRequest request) {
        AutoHandleRule rule = autoHandleRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        rule.setTargetType(request.targetType());
        rule.setReportThreshold(request.reportThreshold());
        rule.setAction(request.action());
        rule.setTimeWindowHours(request.timeWindowHours());
        if (request.enabled() != null) {
            rule.setEnabled(request.enabled());
        }
        autoHandleRuleRepository.save(rule);
        return toRuleVO(rule);
    }

    @Override
    @Transactional
    public void deleteAutoHandleRule(Long id) {
        autoHandleRuleRepository.deleteById(id);
    }

    @Override
    public void deleteReport(Long reportId) {
        reportRepository.deleteById(reportId);
    }

    private AutoHandleRuleVO toRuleVO(AutoHandleRule rule) {
        return new AutoHandleRuleVO(
                rule.getId(),
                rule.getTargetType(),
                rule.getReportThreshold(),
                rule.getAction(),
                rule.getTimeWindowHours(),
                rule.getEnabled()
        );
    }

    private ReportVO toVO(Report report) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setReporterId(report.getReporterId());
        vo.setTargetId(report.getTargetId());
        vo.setTargetType(report.getTargetType());
        vo.setReason(report.getReason());
        vo.setStatus(report.getStatus());
        vo.setHandlerId(report.getHandlerId());
        vo.setHandleResult(report.getHandleResult());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setHandledAt(report.getHandledAt());

        userRepository.findById(report.getReporterId()).ifPresent(user -> {
            vo.setReporterName(user.getNickname());
            vo.setReporterAvatar(user.getAvatarUrl());
        });

        if (report.getHandlerId() != null) {
            userRepository.findById(report.getHandlerId()).ifPresent(user -> {
                vo.setHandlerName(user.getNickname());
            });
        }

        if ("POST".equals(report.getTargetType())) {
            postRepository.findById(report.getTargetId()).ifPresent(post -> {
                vo.setTargetTitle(post.getTitle());
            });
        } else if ("COMMENT".equals(report.getTargetType())) {
            vo.setTargetTitle("评论#" + report.getTargetId());
        }

        return vo;
    }
}
