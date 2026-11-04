package com.csust.backend.service.impl;

import com.csust.backend.dto.response.DashboardStatsVO;
import com.csust.backend.dto.response.DashboardVO;
import com.csust.backend.dto.response.HotTopicVO;
import com.csust.backend.dto.response.TrendDataVO;
import com.csust.backend.repository.CommentRepository;
import com.csust.backend.repository.LikeRecordRepository;
import com.csust.backend.repository.PostRepository;
import com.csust.backend.repository.ReportRepository;
import com.csust.backend.repository.UserRepository;
import com.csust.backend.service.AdminDashboardService;
import com.csust.backend.service.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final LikeRecordRepository likeRecordRepository;
    private final ReportRepository reportRepository;
    private final OnlineStatusService onlineStatusService;

    @Override
    public DashboardVO getDashboard() {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        return new DashboardVO(
                userRepository.count(),
                postRepository.count(),
                commentRepository.count(),
                postRepository.sumAllLikeCount(),
                userRepository.countByCreatedAtAfter(todayStart),
                postRepository.countByCreatedAtAfter(todayStart)
        );
    }

    @Override
    public TrendDataVO getTrends(int days) {
        LocalDateTime startDate = LocalDateTime.of(LocalDate.now().minusDays(days - 1), LocalTime.MIN);

        List<Object[]> userCounts = userRepository.countByDateAfter(startDate);
        List<Object[]> postCounts = postRepository.countByDateAfter(startDate);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        List<TrendDataVO.DateCountVO> userTrends = new ArrayList<>();
        List<TrendDataVO.DateCountVO> postTrends = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dateStr = date.format(formatter);
            long userCount = userCounts.stream()
                    .filter(row -> row[0].toString().equals(date.toString()))
                    .mapToLong(row -> ((Number) row[1]).longValue())
                    .findFirst()
                    .orElse(0L);
            long postCount = postCounts.stream()
                    .filter(row -> row[0].toString().equals(date.toString()))
                    .mapToLong(row -> ((Number) row[1]).longValue())
                    .findFirst()
                    .orElse(0L);
            userTrends.add(new TrendDataVO.DateCountVO(dateStr, userCount));
            postTrends.add(new TrendDataVO.DateCountVO(dateStr, postCount));
        }

        return new TrendDataVO(userTrends, postTrends);
    }

    @Override
    public List<HotTopicVO> getHotTopics() {
        List<Object[]> results = postRepository.countGroupByTopic(PageRequest.of(0, 10));
        List<HotTopicVO> topics = new ArrayList<>();
        for (Object[] row : results) {
            topics.add(new HotTopicVO((String) row[0], ((Number) row[1]).longValue()));
        }
        return topics;
    }

    @Override
    public DashboardStatsVO getDashboardStats() {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        long todayComments = commentRepository.countByCreatedAtAfter(todayStart);
        long todayLikes = likeRecordRepository.countByCreatedAtAfter(todayStart);
        long pendingReports = reportRepository.countByStatus("PENDING");
        long pendingAppeals = postRepository.countByStatus("APPEALING");
        long onlineUsers = onlineStatusService.getOnlineCount();

        return new DashboardStatsVO(todayComments, todayLikes, pendingReports, pendingAppeals, onlineUsers);
    }
}
