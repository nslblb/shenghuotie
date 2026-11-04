package com.csust.backend.repository;

import com.csust.backend.entity.Appeal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    Page<Appeal> findByStatus(String status, Pageable pageable);

    List<Appeal> findByPostIdOrderByCreatedAtDesc(Long postId);

    List<Appeal> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT a FROM Appeal a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:postId IS NULL OR a.postId = :postId) AND " +
           "(:userId IS NULL OR a.userId = :userId)")
    Page<Appeal> findByConditions(
            @Param("status") String status,
            @Param("postId") Long postId,
            @Param("userId") Long userId,
            Pageable pageable);

    long countByStatus(String status);

    boolean existsByPostIdAndStatus(Long postId, String status);
}
