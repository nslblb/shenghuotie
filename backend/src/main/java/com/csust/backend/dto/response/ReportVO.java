package com.csust.backend.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportVO {
    private Long id;
    private Long reporterId;
    private String reporterName;
    private String reporterAvatar;
    private Long targetId;
    private String targetType;
    private String reason;
    private String status;
    private Long handlerId;
    private String handlerName;
    private String handleResult;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;

    private String targetTitle;
}
