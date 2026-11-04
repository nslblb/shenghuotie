package com.csust.backend.repository;

import com.csust.backend.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetIdAndTargetType(Long reporterId, Long targetId, String targetType);

    boolean existsByReporterIdAndTargetIdAndTargetTypeAndStatus(Long reporterId, Long targetId, String targetType, String status);

    Page<Report> findByStatus(String status, Pageable pageable);

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);

    long countByTargetIdAndTargetType(Long targetId, String targetType);

    List<Report> findByTargetIdAndTargetTypeOrderByCreatedAtDesc(Long targetId, String targetType);

    List<Report> findByTargetIdAndTargetTypeAndStatus(Long targetId, String targetType, String status);

    @Query("SELECT r.targetId, r.targetType, COUNT(r) as cnt FROM Report r " +
           "WHERE r.status = :status " +
           "GROUP BY r.targetId, r.targetType " +
           "HAVING COUNT(r) >= :threshold")
    List<Object[]> findTargetsWithReportCount(@Param("status") String status, @Param("threshold") long threshold);

    @Query("SELECT r.reason, COUNT(r) FROM Report r GROUP BY r.reason ORDER BY COUNT(r) DESC")
    List<Object[]> countByReason();

    @Query("SELECT COUNT(r) FROM Report r WHERE r.status = :status AND r.createdAt >= :startTime")
    long countByStatusAndCreatedAtAfter(@Param("status") String status, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT r FROM Report r WHERE r.targetId = :targetId AND r.targetType = :targetType AND r.createdAt >= :startTime")
    List<Report> findByTargetIdAndTargetTypeAndCreatedAtAfter(
            @Param("targetId") Long targetId, 
            @Param("targetType") String targetType, 
            @Param("startTime") LocalDateTime startTime);

    @Query("SELECT r FROM Report r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:targetType IS NULL OR r.targetType = :targetType) AND " +
           "(:keyword IS NULL OR r.reason LIKE %:keyword%)")
    Page<Report> findByConditions(
            @Param("status") String status,
            @Param("targetType") String targetType,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT DISTINCT r.targetId, r.targetType FROM Report r WHERE r.status = 'PENDING'")
    List<Object[]> findDistinctPendingTargets();

    @Query("SELECT COUNT(r) FROM Report r WHERE r.targetId = :targetId AND r.targetType = :targetType AND r.status = 'PENDING'")
    long countPendingByTargetIdAndTargetType(@Param("targetId") Long targetId, @Param("targetType") String targetType);
}
