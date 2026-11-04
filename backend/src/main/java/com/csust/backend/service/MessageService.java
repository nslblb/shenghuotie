package com.csust.backend.service;

import com.csust.backend.dto.response.ConversationVO;
import com.csust.backend.dto.response.MessageVO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface MessageService {

    MessageVO sendMessage(Long senderId, Long receiverId, String content, String messageType);

    Page<MessageVO> getConversation(Long userId, Long partnerId, int page, int size);

    List<ConversationVO> getConversations(Long userId);

    void markConversationRead(Long userId, Long partnerId);

    long getUnreadCount(Long userId);

    void deleteConversation(Long userId, Long partnerId);

    void deleteMessages(List<Long> ids, Long userId);

    void recallMessage(Long messageId, Long senderId);
}
