package com.csust.backend.service.impl;

import com.csust.backend.dto.response.NotificationVO;
import com.csust.backend.entity.Notification;
import com.csust.backend.entity.User;
import com.csust.backend.repository.NotificationRepository;
import com.csust.backend.repository.UserRepository;
import com.csust.backend.service.NotificationService;
import com.csust.backend.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String CACHE_UNREAD_COUNT = "shenghuotie:cache:notification:unread:";
    private static final long UNREAD_COUNT_TTL = 120;
    private static final String CACHE_USER_INFO = "shenghuotie:cache:user:info:";
    private static final long USER_INFO_TTL = 300;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RedisCacheService redisCacheService;
    private final SimpMessagingTemplate messagingTemplate;

    @SuppressWarnings("unchecked")
    private List<String> getCachedUserInfo(Long userId) {
        String cacheKey = CACHE_USER_INFO + userId;
        Object cached = redisCacheService.get(cacheKey);
        if (cached instanceof List) {
            return (List<String>) cached;
        }
        List<String> info = userRepository.findById(userId)
                .map(user -> List.of(
                        user.getNickname() != null ? user.getNickname() : "",
                        user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
                ))
                .orElse(null);
        if (info != null) {
            redisCacheService.set(cacheKey, new ArrayList<>(info), USER_INFO_TTL);
        }
        return info;
    }

    @Override
    @Transactional
    public void createNotification(Long receiverId, Long senderId, String type, Long postId, String content) {
        // SYSTEM 类型通知由系统发送(senderId=0)，不跳过；其他类型自己触发自己则跳过
        if (!"SYSTEM".equals(type) && receiverId.equals(senderId)) return;

        Notification notification = new Notification();
        notification.setReceiverId(receiverId);
        notification.setSenderId(senderId);
        notification.setType(type);
        notification.setPostId(postId);
        notification.setContent(content);
        notificationRepository.save(notification);

        redisCacheService.evict(CACHE_UNREAD_COUNT + receiverId);

        long unreadCount = notificationRepository.countByReceiverIdAndIsReadFalse(receiverId);

        NotificationVO vo = NotificationVO.fromEntity(notification);
        if (!"SYSTEM".equals(type)) {
            List<String> senderInfo = getCachedUserInfo(senderId);
            if (senderInfo != null && !senderInfo.isEmpty()) {
                vo.setSenderName(senderInfo.get(0));
                vo.setSenderAvatar(senderInfo.get(1));
            }
        } else {
            vo.setSenderName("系统通知");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("unreadCount", unreadCount);
        payload.put("notification", vo);

        messagingTemplate.convertAndSendToUser(
                receiverId.toString(),
                "/topic/notifications",
                payload
        );
    }

    @Override
    public Page<NotificationVO> getUserNotifications(Long receiverId, String type, Pageable pageable) {
        Page<Notification> page;
        if (type != null && !type.isEmpty()) {
            page = notificationRepository.findByReceiverIdAndTypeOrderByCreatedAtDesc(receiverId, type, pageable);
        } else {
            page = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(receiverId, pageable);
        }
        return page.map(n -> {
            NotificationVO vo = NotificationVO.fromEntity(n);
            if ("SYSTEM".equals(n.getType())) {
                vo.setSenderName("系统通知");
            } else {
                List<String> senderInfo = getCachedUserInfo(n.getSenderId());
                if (senderInfo != null && !senderInfo.isEmpty()) {
                    vo.setSenderName(senderInfo.get(0));
                    vo.setSenderAvatar(senderInfo.get(1));
                }
            }
            return vo;
        });
    }

    @Override
    @Transactional
    public void markAllAsRead(Long receiverId) {
        List<Notification> unread = notificationRepository.findByReceiverIdAndIsReadFalse(receiverId);
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);

        redisCacheService.evict(CACHE_UNREAD_COUNT + receiverId);

        messagingTemplate.convertAndSendToUser(
                receiverId.toString(),
                "/topic/notifications",
                Map.of("unreadCount", 0)
        );
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long receiverId) {
        Notification notification = notificationRepository.findByIdAndReceiverId(notificationId, receiverId)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在"));
        if (notification.getIsRead()) return;
        notification.setIsRead(true);
        notificationRepository.save(notification);

        redisCacheService.evict(CACHE_UNREAD_COUNT + receiverId);

        long unreadCount = notificationRepository.countByReceiverIdAndIsReadFalse(receiverId);
        messagingTemplate.convertAndSendToUser(
                receiverId.toString(),
                "/topic/notifications",
                Map.of("unreadCount", unreadCount)
        );
    }

    @Override
    public long getUnreadCount(Long receiverId) {
        String cacheKey = CACHE_UNREAD_COUNT + receiverId;
        Object cached = redisCacheService.get(cacheKey);
        if (cached != null) {
            return ((Number) cached).longValue();
        }
        long count = notificationRepository.countByReceiverIdAndIsReadFalse(receiverId);
        redisCacheService.set(cacheKey, count, UNREAD_COUNT_TTL);
        return count;
    }

    @Override
    @Transactional
    public void deleteNotifications(List<Long> ids, Long receiverId) {
        if (ids == null || ids.isEmpty()) return;
        notificationRepository.deleteByIdInAndReceiverId(ids, receiverId);
        redisCacheService.evict(CACHE_UNREAD_COUNT + receiverId);
        long unreadCount = notificationRepository.countByReceiverIdAndIsReadFalse(receiverId);
        messagingTemplate.convertAndSendToUser(
                receiverId.toString(),
                "/topic/notifications",
                Map.of("unreadCount", unreadCount)
        );
    }

    @Override
    @Transactional
    public void deleteAllNotifications(Long receiverId) {
        notificationRepository.deleteAllByReceiverId(receiverId);
        redisCacheService.evict(CACHE_UNREAD_COUNT + receiverId);
        messagingTemplate.convertAndSendToUser(
                receiverId.toString(),
                "/topic/notifications",
                Map.of("unreadCount", 0)
        );
    }

    @Override
    public Map<String, Long> getUnreadCountByType(Long receiverId) {
        Map<String, Long> result = new HashMap<>();
        String[] types = {"LIKE", "COMMENT", "FOLLOW", "REPORT", "APPEAL", "SYSTEM"};
        long total = 0;
        for (String type : types) {
            long count = notificationRepository.countByReceiverIdAndTypeAndIsReadFalse(receiverId, type);
            result.put(type, count);
            total += count;
        }
        result.put("", total);
        return result;
    }
}
