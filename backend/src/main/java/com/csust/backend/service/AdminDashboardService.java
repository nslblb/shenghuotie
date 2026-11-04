package com.csust.backend.service;

import com.csust.backend.dto.response.DashboardStatsVO;
import com.csust.backend.dto.response.DashboardVO;
import com.csust.backend.dto.response.HotTopicVO;
import com.csust.backend.dto.response.TrendDataVO;

import java.util.List;

public interface AdminDashboardService {

    DashboardVO getDashboard();

    TrendDataVO getTrends(int days);

    List<HotTopicVO> getHotTopics();

    DashboardStatsVO getDashboardStats();
}
