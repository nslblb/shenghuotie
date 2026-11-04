package com.csust.backend.service;

public interface LikeService {

    void like(Long userId, Long postId);

    void unlike(Long userId, Long postId);

    boolean isLiked(Long userId, Long postId);
}
