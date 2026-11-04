package com.csust.backend.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class ReportReasonStatsVO {
    private String reason;
    private Integer count;
    private Double percentage;

    @Data
    public static class StatsResponse {
        private Integer totalReports;
        private Integer pendingReports;
        private Integer resolvedReports;
        private Integer rejectedReports;
        private List<ReportReasonStatsVO> reasonStats;
    }
}
