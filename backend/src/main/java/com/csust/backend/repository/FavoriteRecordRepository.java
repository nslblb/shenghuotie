package com.csust.backend.repository;

import com.csust.backend.entity.FavoriteRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FavoriteRecordRepository extends JpaRepository<FavoriteRecord, Long> {

    Optional<FavoriteRecord> findByUserIdAndPostId(Long userId, Long postId);

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    void deleteByUserIdAndPostId(Long userId, Long postId);

    void deleteByPostId(Long postId);

    List<FavoriteRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 收藏记录分页：仅 SQL 层分页，避免全表加载后内存切片 OOM */
    Page<FavoriteRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    int countByPostId(Long postId);

    @Query("SELECT f.postId FROM FavoriteRecord f WHERE f.userId = :uid AND f.postId IN :ids")
    Set<Long> findFavoritedPostIds(@Param("uid") Long userId, @Param("ids") Collection<Long> postIds);
}
