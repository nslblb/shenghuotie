package com.csust.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCacheService {

    private static final String NULL_CACHE_VALUE = "NULL_CACHE";
    private static final String LOCK_PREFIX = "shenghuotie:lock:";
    private static final long LOCK_WAIT_TIMEOUT = 3;
    private static final long LOCK_EXPIRE_SECONDS = 10;

    private final RedisTemplate<String, Object> redisTemplate;

    public <T> T getWithMutex(String key, long baseTTLSeconds, Supplier<T> dbLoader) {
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_CACHE_VALUE.equals(cached.toString())) {
                return null;
            }
            @SuppressWarnings("unchecked")
            T result = (T) cached;
            return result;
        }

        String lockKey = LOCK_PREFIX + key;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(locked)) {
            try {
                cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    if (NULL_CACHE_VALUE.equals(cached.toString())) {
                        return null;
                    }
                    @SuppressWarnings("unchecked")
                    T result = (T) cached;
                    return result;
                }

                T data = dbLoader.get();
                if (data != null) {
                    long randomTTL = baseTTLSeconds + ThreadLocalRandom.current().nextLong(0, baseTTLSeconds / 5);
                    redisTemplate.opsForValue().set(key, data, randomTTL, TimeUnit.SECONDS);
                } else {
                    long nullTTL = 60 + ThreadLocalRandom.current().nextLong(0, 30);
                    redisTemplate.opsForValue().set(key, NULL_CACHE_VALUE, nullTTL, TimeUnit.SECONDS);
                }
                return data;
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // 未抢到锁，自旋等待缓存填充（最多等待 ~500ms）
            for (int i = 0; i < 10; i++) {
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    if (NULL_CACHE_VALUE.equals(cached.toString())) {
                        return null;
                    }
                    @SuppressWarnings("unchecked")
                    T result = (T) cached;
                    return result;
                }
            }
            // 仍无缓存，兜底查库
            T data = dbLoader.get();
            if (data != null) {
                long randomTTL = baseTTLSeconds + ThreadLocalRandom.current().nextLong(0, baseTTLSeconds / 5);
                redisTemplate.opsForValue().set(key, data, randomTTL, TimeUnit.SECONDS);
            } else {
                long nullTTL = 60 + ThreadLocalRandom.current().nextLong(0, 30);
                redisTemplate.opsForValue().set(key, NULL_CACHE_VALUE, nullTTL, TimeUnit.SECONDS);
            }
            return data;
        }
    }

    public void set(String key, Object value, long baseTTLSeconds) {
        if (value != null) {
            long randomTTL = baseTTLSeconds + ThreadLocalRandom.current().nextLong(0, baseTTLSeconds / 5);
            redisTemplate.opsForValue().set(key, value, randomTTL, TimeUnit.SECONDS);
        } else {
            long nullTTL = 60 + ThreadLocalRandom.current().nextLong(0, 30);
            redisTemplate.opsForValue().set(key, NULL_CACHE_VALUE, nullTTL, TimeUnit.SECONDS);
        }
    }

    public void evict(String key) {
        redisTemplate.delete(key);
    }

    public void evictByPattern(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    if (keys.size() >= 500) {
                        redisTemplate.delete(keys);
                        keys.clear();
                    }
                }
            }
            return null;
        });
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 使用 SCAN 统计匹配前缀的 Key 数量，避免 KEYS 命令阻塞 Redis
     */
    public long countByPattern(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        Long count = redisTemplate.execute((RedisCallback<Long>) connection -> {
            long c = 0;
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    cursor.next();
                    c++;
                }
            }
            return c;
        });
        return count != null ? count : 0L;
    }

    /**
     * 使用 SCAN 扫描匹配前缀的 Key，返回最多 limit 个
     */
    public Set<String> scanByPattern(String pattern, int limit) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext() && keys.size() < limit) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        return keys;
    }

    public <T> T get(String key) {
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null || NULL_CACHE_VALUE.equals(cached.toString())) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T result = (T) cached;
        return result;
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 原子递增，key 不存在时从 0 开始，返回递增后的值
     */
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Long incrementBy(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 设置 key 的过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }
}
