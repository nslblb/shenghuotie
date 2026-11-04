package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.common.Role;
import com.csust.backend.common.Status;
import com.csust.backend.common.XssUtil;
import com.csust.backend.dto.response.UserDetailVO;
import com.csust.backend.dto.response.UserVO;
import com.csust.backend.entity.User;
import com.csust.backend.repository.FollowRepository;
import com.csust.backend.repository.PostRepository;
import com.csust.backend.repository.CommentRepository;
import com.csust.backend.repository.ReportRepository;
import com.csust.backend.repository.UserRepository;
import com.csust.backend.service.AdminUserService;
import com.csust.backend.service.NotificationService;
import com.csust.backend.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final FollowRepository followRepository;
    private final ReportRepository reportRepository;
    private final RedisCacheService redisCacheService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    @Override
    public Page<UserVO> getUsers(String keyword, String role, String status, String startTime, String endTime, Pageable pageable) {
        Role roleEnum = role != null && !role.isBlank() ? Role.valueOf(role.toUpperCase()) : null;
        Status statusEnum = status != null && !status.isBlank() ? Status.valueOf(status.toUpperCase()) : null;
        LocalDateTime start = startTime != null && !startTime.isBlank() ? LocalDate.parse(startTime).atStartOfDay() : null;
        LocalDateTime end = endTime != null && !endTime.isBlank() ? LocalDate.parse(endTime).atTime(23, 59, 59) : null;

        Page<User> users = userRepository.findByConditions(keyword, roleEnum, statusEnum, start, end, pageable);
        return users.map(UserVO::fromEntity);
    }

    @Override
    public UserDetailVO getUserDetail(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserDetailVO vo = UserDetailVO.fromEntity(user);
        vo.setPostCount(postRepository.countByUserId(id));
        vo.setCommentCount(commentRepository.countByUserId(id));
        vo.setFollowerCount(followRepository.countByFollowingId(id));
        vo.setFollowingCount(followRepository.countByFollowerId(id));
        vo.setReportCount(reportRepository.countByTargetIdAndTargetType(id, "USER"));

        return vo;
    }

    @Override
    public void updateUserStatus(Long id, String status, String banReason) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Status newStatus = Status.valueOf(status.toUpperCase());
        user.setStatus(newStatus);

        if (newStatus == Status.DISABLED) {
            user.setBanReason(XssUtil.clean(banReason));
            user.setBannedAt(LocalDateTime.now());
            user.setBannedBy(0L);
        } else {
            user.setBanReason(null);
            user.setBannedAt(null);
            user.setBannedBy(null);
        }

        userRepository.save(user);
        redisCacheService.evict("shenghuotie:cache:user:" + id);
        redisCacheService.evict("shenghuotie:cache:user:profile:" + id);
    }

    @Override
    public void resetUserPassword(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String newPassword = generateRandomPassword(8);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redisCacheService.evict("shenghuotie:cache:user:" + id);
        redisCacheService.evict("shenghuotie:cache:user:profile:" + id);
        notificationService.createNotification(
                id, 0L, "SYSTEM", null,
                "你的密码已被管理员重置，新密码为：" + newPassword + "，请尽快修改"
        );
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    public void updateUserRole(Long id, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.setRole(Role.valueOf(role.toUpperCase()));
        userRepository.save(user);
        redisCacheService.evict("shenghuotie:cache:user:" + id);
        redisCacheService.evict("shenghuotie:cache:user:profile:" + id);
    }

    @Override
    @Transactional
    public void batchUpdateUserStatus(List<Long> ids, String status, String banReason) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        Status newStatus = Status.valueOf(status.toUpperCase());
        LocalDateTime bannedAt = newStatus == Status.DISABLED ? LocalDateTime.now() : null;

        userRepository.updateStatusByIds(ids, newStatus, banReason, bannedAt, 0L);

        for (Long id : ids) {
            redisCacheService.evict("shenghuotie:cache:user:" + id);
            redisCacheService.evict("shenghuotie:cache:user:profile:" + id);
        }
    }
}
