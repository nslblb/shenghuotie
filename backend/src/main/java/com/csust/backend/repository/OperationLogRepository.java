package com.csust.backend.repository;

import com.csust.backend.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    Page<OperationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<OperationLog> findByOperationContaining(String keyword, Pageable pageable);

    Page<OperationLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT l FROM OperationLog l WHERE " +
           "(:userId IS NULL OR l.userId = :userId) AND " +
           "(:operation IS NULL OR l.operation LIKE %:operation%) AND " +
           "(:status IS NULL OR l.status = :status) AND " +
           "(:startTime IS NULL OR l.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR l.createdAt <= :endTime)")
    Page<OperationLog> findByConditions(
            @Param("userId") Long userId,
            @Param("operation") String operation,
            @Param("status") String status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    long countByStatus(String status);

    long countByUserId(Long userId);

    long countByOperation(String operation);

    /** DB 侧聚合平均耗时，避免 findAll() 内存计算 OOM */
    @Query("SELECT COALESCE(AVG(l.duration), 0) FROM OperationLog l")
    double avgDuration();

    /** DB 侧分组统计操作类型，避免 findAll() 内存聚合 OOM */
    @Query("SELECT l.operation AS operation, COUNT(l) AS cnt FROM OperationLog l " +
           "GROUP BY l.operation ORDER BY COUNT(l) DESC")
    List<Object[]> topOperations(Pageable pageable);
}
