package com.csust.backend.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
public class RateLimiterService {

    private static final String REDIS_KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> tryConsumeScript;

    public RateLimiterService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.tryConsumeScript = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, Long.class);
    }

    public boolean tryConsume(String key, int capacity, int refillCount, Duration duration) {
        Long result = stringRedisTemplate.execute(
                tryConsumeScript,
                Collections.singletonList(REDIS_KEY_PREFIX + key),
                String.valueOf(capacity),
                String.valueOf(refillCount),
                String.valueOf(duration.toMillis()),
                String.valueOf(System.currentTimeMillis())
        );
        return result != null && result == 1L;
    }

    /**
     * Lua 令牌桶脚本：
     * KEYS[1] — 限流 key
     * ARGV[1] — 桶容量 (capacity)
     * ARGV[2] — 补充令牌数 (refillCount)
     * ARGV[3] — 补充周期毫秒 (refillDurationMs)
     * ARGV[4] — 当前时间毫秒 (nowMs)
     *
     * 返回 1 表示消费成功，0 表示被限流
     */
    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refillCount = tonumber(ARGV[2])
            local refillDurationMs = tonumber(ARGV[3])
            local nowMs = tonumber(ARGV[4])
            
            local data = redis.call('HMGET', key, 'tokens', 'last_refill_ms')
            local tokens = tonumber(data[1])
            local lastRefillMs = tonumber(data[2])
            
            if tokens == nil then
                -- 首次访问，初始化桶
                tokens = capacity - 1
                lastRefillMs = nowMs
                redis.call('HMSET', key, 'tokens', tokens, 'last_refill_ms', lastRefillMs)
                redis.call('PEXPIRE', key, refillDurationMs * 2)
                return 1
            end
            
            -- 计算上次补充到现在的间隔，补充令牌
            local elapsed = nowMs - lastRefillMs
            if elapsed > 0 then
                local refillTokens = math.floor(elapsed / refillDurationMs) * refillCount
                if refillTokens > 0 then
                    tokens = math.min(capacity, tokens + refillTokens)
                    lastRefillMs = lastRefillMs + math.floor(elapsed / refillDurationMs) * refillDurationMs
                end
            end
            
            if tokens > 0 then
                tokens = tokens - 1
                redis.call('HMSET', key, 'tokens', tokens, 'last_refill_ms', lastRefillMs)
                redis.call('PEXPIRE', key, refillDurationMs * 2)
                return 1
            end
            
            -- 被限流，但仍续期 key
            redis.call('PEXPIRE', key, refillDurationMs * 2)
            return 0
            """;
}
