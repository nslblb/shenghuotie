package com.csust.backend.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {

    private Long id;
    private Long senderId;
    private Long receiverId;
    private String content;
    private String messageType;
    private Boolean isRead;
    private Boolean isRecalled;
    private LocalDateTime createdAt;
}
