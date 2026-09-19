package com.honeymesh.threatengine.service;

import com.honeymesh.threatengine.event.TelemetryEvent;
import com.honeymesh.threatengine.model.CorrelationSnapshot;
import com.honeymesh.threatengine.model.RiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class CorrelationService {

    private static final Logger log = LoggerFactory.getLogger(CorrelationService.class);

    private static final String KEY_PREFIX = "honeymesh:correlation:source:";
    private static final long WINDOW_DURATION_MS = 5 * 60 * 1000L;
    private static final Duration KEY_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public CorrelationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public CorrelationSnapshot correlate(TelemetryEvent event) {
        if (event == null || event.sourceIp() == null || event.sourceIp().isBlank()) {
            return new CorrelationSnapshot(0, 0, RiskLevel.LOW);
        }

        String sourceIp = event.sourceIp();
        String redisKey = KEY_PREFIX + sourceIp;

        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        long eventTimeMs = occurredAt.toEpochMilli();

        RiskLevel riskLevel = event.riskLevel() != null ? event.riskLevel() : RiskLevel.LOW;
        String decoyId = event.decoyId() != null ? event.decoyId() : "unknown-decoy";

        String member = decoyId + ":" + riskLevel.name() + ":" + eventTimeMs + ":"
                + UUID.randomUUID().toString().substring(0, 8);

        redisTemplate.opsForZSet().add(redisKey, member, (double) eventTimeMs);

        long windowStartMs = eventTimeMs - WINDOW_DURATION_MS;
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, (double) (windowStartMs - 1));

        Set<String> remainingMembers = redisTemplate.opsForZSet().rangeByScore(redisKey, (double) windowStartMs,
                Double.MAX_VALUE);

        CorrelationSnapshot snapshot = computeSnapshotFromMembers(remainingMembers);

        redisTemplate.expire(redisKey, KEY_TTL);

        log.debug("Correlated source IP {}: recentHits={}, distinctDecoys={}, highestRisk={}",
                sourceIp, snapshot.recentHitCount(), snapshot.distinctDecoyCount(), snapshot.highestRiskLevel());

        return snapshot;
    }

    public CorrelationSnapshot getCorrelationSnapshot(String sourceIp) {
        if (sourceIp == null || sourceIp.isBlank()) {
            return new CorrelationSnapshot(0, 0, RiskLevel.LOW);
        }
        String redisKey = KEY_PREFIX + sourceIp;
        long nowMs = System.currentTimeMillis();
        long windowStartMs = nowMs - WINDOW_DURATION_MS;

        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, (double) (windowStartMs - 1));

        Set<String> remainingMembers = redisTemplate.opsForZSet().rangeByScore(redisKey, (double) windowStartMs,
                Double.MAX_VALUE);

        return computeSnapshotFromMembers(remainingMembers);
    }

    private CorrelationSnapshot computeSnapshotFromMembers(Set<String> members) {
        if (members == null || members.isEmpty()) {
            return new CorrelationSnapshot(0, 0, RiskLevel.LOW);
        }

        int recentHitCount = members.size();
        Set<String> distinctDecoys = new HashSet<>();
        RiskLevel highestRiskLevel = null;

        for (String member : members) {
            String[] parts = member.split(":");
            if (parts.length >= 2) {
                String decoyId = parts[0];
                RiskLevel risk = parseRiskLevel(parts[1]);

                distinctDecoys.add(decoyId);

                if (highestRiskLevel == null || risk.ordinal() > highestRiskLevel.ordinal()) {
                    highestRiskLevel = risk;
                }
            }
        }

        if (highestRiskLevel == null) {
            highestRiskLevel = RiskLevel.LOW;
        }

        return new CorrelationSnapshot(recentHitCount, distinctDecoys.size(), highestRiskLevel);
    }

    private RiskLevel parseRiskLevel(String value) {
        try {
            return RiskLevel.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return RiskLevel.LOW;
        }
    }
}
