package com.csust.backend.service;

import com.csust.backend.common.BusinessException;
import com.csust.backend.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 登录保护服务：账号锁定 + 验证码触发
 * 连续失败5次锁定30分钟，连续失败3次后需要验证码
 */
@Service
@RequiredArgsConstructor
public class LoginProtectionService {

    private static final String FAIL_COUNT_PREFIX = "shenghuotie:login_fail:";
    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_MINUTES = 30;

    private final RedisCacheService redisCacheService;

    /**
     * 检查账号是否被锁定，锁定则抛异常
     */
    public void checkLock(String phone) {
        String key = FAIL_COUNT_PREFIX + phone;
        Integer failCount = redisCacheService.get(key);
        if (failCount != null && failCount >= MAX_FAIL_COUNT) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                    "登录失败次数过多，账号已锁定，请" + LOCK_MINUTES + "分钟后重试");
        }
    }

    /**
     * 记录一次登录失败
     */
    public void recordFail(String phone) {
        String key = FAIL_COUNT_PREFIX + phone;
        Long count = redisCacheService.increment(key);
        if (count != null && count == 1) {
            // 第一次失败，设置30分钟过期
            redisCacheService.expire(key, LOCK_MINUTES, TimeUnit.MINUTES);
        }
    }

    /**
     * 登录成功，清除失败计数
     */
    public void clearFail(String phone) {
        redisCacheService.evict(FAIL_COUNT_PREFIX + phone);
    }

    /**
     * 获取当前失败次数
     */
    public int getFailCount(String phone) {
        String key = FAIL_COUNT_PREFIX + phone;
        Integer count = redisCacheService.get(key);
        return count != null ? count : 0;
    }
}
