package com.csust.backend.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReportGroupVO {
    private Long targetId;
    private String targetType;
    private String targetTitle;
    private String targetContent;
    private String targetAuthorName;
    private String targetStatus;
    private Integer reportCount;
    private LocalDateTime latestReportTime;
    private LocalDateTime earliestReportTime;
    private List<String> reasons;
    private List<ReportHistoryItem> reports;

    @Data
    public static class ReportHistoryItem {
        private Long id;
        private Long reporterId;
        private String reporterName;
        private String reporterAvatar;
        private String reason;
        private String status;
        private LocalDateTime createdAt;
    }
}
