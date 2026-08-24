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
    private static final long WINDOW_DURATION_MS = 5 * 60 * 1000L; // 5-minute rolling window in milliseconds
    private static final Duration KEY_TTL = Duration.ofMinutes(10); // 10-minute expiry for inactive source IP keys

    private final StringRedisTemplate redisTemplate;

    public CorrelationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Correlates an incoming TelemetryEvent for a source IP by:
     * 1. Adding the event to the source IP's Redis sorted set (ZSET) with timestamp as score.
     * 2. Removing entries older than 5 minutes from the sorted set.
     * 3. Reading the remaining active entries to compute the CorrelationSnapshot.
     * 4. Refreshing the key's TTL to 10 minutes so inactive keys expire automatically.
     */
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

        // Deterministic member format: <decoyId>:<riskLevel>:<eventTimeMs>:<uniqueSuffix>
        // The unique UUID suffix guarantees member uniqueness in the Redis ZSET if multiple hits occur at the exact same millisecond.
        String member = decoyId + ":" + riskLevel.name() + ":" + eventTimeMs + ":" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Add event member to Redis ZSET with timestamp score
        redisTemplate.opsForZSet().add(redisKey, member, (double) eventTimeMs);

        // 2. Remove entries older than 5 minutes (score strictly less than windowStartMs)
        long windowStartMs = eventTimeMs - WINDOW_DURATION_MS;
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, (double) (windowStartMs - 1));

        // 3. Read remaining active members within the 5-minute window
        Set<String> remainingMembers = redisTemplate.opsForZSet().rangeByScore(redisKey, (double) windowStartMs, Double.MAX_VALUE);

        // 4. Calculate correlation metrics
        CorrelationSnapshot snapshot = computeSnapshotFromMembers(remainingMembers);

        // 5. Refresh TTL so inactive keys auto-expire after 10 minutes
        redisTemplate.expire(redisKey, KEY_TTL);

        log.debug("Correlated source IP {}: recentHits={}, distinctDecoys={}, highestRisk={}",
                sourceIp, snapshot.recentHitCount(), snapshot.distinctDecoyCount(), snapshot.highestRiskLevel());

        return snapshot;
    }

    /**
     * Reads the current correlation snapshot for a given source IP without inserting a new event.
     */
    public CorrelationSnapshot getCorrelationSnapshot(String sourceIp) {
        if (sourceIp == null || sourceIp.isBlank()) {
            return new CorrelationSnapshot(0, 0, RiskLevel.LOW);
        }
        String redisKey = KEY_PREFIX + sourceIp;
        long nowMs = System.currentTimeMillis();
        long windowStartMs = nowMs - WINDOW_DURATION_MS;

        // Prune stale entries older than 5 minutes
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, (double) (windowStartMs - 1));

        Set<String> remainingMembers = redisTemplate.opsForZSet().rangeByScore(redisKey, (double) windowStartMs, Double.MAX_VALUE);

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
