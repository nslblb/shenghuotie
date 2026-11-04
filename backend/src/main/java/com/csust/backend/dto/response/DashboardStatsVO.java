package com.csust.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardStatsVO {

    private long todayComments;
    private long todayLikes;
    private long pendingReports;
    private long pendingAppeals;
    private long onlineUsers;
}
