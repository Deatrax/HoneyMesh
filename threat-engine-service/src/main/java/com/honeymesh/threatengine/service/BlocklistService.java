package com.honeymesh.threatengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class BlocklistService {

    private static final Logger log = LoggerFactory.getLogger(BlocklistService.class);

    public static final String BLOCK_KEY_PREFIX = "honeymesh:block:";
    public static final long DEFAULT_BLOCK_TTL_SECONDS = 300; // 5-minute block TTL

    private final StringRedisTemplate redisTemplate;

    public BlocklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Stores a temporary block entry in Redis for the given source IP with default 300-second TTL.
     * If the IP is already blocked, this call atomically refreshes the 300-second block TTL.
     */
    public void block(String sourceIp) {
        block(sourceIp, Duration.ofSeconds(DEFAULT_BLOCK_TTL_SECONDS));
    }

    /**
     * Stores a temporary block entry in Redis for the given source IP with a custom TTL duration.
     * Uses atomic SET key value EX duration to ensure value and expiry are set together.
     */
    public void block(String sourceIp, Duration duration) {
        if (sourceIp == null || sourceIp.isBlank()) {
            log.warn("Cannot block null or blank source IP");
            return;
        }

        String key = BLOCK_KEY_PREFIX + sourceIp.trim();
        // Atomic SET key true EX duration
        redisTemplate.opsForValue().set(key, "true", duration);
        log.info("Blocked source IP {} for {} seconds in Redis (key: {})", sourceIp, duration.getSeconds(), key);
    }

    /**
     * Checks if a source IP is currently blocked in Redis.
     */
    public boolean isBlocked(String sourceIp) {
        if (sourceIp == null || sourceIp.isBlank()) {
            return false;
        }
        String key = BLOCK_KEY_PREFIX + sourceIp.trim();
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Returns the remaining TTL in seconds for a blocked source IP.
     * Returns -2 if key does not exist, -1 if key exists without TTL.
     */
    public long getRemainingTtlSeconds(String sourceIp) {
        if (sourceIp == null || sourceIp.isBlank()) {
            return -2;
        }
        String key = BLOCK_KEY_PREFIX + sourceIp.trim();
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -2;
    }
}
