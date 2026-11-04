package com.csust.backend.service;

public interface FavoriteService {

    void favorite(Long userId, Long postId);

    void unfavorite(Long userId, Long postId);

    boolean isFavorited(Long userId, Long postId);
}
