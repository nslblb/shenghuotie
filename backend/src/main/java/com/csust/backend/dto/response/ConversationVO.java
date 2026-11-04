package com.csust.backend.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long partnerId;
    private String partnerName;
    private String partnerAvatar;
    private String lastMessage;
    private String lastMessageType;
    private boolean lastMessageRecalled;
    private LocalDateTime lastMessageTime;
    private long unreadCount;
}
