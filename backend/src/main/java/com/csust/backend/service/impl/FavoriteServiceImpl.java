package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.entity.FavoriteRecord;
import com.csust.backend.repository.FavoriteRecordRepository;
import com.csust.backend.repository.PostRepository;
import com.csust.backend.service.FavoriteService;
import com.csust.backend.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private static final String CACHE_FAVORITE = "shenghuotie:cache:favorite:";
    private static final String CACHE_POST_DETAIL = "shenghuotie:cache:post:detail:";
    private static final long FAVORITE_TTL = 1800;

    private final FavoriteRecordRepository favoriteRecordRepository;
    private final PostRepository postRepository;
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional
    public void favorite(Long userId, Long postId) {
        if (favoriteRecordRepository.existsByUserIdAndPostId(userId, postId)) {
            throw new BusinessException(ErrorCode.ALREADY_FAVORITED);
        }

        FavoriteRecord record = new FavoriteRecord();
        record.setUserId(userId);
        record.setPostId(postId);
        favoriteRecordRepository.save(record);

        postRepository.incrementFavoriteCount(postId);

        redisCacheService.evict(CACHE_FAVORITE + userId + ":" + postId);
        redisCacheService.evict(CACHE_POST_DETAIL + postId);
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
    }

    @Override
    @Transactional
    public void unfavorite(Long userId, Long postId) {
        if (!favoriteRecordRepository.existsByUserIdAndPostId(userId, postId)) {
            throw new BusinessException(ErrorCode.NOT_FAVORITED);
        }

        favoriteRecordRepository.deleteByUserIdAndPostId(userId, postId);

        postRepository.decrementFavoriteCount(postId);

        redisCacheService.evict(CACHE_FAVORITE + userId + ":" + postId);
        redisCacheService.evict(CACHE_POST_DETAIL + postId);
        redisCacheService.evictByPattern("shenghuotie:cache:post:list:*");
    }

    @Override
    public boolean isFavorited(Long userId, Long postId) {
        String cacheKey = CACHE_FAVORITE + userId + ":" + postId;
        Object cached = redisCacheService.get(cacheKey);
        if (cached != null) {
            return Boolean.TRUE.equals(cached);
        }
        boolean favorited = favoriteRecordRepository.existsByUserIdAndPostId(userId, postId);
        redisCacheService.set(cacheKey, favorited, FAVORITE_TTL);
        return favorited;
    }
}
