package com.csust.backend.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AppealVO {
    private Long id;
    private Long postId;
    private Long userId;
    private String userName;
    private String userAvatar;
    private String postTitle;
    private String postContent;
    private String postCoverImage;
    private String reason;
    private String banReason;
    private String status;
    private Long handlerId;
    private String handlerName;
    private String handleNote;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;

    private List<AppealHistoryItem> history;

    @Data
    public static class AppealHistoryItem {
        private Long id;
        private String reason;
        private String status;
        private String handleNote;
        private String handlerName;
        private LocalDateTime createdAt;
        private LocalDateTime handledAt;
    }
}
