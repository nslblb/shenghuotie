package com.csust.backend.service.impl;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import com.csust.backend.common.XssUtil;
import com.csust.backend.dto.response.ConversationVO;
import com.csust.backend.dto.response.MessageVO;
import com.csust.backend.entity.Message;
import com.csust.backend.entity.User;
import com.csust.backend.repository.MessageRepository;
import com.csust.backend.repository.UserRepository;
import com.csust.backend.repository.FollowRepository;
import com.csust.backend.service.AntiSpamService;
import com.csust.backend.service.MessageService;
import com.csust.backend.service.RedisCacheService;
import com.csust.backend.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private static final String CACHE_UNREAD_COUNT = "shenghuotie:cache:message:unread:";
    private static final long UNREAD_COUNT_TTL = 120;
    private static final String CACHE_USER_INFO = "shenghuotie:cache:user:info:";
    private static final long USER_INFO_TTL = 300;
    private static final String CACHE_CONVERSATIONS = "shenghuotie:cache:message:conversations:";
    private static final long CONVERSATIONS_TTL = 60;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisCacheService redisCacheService;
    private final AntiSpamService antiSpamService;
    private final SensitiveWordService sensitiveWordService;

    @Value("${app.upload.dir}")
    private String uploadDir;

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
    public MessageVO sendMessage(Long senderId, Long receiverId, String content, String messageType) {
        // 灌水防护：新用户限制 + 重复内容检测
        antiSpamService.checkNewUserMessageLimit(senderId);
        antiSpamService.checkMessageDuplicate(senderId, receiverId, content);

        // 敏感词拦截
        if (sensitiveWordService.containsSensitiveWord(content)) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD);
        }

        // 检查接收者私信隐私设置
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String privacy = receiver.getMessagePrivacy();
        if ("NONE".equals(privacy)) {
            throw new BusinessException(ErrorCode.MESSAGE_PRIVACY_BLOCKED);
        }
        if ("FOLLOWERS_ONLY".equals(privacy)) {
            if (!followRepository.existsByFollowerIdAndFollowingId(senderId, receiverId)) {
                throw new BusinessException(ErrorCode.MESSAGE_PRIVACY_FOLLOWERS_ONLY);
            }
        }

        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(XssUtil.clean(content));
        message.setMessageType(messageType != null ? messageType : "TEXT");
        messageRepository.save(message);

        // 清除接收者未读数缓存，下次查询时重新计算
        redisCacheService.evict(CACHE_UNREAD_COUNT + receiverId);
        // 清除双方会话列表缓存
        redisCacheService.evict(CACHE_CONVERSATIONS + receiverId);
        redisCacheService.evict(CACHE_CONVERSATIONS + senderId);

        MessageVO vo = toVO(message);

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", vo);

        messagingTemplate.convertAndSendToUser(
                receiverId.toString(),
                "/topic/messages",
                payload
        );

        Map<String, Object> senderPayload = new HashMap<>();
        senderPayload.put("message", vo);
        messagingTemplate.convertAndSendToUser(
                senderId.toString(),
                "/topic/messages",
                senderPayload
        );

        return vo;
    }

    @Override
    public Page<MessageVO> getConversation(Long userId, Long partnerId, int page, int size) {
        Page<Message> messages = messageRepository.findConversation(userId, partnerId, PageRequest.of(page, size));
        return messages.map(this::toVO);
    }

    @Override
    public List<ConversationVO> getConversations(Long userId) {
        String cacheKey = CACHE_CONVERSATIONS + userId;
        try {
            Object cached = redisCacheService.get(cacheKey);
            if (cached instanceof List) {
                @SuppressWarnings("unchecked")
                List<ConversationVO> result = (List<ConversationVO>) cached;
                return result;
            }
        } catch (Exception e) {
            // 缓存反序列化失败，清除旧缓存继续查库
            redisCacheService.evict(cacheKey);
        }

        List<Message> latestMessages = messageRepository.findLatestMessagesPerConversation(userId);

        List<ConversationVO> conversations = latestMessages.stream().map(msg -> {
            Long partnerId = msg.getSenderId().equals(userId) ? msg.getReceiverId() : msg.getSenderId();
            ConversationVO vo = new ConversationVO();
            vo.setPartnerId(partnerId);
            vo.setLastMessage(msg.getContent());
            vo.setLastMessageType(msg.getMessageType() != null ? msg.getMessageType() : "TEXT");
            vo.setLastMessageRecalled(msg.getIsRecalled());
            vo.setLastMessageTime(msg.getCreatedAt());

            List<String> partnerInfo = getCachedUserInfo(partnerId);
            if (partnerInfo != null && !partnerInfo.isEmpty()) {
                vo.setPartnerName(partnerInfo.get(0));
                vo.setPartnerAvatar(partnerInfo.get(1));
            }

            vo.setUnreadCount(messageRepository.countUnreadFromPartner(userId, partnerId));
            return vo;
        }).collect(Collectors.toCollection(ArrayList::new));

        redisCacheService.set(cacheKey, conversations, CONVERSATIONS_TTL);
        return conversations;
    }

    @Override
    @Transactional
    public void markConversationRead(Long userId, Long partnerId) {
        messageRepository.markConversationRead(userId, partnerId);
        // 清除未读数缓存和会话列表缓存
        redisCacheService.evict(CACHE_UNREAD_COUNT + userId);
        redisCacheService.evict(CACHE_CONVERSATIONS + userId);
    }

    @Override
    public long getUnreadCount(Long userId) {
        String cacheKey = CACHE_UNREAD_COUNT + userId;
        Object cached = redisCacheService.get(cacheKey);
        if (cached != null) {
            return ((Number) cached).longValue();
        }
        long count = messageRepository.countUnreadByUserId(userId);
        redisCacheService.set(cacheKey, count, UNREAD_COUNT_TTL);
        return count;
    }

    private MessageVO toVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setSenderId(message.getSenderId());
        vo.setReceiverId(message.getReceiverId());
        vo.setContent(message.getContent());
        vo.setMessageType(message.getMessageType() != null ? message.getMessageType() : "TEXT");
        vo.setIsRead(message.getIsRead());
        vo.setIsRecalled(message.getIsRecalled());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

    @Override
    @Transactional
    public void deleteConversation(Long userId, Long partnerId) {
        messageRepository.softDeleteAsSender(userId, partnerId);
        messageRepository.softDeleteAsReceiver(userId, partnerId);
        
        List<Message> bothDeleted = messageRepository.findBothDeletedMessages(userId, partnerId);
        if (!bothDeleted.isEmpty()) {
            for (Message msg : bothDeleted) {
                if ("IMAGE".equals(msg.getMessageType()) && msg.getContent() != null) {
                    deleteImageFile(msg.getContent());
                }
            }
            List<Long> ids = bothDeleted.stream().map(Message::getId).collect(Collectors.toList());
            messageRepository.deleteByIds(ids);
        }
        
        redisCacheService.evict(CACHE_UNREAD_COUNT + userId);
        redisCacheService.evict(CACHE_CONVERSATIONS + userId);
    }

    @Override
    @Transactional
    public void deleteMessages(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) return;
        
        messageRepository.softDeleteByIdsAsSender(ids, userId);
        messageRepository.softDeleteByIdsAsReceiver(ids, userId);
        
        List<Message> messages = messageRepository.findAllByIds(ids);
        List<Long> bothDeletedIds = new ArrayList<>();
        for (Message msg : messages) {
            if (Boolean.TRUE.equals(msg.getDeletedBySender()) && Boolean.TRUE.equals(msg.getDeletedByReceiver())) {
                if ("IMAGE".equals(msg.getMessageType()) && msg.getContent() != null) {
                    deleteImageFile(msg.getContent());
                }
                bothDeletedIds.add(msg.getId());
            }
        }
        
        if (!bothDeletedIds.isEmpty()) {
            messageRepository.deleteByIds(bothDeletedIds);
        }
        
        redisCacheService.evict(CACHE_UNREAD_COUNT + userId);
        redisCacheService.evict(CACHE_CONVERSATIONS + userId);
    }

    private void deleteImageFile(String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            return;
        }
        String fileName = url.substring("/uploads/".length());
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return;
        }
        try {
            Path filePath = Paths.get(uploadDir).resolve(fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // ignore
        }
    }

    @Override
    @Transactional
    public void recallMessage(Long messageId, Long senderId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        
        if (!message.getSenderId().equals(senderId)) {
            throw new BusinessException(ErrorCode.NOT_YOUR_MESSAGE);
        }
        
        if (message.getIsRecalled()) {
            throw new BusinessException(ErrorCode.MESSAGE_ALREADY_RECALLED);
        }
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime createdAt = message.getCreatedAt();
        long minutesDiff = java.time.Duration.between(createdAt, now).toMinutes();
        
        if (minutesDiff > 2) {
            throw new BusinessException(ErrorCode.MESSAGE_RECALL_TIMEOUT);
        }
        
        int updated = messageRepository.recallMessage(messageId, senderId);
        if (updated > 0) {
            message.setIsRecalled(true);

            // 清除双方会话缓存，使私信列表不再展示已撤回的消息内容
            redisCacheService.evict(CACHE_CONVERSATIONS + senderId);
            redisCacheService.evict(CACHE_CONVERSATIONS + message.getReceiverId());

            MessageVO vo = toVO(message);
            Map<String, Object> payload = new HashMap<>();
            payload.put("message", vo);
            
            messagingTemplate.convertAndSendToUser(
                    message.getReceiverId().toString(),
                    "/topic/messages",
                    payload
            );
            messagingTemplate.convertAndSendToUser(
                    senderId.toString(),
                    "/topic/messages",
                    payload
            );
        }
    }
}
