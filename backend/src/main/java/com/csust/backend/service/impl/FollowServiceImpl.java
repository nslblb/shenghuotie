package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.dto.response.UserVO;
import com.csust.backend.entity.Follow;
import com.csust.backend.entity.User;
import com.csust.backend.repository.FollowRepository;
import com.csust.backend.repository.UserRepository;
import com.csust.backend.service.AntiSpamService;
import com.csust.backend.service.FollowService;
import com.csust.backend.service.NotificationService;
import com.csust.backend.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private static final String CACHE_USER_PROFILE = "shenghuotie:cache:user:profile:";

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RedisCacheService redisCacheService;
    private final AntiSpamService antiSpamService;

    @Override
    @Transactional
    public void follow(Long followerId, Long followingId) {
        // 业务滥用防护：关注日总量
        antiSpamService.checkDailyFollowLimit(followerId);

        if (followerId.equals(followingId)) {
            throw new BusinessException(ErrorCode.CANNOT_FOLLOW_SELF);
        }
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new BusinessException(ErrorCode.ALREADY_FOLLOWED);
        }

        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);
        followRepository.save(follow);

        String nickname = userRepository.findById(followerId)
                .map(u -> u.getNickname()).orElse("有人");
        notificationService.createNotification(
                followingId, followerId, "FOLLOW", null,
                nickname + " 关注了你");

        redisCacheService.evict(CACHE_USER_PROFILE + followerId);
        redisCacheService.evict(CACHE_USER_PROFILE + followingId);
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        if (!followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new BusinessException(ErrorCode.NOT_FOLLOWED);
        }
        followRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);

        redisCacheService.evict(CACHE_USER_PROFILE + followerId);
        redisCacheService.evict(CACHE_USER_PROFILE + followingId);
    }

    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    @Override
    public Page<UserVO> getFollowers(Long userId, Pageable pageable) {
        Page<Follow> follows = followRepository.findByFollowingIdOrderByCreatedAtDesc(userId, pageable);
        if (follows.isEmpty()) return new PageImpl<>(List.of(), pageable, 0);
        List<Long> ids = follows.stream().map(Follow::getFollowerId).toList();
        Map<Long, User> map = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<UserVO> voList = ids.stream()
                .map(map::get)
                .filter(Objects::nonNull)
                .map(UserVO::fromEntity)
                .toList();
        return new PageImpl<>(voList, pageable, follows.getTotalElements());
    }

    @Override
    public Page<UserVO> getFollowings(Long userId, Pageable pageable) {
        Page<Follow> follows = followRepository.findByFollowerIdOrderByCreatedAtDesc(userId, pageable);
        if (follows.isEmpty()) return new PageImpl<>(List.of(), pageable, 0);
        List<Long> ids = follows.stream().map(Follow::getFollowingId).toList();
        Map<Long, User> map = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<UserVO> voList = ids.stream()
                .map(map::get)
                .filter(Objects::nonNull)
                .map(UserVO::fromEntity)
                .toList();
        return new PageImpl<>(voList, pageable, follows.getTotalElements());
    }

    @Override
    public int getFollowerCount(Long userId) {
        return followRepository.countByFollowingId(userId);
    }

    @Override
    public int getFollowingCount(Long userId) {
        return followRepository.countByFollowerId(userId);
    }
}
