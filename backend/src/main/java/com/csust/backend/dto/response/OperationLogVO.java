package com.csust.backend.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OperationLogVO {
    private Long id;
    private Long userId;
    private String username;
    private String operation;
    private String method;
    private String params;
    private String url;
    private String ip;
    private Long duration;
    private String status;
    private String errorMsg;
    private LocalDateTime createdAt;

    @Data
    public static class Stats {
        private Long total;
        private Long successCount;
        private Long failCount;
        private Double avgDuration;
        private List<OperationCount> topOperations;
    }

    @Data
    public static class OperationCount {
        private String operation;
        private Long count;
    }
}
