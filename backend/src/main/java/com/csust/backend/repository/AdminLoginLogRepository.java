package com.csust.backend.repository;

import com.csust.backend.entity.AdminLoginLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AdminLoginLogRepository extends JpaRepository<AdminLoginLog, Long> {

    Page<AdminLoginLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AdminLoginLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT l FROM AdminLoginLog l WHERE " +
           "(:userId IS NULL OR l.userId = :userId) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "(:startTime IS NULL OR l.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR l.createdAt <= :endTime)")
    Page<AdminLoginLog> findByConditions(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    long countByStatus(String status);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, String status);
}
