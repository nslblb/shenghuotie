package com.csust.backend.service;

import com.csust.backend.dto.response.AdminLoginLogVO;
import com.csust.backend.dto.response.OperationLogVO;
import com.csust.backend.dto.response.SystemConfigVO;
import com.csust.backend.dto.response.CacheStatusVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Map;

public interface SystemService {

    void recordAdminLogin(Long userId, String username, String ip, String userAgent, boolean success, String failReason);

    Page<AdminLoginLogVO> getAdminLoginLogs(Pageable pageable, Long userId, String status, LocalDateTime startTime, LocalDateTime endTime);

    Page<OperationLogVO> getOperationLogs(Pageable pageable, Long userId, String operation, String status, LocalDateTime startTime, LocalDateTime endTime);

    OperationLogVO.Stats getOperationLogStats();

    SystemConfigVO.AllConfigs getAllConfigs();

    void updateConfig(String key, String value, Long updatedBy);

    void updateConfigs(Map<String, String> configs, Long updatedBy);

    CacheStatusVO getCacheStatus();

    void clearCache(String pattern);

    void clearAllCache();
}
