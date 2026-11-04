package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.entity.LikeRecord;
import com.csust.backend.entity.Post;
import com.csust.backend.repository.LikeRecordRepository;
import com.csust.backend.repository.PostRepository;
import com.csust.backend.service.AntiSpamService;
import com.csust.backend.service.LikeService;
import com.csust.backend.service.NotificationService;
import com.csust.backend.service.RedisCacheService;
import com.csust.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private static final String CACHE_LIKE = "shenghuotie:cache:like:";
    private static final String CACHE_POST_DETAIL = "shenghuotie:cache:post:detail:";
    private static final long LIKE_TTL = 1800;

    private final LikeRecordRepository likeRecordRepository;
    private final PostRepository postRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final RedisCacheService redisCacheService;
    private final AntiSpamService antiSpamService;

    @Override
    @Transactional
    public void like(Long userId, Long postId) {
        // 业务滥用防护：日总量 + 异常模式
        antiSpamService.checkDailyLikeLimit(userId);
        antiSpamService.checkRapidLikePattern(userId);

        if (likeRecordRepository.existsByUserIdAndPostId(userId, postId)) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }

        LikeRecord record = new LikeRecord();
        record.setUserId(userId);
        record.setPostId(postId);
        likeRecordRepository.save(record);

        postRepository.findById(postId).ifPresent(post -> {
            postRepository.incrementLikeCount(postId);

            String nickname = userRepository.findById(userId)
                    .map(u -> u.getNickname()).orElse("有人");
            notificationService.createNotification(
                    post.getUserId(), userId, "LIKE", postId,
                    nickname + " 赞了你的笔记「" + post.getTitle() + "」");
        });

        redisCacheService.evict(CACHE_LIKE + userId + ":" + postId);
        redisCacheService.evict(CACHE_POST_DETAIL + postId);
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
    }

    @Override
    @Transactional
    public void unlike(Long userId, Long postId) {
        if (!likeRecordRepository.existsByUserIdAndPostId(userId, postId)) {
            throw new BusinessException(ErrorCode.NOT_LIKED);
        }

        likeRecordRepository.deleteByUserIdAndPostId(userId, postId);

        postRepository.findById(postId).ifPresent(post -> {
            postRepository.decrementLikeCount(postId);
        });

        redisCacheService.evict(CACHE_LIKE + userId + ":" + postId);
        redisCacheService.evict(CACHE_POST_DETAIL + postId);
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
    }

    @Override
    public boolean isLiked(Long userId, Long postId) {
        String cacheKey = CACHE_LIKE + userId + ":" + postId;
        Object cached = redisCacheService.get(cacheKey);
        if (cached != null) {
            return Boolean.TRUE.equals(cached);
        }
        boolean liked = likeRecordRepository.existsByUserIdAndPostId(userId, postId);
        redisCacheService.set(cacheKey, liked, LIKE_TTL);
        return liked;
    }
}
