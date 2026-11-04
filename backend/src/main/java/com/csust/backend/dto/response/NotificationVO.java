package com.csust.backend.dto.response;

import com.csust.backend.entity.Notification;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationVO {

    private Long id;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String type;
    private Long postId;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationVO fromEntity(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setSenderId(n.getSenderId());
        vo.setType(n.getType());
        vo.setPostId(n.getPostId());
        vo.setContent(n.getContent());
        vo.setIsRead(n.getIsRead());
        vo.setCreatedAt(n.getCreatedAt());
        return vo;
    }
}
