package com.csust.backend.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务（Redis 实现）
 * 用户退出登录时将 token 加入黑名单，JWT 过滤器会检查
 * 使用 Redis 存储并设置与 token 过期时间一致的 TTL，自动清理过期条目
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "shenghuotie:token:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtil jwtUtil;

    /**
     * 将 token 加入黑名单，TTL 为 token 剩余有效期
     */
    public void addToBlacklist(String token) {
        try {
            long ttlMs = jwtUtil.getExpirationFromToken(token).getTime() - System.currentTimeMillis();
            if (ttlMs > 0) {
                stringRedisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "1", ttlMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            // token 已过期则无需加入黑名单
            log.debug("Token already expired, skip blacklist: {}", e.getMessage());
        }
    }

    /**
     * 检查 token 是否在黑名单中
     */
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }
}
