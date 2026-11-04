package com.csust.backend.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminLoginLogVO {
    private Long id;
    private Long userId;
    private String username;
    private String ip;
    private String location;
    private String userAgent;
    private String browser;
    private String os;
    private String status;
    private String failReason;
    private LocalDateTime createdAt;
}
