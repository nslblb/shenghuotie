package com.csust.backend.repository;

import com.csust.backend.entity.LikeRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LikeRecordRepository extends JpaRepository<LikeRecord, Long> {

    Optional<LikeRecord> findByUserIdAndPostId(Long userId, Long postId);

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    void deleteByUserIdAndPostId(Long userId, Long postId);

    void deleteByPostId(Long postId);

    List<LikeRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 点赞记录分页：SQL 层分页避免全表加载 OOM */
    Page<LikeRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByCreatedAtAfter(java.time.LocalDateTime dateTime);

    int countByPostId(Long postId);

    @Query("SELECT l.postId FROM LikeRecord l WHERE l.userId = :uid AND l.postId IN :ids")
    Set<Long> findLikedPostIds(@Param("uid") Long userId, @Param("ids") Collection<Long> postIds);
}
