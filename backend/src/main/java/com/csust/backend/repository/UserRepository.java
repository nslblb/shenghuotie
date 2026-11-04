package com.csust.backend.repository;

import com.csust.backend.common.Role;
import com.csust.backend.common.Status;
import com.csust.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByNickname(String nickname);

    Page<User> findByNicknameContaining(String nickname, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime dateTime);

    long countByStatus(Status status);

    @Query("SELECT DATE(u.createdAt) as date, COUNT(u) as count FROM User u WHERE u.createdAt >= :startDate GROUP BY DATE(u.createdAt) ORDER BY DATE(u.createdAt)")
    List<Object[]> countByDateAfter(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT u FROM User u WHERE " +
           "(:keyword IS NULL OR u.nickname LIKE %:keyword%) AND " +
           "(:role IS NULL OR u.role = :role) AND " +
           "(:status IS NULL OR u.status = :status) AND " +
           "(:startTime IS NULL OR u.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR u.createdAt <= :endTime)")
    Page<User> findByConditions(@Param("keyword") String keyword,
                                @Param("role") Role role,
                                @Param("status") Status status,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime,
                                Pageable pageable);

    long countByIdIn(List<Long> ids);

    @Modifying
    @Query("UPDATE User u SET u.status = :status, u.banReason = :banReason, u.bannedAt = :bannedAt, u.bannedBy = :bannedBy WHERE u.id IN :ids")
    int updateStatusByIds(@Param("ids") List<Long> ids, @Param("status") Status status, @Param("banReason") String banReason, @Param("bannedAt") LocalDateTime bannedAt, @Param("bannedBy") Long bannedBy);
}
