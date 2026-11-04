package com.csust.backend.repository;

import com.csust.backend.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByReceiverIdOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

    Page<Notification> findByReceiverIdAndTypeOrderByCreatedAtDesc(Long receiverId, String type, Pageable pageable);

    List<Notification> findByReceiverIdAndIsReadFalse(Long receiverId);

    long countByReceiverIdAndIsReadFalse(Long receiverId);

    void deleteByPostId(Long postId);

    Optional<Notification> findByIdAndReceiverId(Long id, Long receiverId);

    void deleteByIdInAndReceiverId(List<Long> ids, Long receiverId);

    void deleteAllByReceiverId(Long receiverId);

    long countByReceiverIdAndTypeAndIsReadFalse(Long receiverId, String type);
}
