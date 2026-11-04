package com.csust.backend.service;

import com.csust.backend.dto.response.NotificationVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface NotificationService {

    void createNotification(Long receiverId, Long senderId, String type, Long postId, String content);

    Page<NotificationVO> getUserNotifications(Long receiverId, String type, Pageable pageable);

    void markAllAsRead(Long receiverId);

    void markAsRead(Long notificationId, Long receiverId);

    long getUnreadCount(Long receiverId);

    void deleteNotifications(List<Long> ids, Long receiverId);

    void deleteAllNotifications(Long receiverId);

    Map<String, Long> getUnreadCountByType(Long receiverId);
}
