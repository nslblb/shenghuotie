package com.csust.backend.service;

import com.csust.backend.dto.response.ReportDetailVO;
import com.csust.backend.dto.response.ReportGroupVO;
import com.csust.backend.dto.response.ReportReasonStatsVO;
import com.csust.backend.dto.response.ReportVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReportService {

    void createReport(Long reporterId, Long targetId, String targetType, String reason);

    Page<ReportVO> getReports(Pageable pageable, String status);

    void handleReport(Long reportId, Long handlerId, String status, String handleResult);

    ReportDetailVO getReportDetail(Long reportId);

    void handleReportWithAction(Long reportId, Long handlerId, String status, String handleResult, String action);

    Page<ReportGroupVO> getGroupedReports(Pageable pageable, String status, String targetType);

    ReportReasonStatsVO.StatsResponse getReportStats();

    void processAutoHandleRules();

    List<AutoHandleRuleVO> getAutoHandleRules();

    AutoHandleRuleVO createAutoHandleRule(CreateAutoHandleRuleRequest request);

    AutoHandleRuleVO updateAutoHandleRule(Long id, CreateAutoHandleRuleRequest request);

    void deleteAutoHandleRule(Long id);

    void deleteReport(Long reportId);

    record AutoHandleRuleVO(
            Long id,
            String targetType,
            Integer reportThreshold,
            String action,
            Integer timeWindowHours,
            Boolean enabled
    ) {}

    record CreateAutoHandleRuleRequest(
            String targetType,
            Integer reportThreshold,
            String action,
            Integer timeWindowHours,
            Boolean enabled
    ) {}
}
