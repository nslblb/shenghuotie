package com.csust.backend.service.impl;

import com.csust.backend.dto.response.AdminLoginLogVO;
import com.csust.backend.dto.response.CacheStatusVO;
import com.csust.backend.dto.response.OperationLogVO;
import com.csust.backend.dto.response.SystemConfigVO;
import com.csust.backend.entity.AdminLoginLog;
import com.csust.backend.entity.OperationLog;
import com.csust.backend.entity.SystemConfig;
import com.csust.backend.repository.AdminLoginLogRepository;
import com.csust.backend.repository.OperationLogRepository;
import com.csust.backend.repository.SystemConfigRepository;
import com.csust.backend.service.RedisCacheService;
import com.csust.backend.service.SystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemServiceImpl implements SystemService {

    private static final String CACHE_KEY_PREFIX = "shenghuotie:cache:*";

    private final AdminLoginLogRepository adminLoginLogRepository;
    private final OperationLogRepository operationLogRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisCacheService redisCacheService;

    @Override
    public void recordAdminLogin(Long userId, String username, String ip, String userAgent, boolean success, String failReason) {
        AdminLoginLog loginLog = new AdminLoginLog();
        loginLog.setUserId(userId);
        loginLog.setUsername(username);
        loginLog.setIp(ip);
        loginLog.setUserAgent(userAgent);
        loginLog.setStatus(success ? "SUCCESS" : "FAIL");
        loginLog.setFailReason(failReason);

        parseUserAgent(userAgent, loginLog);

        adminLoginLogRepository.save(loginLog);
    }

    private void parseUserAgent(String userAgent, AdminLoginLog loginLog) {
        if (userAgent == null) return;

        String lowerUA = userAgent.toLowerCase();

        if (lowerUA.contains("edge") || lowerUA.contains("edg/")) {
            loginLog.setBrowser("Edge");
        } else if (lowerUA.contains("chrome")) {
            loginLog.setBrowser("Chrome");
        } else if (lowerUA.contains("firefox")) {
            loginLog.setBrowser("Firefox");
        } else if (lowerUA.contains("safari") && !lowerUA.contains("chrome")) {
            loginLog.setBrowser("Safari");
        } else if (lowerUA.contains("opera") || lowerUA.contains("opr/")) {
            loginLog.setBrowser("Opera");
        } else if (lowerUA.contains("msie") || lowerUA.contains("trident")) {
            loginLog.setBrowser("IE");
        } else {
            loginLog.setBrowser("Unknown");
        }

        if (lowerUA.contains("windows")) {
            loginLog.setOs("Windows");
        } else if (lowerUA.contains("mac")) {
            loginLog.setOs("MacOS");
        } else if (lowerUA.contains("linux")) {
            loginLog.setOs("Linux");
        } else if (lowerUA.contains("android")) {
            loginLog.setOs("Android");
        } else if (lowerUA.contains("iphone") || lowerUA.contains("ipad")) {
            loginLog.setOs("iOS");
        } else {
            loginLog.setOs("Unknown");
        }
    }

    @Override
    public Page<AdminLoginLogVO> getAdminLoginLogs(Pageable pageable, Long userId, String status, LocalDateTime startTime, LocalDateTime endTime) {
        Page<AdminLoginLog> logs = adminLoginLogRepository.findByConditions(userId, status, startTime, endTime, pageable);
        return logs.map(this::toLoginLogVO);
    }

    private AdminLoginLogVO toLoginLogVO(AdminLoginLog log) {
        AdminLoginLogVO vo = new AdminLoginLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setIp(log.getIp());
        vo.setLocation(log.getLocation());
        vo.setUserAgent(log.getUserAgent());
        vo.setBrowser(log.getBrowser());
        vo.setOs(log.getOs());
        vo.setStatus(log.getStatus());
        vo.setFailReason(log.getFailReason());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    @Override
    public Page<OperationLogVO> getOperationLogs(Pageable pageable, Long userId, String operation, String status, LocalDateTime startTime, LocalDateTime endTime) {
        Page<OperationLog> logs = operationLogRepository.findByConditions(userId, operation, status, startTime, endTime, pageable);
        return logs.map(this::toOperationLogVO);
    }

    private OperationLogVO toOperationLogVO(OperationLog log) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setOperation(log.getOperation());
        vo.setMethod(log.getMethod());
        vo.setParams(log.getParams());
        vo.setUrl(log.getUrl());
        vo.setIp(log.getIp());
        vo.setDuration(log.getDuration());
        vo.setStatus(log.getStatus());
        vo.setErrorMsg(log.getErrorMsg());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    @Override
    public OperationLogVO.Stats getOperationLogStats() {
        OperationLogVO.Stats stats = new OperationLogVO.Stats();
        stats.setTotal(operationLogRepository.count());
        stats.setSuccessCount(operationLogRepository.countByStatus("SUCCESS"));
        stats.setFailCount(operationLogRepository.countByStatus("FAIL"));

        // 改用 DB 侧聚合，避免 findAll() 全量加载到内存
        double avg = operationLogRepository.avgDuration();
        stats.setAvgDuration(avg);

        List<Object[]> topOpRows = operationLogRepository.topOperations(
                org.springframework.data.domain.PageRequest.of(0, 10));
        List<OperationLogVO.OperationCount> topOps = new ArrayList<>();
        for (Object[] row : topOpRows) {
            OperationLogVO.OperationCount oc = new OperationLogVO.OperationCount();
            oc.setOperation((String) row[0]);
            oc.setCount(row[1] == null ? 0L : ((Number) row[1]).longValue());
            topOps.add(oc);
        }
        stats.setTopOperations(topOps);

        return stats;
    }

    @Override
    public SystemConfigVO.AllConfigs getAllConfigs() {
        SystemConfigVO.AllConfigs configs = new SystemConfigVO.AllConfigs();

        List<SystemConfig> allConfigs = systemConfigRepository.findAll();
        Map<String, String> configMap = allConfigs.stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, c -> c.getConfigValue() != null ? c.getConfigValue() : ""));

        configs.setSiteName(configMap.getOrDefault("siteName", "生活贴"));
        configs.setSiteDescription(configMap.getOrDefault("siteDescription", "分享生活，记录美好"));
        configs.setAnnouncement(configMap.getOrDefault("announcement", ""));
        configs.setMaxUploadSize(getIntConfig(configMap, "maxUploadSize", 10));
        configs.setMaxPostImages(getIntConfig(configMap, "maxPostImages", 9));
        configs.setEnableRegister(getBoolConfig(configMap, "enableRegister", true));
        configs.setEnableComment(getBoolConfig(configMap, "enableComment", true));
        configs.setEnablePost(getBoolConfig(configMap, "enablePost", true));
        configs.setContactEmail(configMap.getOrDefault("contactEmail", ""));
        configs.setContactWechat(configMap.getOrDefault("contactWechat", ""));

        return configs;
    }

    private Integer getIntConfig(Map<String, String> configMap, String key, Integer defaultValue) {
        String value = configMap.get(key);
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Boolean getBoolConfig(Map<String, String> configMap, String key, Boolean defaultValue) {
        String value = configMap.get(key);
        if (value == null || value.isEmpty()) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    @Override
    @Transactional
    public void updateConfig(String key, String value, Long updatedBy) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseGet(() -> {
                    SystemConfig c = new SystemConfig();
                    c.setConfigKey(key);
                    return c;
                });
        config.setConfigValue(value);
        config.setUpdatedBy(updatedBy);
        systemConfigRepository.save(config);
    }

    @Override
    @Transactional
    public void updateConfigs(Map<String, String> configs, Long updatedBy) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            updateConfig(entry.getKey(), entry.getValue(), updatedBy);
        }
    }

    @Override
    public CacheStatusVO getCacheStatus() {
        CacheStatusVO status = new CacheStatusVO();

        try {
            Properties info = redisTemplate.execute((RedisCallback<Properties>) RedisServerCommands::info);
            if (info != null) {
                status.setConnected(true);

                String usedMemory = info.getProperty("used_memory", "0");
                status.setUsedMemory(Long.parseLong(usedMemory));

                String uptime = info.getProperty("uptime_in_seconds", "0");
                status.setUptime(Long.parseLong(uptime));

                String clients = info.getProperty("connected_clients", "0");
                status.setConnectedClients(Long.parseLong(clients));

                String hits = info.getProperty("keyspace_hits", "0");
                String misses = info.getProperty("keyspace_misses", "0");
                long hitsLong = Long.parseLong(hits);
                long missesLong = Long.parseLong(misses);
                status.setHits(hitsLong);
                status.setMisses(missesLong);

                if (hitsLong + missesLong > 0) {
                    status.setHitRate((double) hitsLong / (hitsLong + missesLong) * 100);
                } else {
                    status.setHitRate(0.0);
                }
            }

            Long dbSize = redisTemplate.execute((RedisCallback<Long>) RedisServerCommands::dbSize);
            status.setTotalKeys(dbSize != null ? dbSize : 0L);

            Set<String> keys = redisCacheService.scanByPattern(CACHE_KEY_PREFIX, 100);
            List<CacheStatusVO.CacheKeyInfo> keyInfos = new ArrayList<>();
            for (String key : keys) {
                CacheStatusVO.CacheKeyInfo keyInfo = new CacheStatusVO.CacheKeyInfo();
                keyInfo.setKey(key);

                try {
                    Long ttl = redisTemplate.getExpire(key);
                    keyInfo.setTtl(ttl);

                    String type = redisTemplate.type(key).code();
                    keyInfo.setType(type);
                } catch (Exception e) {
                    keyInfo.setTtl(-1L);
                    keyInfo.setType("unknown");
                }

                keyInfos.add(keyInfo);
            }
            status.setKeys(keyInfos);

        } catch (Exception e) {
            log.error("获取Redis状态失败", e);
            status.setConnected(false);
        }

        return status;
    }

    @Override
    public void clearCache(String pattern) {
        redisCacheService.evictByPattern(pattern);
    }

    @Override
    public void clearAllCache() {
        // 只删除业务缓存前缀的 Key，保留 token 黑名单、限流计数器、在线状态等系统 Key
        redisCacheService.evictByPattern(CACHE_KEY_PREFIX);
    }
}
