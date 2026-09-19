package com.honeymesh.threatengine.service;

import com.honeymesh.threatengine.event.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@Service
public class TelemetryIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIdempotencyService.class);

    public static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    public static final Duration COMPLETED_TTL = Duration.ofHours(24);

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_DONE = "DONE";
    public static final String KEY_PREFIX = "honeymesh:processed:telemetry:";

    private final StringRedisTemplate redisTemplate;

    public TelemetryIdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryClaim(TelemetryEvent event) {
        if (event == null) {
            return false;
        }
        String fingerprint = generateFingerprint(event);
        String key = getKey(fingerprint);

        Boolean setSuccess = redisTemplate.opsForValue().setIfAbsent(key, STATUS_PROCESSING, PROCESSING_TTL);
        boolean acquired = Boolean.TRUE.equals(setSuccess);

        if (!acquired) {
            log.info("Duplicate telemetry event detected for fingerprint={}. Skipping execution.", fingerprint);
        }
        return acquired;
    }

    public void markCompleted(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        String fingerprint = generateFingerprint(event);
        String key = getKey(fingerprint);
        redisTemplate.opsForValue().set(key, STATUS_DONE, COMPLETED_TTL);
    }

    public void releaseClaim(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        String fingerprint = generateFingerprint(event);
        String key = getKey(fingerprint);
        redisTemplate.delete(key);
    }

    public String generateFingerprint(TelemetryEvent event) {
        if (event == null) {
            return "";
        }
        String canonical = String.format("%s|%s|%s|%s|%s",
                event.decoyId() != null ? event.decoyId() : "",
                event.sourceIp() != null ? event.sourceIp() : "",
                event.endpoint() != null ? event.endpoint() : "",
                event.riskLevel() != null ? event.riskLevel().name() : "",
                event.occurredAt() != null ? event.occurredAt().toString() : "");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public String getKey(String fingerprint) {
        return KEY_PREFIX + fingerprint;
    }

    public String getStatus(TelemetryEvent event) {
        if (event == null)
            return null;
        return redisTemplate.opsForValue().get(getKey(generateFingerprint(event)));
    }

    public Long getTtlSeconds(TelemetryEvent event) {
        if (event == null)
            return -2L;
        return redisTemplate.getExpire(getKey(generateFingerprint(event)));
    }
}
