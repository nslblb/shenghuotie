package com.csust.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 后台仪表盘统计数据
 */
@Data
@AllArgsConstructor
public class DashboardVO {

    private long totalUsers;
    private long totalPosts;
    private long totalComments;
    private long totalLikes;
    private long todayNewUsers;
    private long todayNewPosts;
}
