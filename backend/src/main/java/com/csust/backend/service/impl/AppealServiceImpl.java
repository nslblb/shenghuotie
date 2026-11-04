package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.dto.response.AppealVO;
import com.csust.backend.entity.Appeal;
import com.csust.backend.entity.Post;
import com.csust.backend.entity.User;
import com.csust.backend.entity.Report;
import com.csust.backend.repository.AppealRepository;
import com.csust.backend.repository.PostRepository;
import com.csust.backend.repository.UserRepository;
import com.csust.backend.repository.ReportRepository;
import com.csust.backend.service.AppealService;
import com.csust.backend.service.NotificationService;
import com.csust.backend.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppealServiceImpl implements AppealService {

    private final AppealRepository appealRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final RedisCacheService redisCacheService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public void createAppeal(Long postId, Long userId, String reason) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }

        if (!"DISABLED".equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该笔记未被封禁，无法申诉");
        }

        if (appealRepository.existsByPostIdAndStatus(postId, "PENDING")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该笔记已有待处理的申诉");
        }

        Appeal appeal = new Appeal();
        appeal.setPostId(postId);
        appeal.setUserId(userId);
        appeal.setReason(reason);
        appeal.setStatus("PENDING");

        String banReason = null;
        List<Appeal> previousAppeals = appealRepository.findByPostIdOrderByCreatedAtDesc(postId);
        if (!previousAppeals.isEmpty()) {
            banReason = previousAppeals.get(0).getBanReason();
        }
        if (banReason == null) {
            List<Report> reports = reportRepository.findByTargetIdAndTargetTypeAndStatus(postId, "POST", "RESOLVED");
            if (!reports.isEmpty()) {
                // 取最近一次处理的举报理由
                banReason = reports.stream()
                        .filter(r -> r.getHandledAt() != null)
                        .max(java.util.Comparator.comparing(Report::getHandledAt))
                        .map(Report::getReason)
                        .orElse(reports.get(reports.size() - 1).getReason());
            }
        }
        appeal.setBanReason(banReason);

        appealRepository.save(appeal);

        post.setStatus("APPEALING");
        postRepository.save(post);

        redisCacheService.evict("shenghuotie:cache:post:detail:" + postId);
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
    }

    @Override
    public Page<AppealVO> getAppeals(Pageable pageable, String status) {
        Page<Appeal> appeals;
        if (status != null && !status.isBlank()) {
            appeals = appealRepository.findByStatus(status, pageable);
        } else {
            appeals = appealRepository.findAll(pageable);
        }
        return appeals.map(this::toVO);
    }

    @Override
    public AppealVO getAppealDetail(Long appealId) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND.getCode(), "申诉不存在"));

        AppealVO vo = toVO(appeal);

        List<Appeal> history = appealRepository.findByPostIdOrderByCreatedAtDesc(appeal.getPostId());
        List<AppealVO.AppealHistoryItem> historyItems = history.stream()
                .filter(a -> !a.getId().equals(appealId))
                .map(this::toHistoryItem)
                .collect(Collectors.toList());
        vo.setHistory(historyItems);

        return vo;
    }

    @Override
    @Transactional
    public void handleAppeal(Long appealId, Long handlerId, boolean approved, String handleNote) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND.getCode(), "申诉不存在"));

        if (!"PENDING".equals(appeal.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该申诉已处理");
        }

        appeal.setStatus(approved ? "APPROVED" : "REJECTED");
        appeal.setHandlerId(handlerId);
        appeal.setHandleNote(handleNote);
        appeal.setHandledAt(LocalDateTime.now());
        appealRepository.save(appeal);

        Post post = postRepository.findById(appeal.getPostId()).orElse(null);
        if (post != null) {
            if (approved) {
                post.setStatus("PUBLISHED");
                postRepository.save(post);
            } else {
                post.setStatus("DISABLED");
                postRepository.save(post);
            }
            redisCacheService.evict("shenghuotie:cache:post:detail:" + post.getId());
            redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
        }

        String resultText = approved ? "已批准" : "已驳回";
        String noteText = handleNote != null && !handleNote.isBlank() ? "，备注：" + handleNote : "";

        notificationService.createNotification(
                appeal.getUserId(), 0L, "APPEAL", appeal.getPostId(),
                "你的申诉" + resultText + noteText
        );
    }

    @Override
    public Page<AppealVO> getAppealHistory(Long postId, Pageable pageable) {
        Page<Appeal> appeals = appealRepository.findByConditions(null, postId, null, pageable);
        return appeals.map(this::toVO);
    }

    @Override
    public void deleteAppeal(Long appealId) {
        appealRepository.deleteById(appealId);
    }

    private AppealVO toVO(Appeal appeal) {
        AppealVO vo = new AppealVO();
        vo.setId(appeal.getId());
        vo.setPostId(appeal.getPostId());
        vo.setUserId(appeal.getUserId());
        vo.setReason(appeal.getReason());
        vo.setBanReason(appeal.getBanReason());
        // 如果 banReason 存的是旧的状态文本（如"举报成立"）或为空，回填实际举报理由
        if (vo.getBanReason() == null || vo.getBanReason().startsWith("举报")) {
            // 取申诉创建时间之前最近一次处理的举报理由
            List<Report> reports = reportRepository.findByTargetIdAndTargetTypeAndStatus(
                    appeal.getPostId(), "POST", "RESOLVED");
            Report matched = reports.stream()
                    .filter(r -> r.getHandledAt() != null && r.getHandledAt().isBefore(appeal.getCreatedAt()))
                    .max(java.util.Comparator.comparing(Report::getHandledAt))
                    .orElse(reports.isEmpty() ? null : reports.get(reports.size() - 1));
            if (matched != null) {
                vo.setBanReason(matched.getReason());
            }
        }
        vo.setStatus(appeal.getStatus());
        vo.setHandlerId(appeal.getHandlerId());
        vo.setHandleNote(appeal.getHandleNote());
        vo.setHandledAt(appeal.getHandledAt());
        vo.setCreatedAt(appeal.getCreatedAt());

        userRepository.findById(appeal.getUserId()).ifPresent(user -> {
            vo.setUserName(user.getNickname());
            vo.setUserAvatar(user.getAvatarUrl());
        });

        if (appeal.getHandlerId() != null) {
            userRepository.findById(appeal.getHandlerId()).ifPresent(user -> {
                vo.setHandlerName(user.getNickname());
            });
        }

        postRepository.findById(appeal.getPostId()).ifPresent(post -> {
            vo.setPostTitle(post.getTitle());
            vo.setPostContent(post.getContent());
            vo.setPostCoverImage(post.getCoverImage());
        });

        return vo;
    }

    private AppealVO.AppealHistoryItem toHistoryItem(Appeal appeal) {
        AppealVO.AppealHistoryItem item = new AppealVO.AppealHistoryItem();
        item.setId(appeal.getId());
        item.setReason(appeal.getReason());
        item.setStatus(appeal.getStatus());
        item.setHandleNote(appeal.getHandleNote());
        item.setCreatedAt(appeal.getCreatedAt());
        item.setHandledAt(appeal.getHandledAt());

        if (appeal.getHandlerId() != null) {
            userRepository.findById(appeal.getHandlerId()).ifPresent(user -> {
                item.setHandlerName(user.getNickname());
            });
        }

        return item;
    }
}
