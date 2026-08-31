package com.honeymesh.threatengine.service;

import com.honeymesh.threatengine.event.TelemetryEvent;
import com.honeymesh.threatengine.model.CorrelationSnapshot;
import com.honeymesh.threatengine.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CorrelationServiceTest {

    private StringRedisTemplate redisTemplate;
    private ZSetOperations<String, String> zSetOperations;
    private CorrelationService correlationService;

    // Simulated Redis ZSET store per key: Map<key, Map<member, score>>
    private Map<String, Map<String, Double>> redisStore;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        redisStore = new HashMap<>();

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // Mock ZADD
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String member = invocation.getArgument(1);
            Double score = invocation.getArgument(2);
            redisStore.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(member, score);
            return true;
        });

        // Mock ZREMRANGEBYSCORE
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Double min = invocation.getArgument(1);
            Double max = invocation.getArgument(2);
            Map<String, Double> members = redisStore.get(key);
            if (members == null) return 0L;

            long removedCount = 0;
            Iterator<Map.Entry<String, Double>> it = members.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Double> entry = it.next();
                if (entry.getValue() >= min && entry.getValue() <= max) {
                    it.remove();
                    removedCount++;
                }
            }
            return removedCount;
        });

        // Mock ZRANGEBYSCORE
        when(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Double min = invocation.getArgument(1);
            Double max = invocation.getArgument(2);
            Map<String, Double> members = redisStore.get(key);
            if (members == null) return Collections.emptySet();

            Set<String> result = new LinkedHashSet<>();
            for (Map.Entry<String, Double> entry : members.entrySet()) {
                if (entry.getValue() >= min && entry.getValue() <= max) {
                    result.add(entry.getKey());
                }
            }
            return result;
        });

        correlationService = new CorrelationService(redisTemplate);
    }

    @Test
    @DisplayName("1. One event from IP A: recentHitCount = 1, distinctDecoyCount = 1")
    void testOneEventSingleIp() {
        Instant now = Instant.now();
        TelemetryEvent event = new TelemetryEvent("decoy-1", "192.168.1.100", "/api/test", RiskLevel.LOW, now);

        CorrelationSnapshot snapshot = correlationService.correlate(event);

        assertThat(snapshot.recentHitCount()).isEqualTo(1);
        assertThat(snapshot.distinctDecoyCount()).isEqualTo(1);
        assertThat(snapshot.highestRiskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("2. Three events from IP A hitting the same decoy: recentHitCount = 3, distinctDecoyCount = 1")
    void testThreeEventsSameDecoy() {
        Instant now = Instant.now();
        TelemetryEvent e1 = new TelemetryEvent("decoy-1", "192.168.1.100", "/api/test1", RiskLevel.LOW, now);
        TelemetryEvent e2 = new TelemetryEvent("decoy-1", "192.168.1.100", "/api/test2", RiskLevel.LOW, now.plusMillis(100));
        TelemetryEvent e3 = new TelemetryEvent("decoy-1", "192.168.1.100", "/api/test3", RiskLevel.LOW, now.plusMillis(200));

        correlationService.correlate(e1);
        correlationService.correlate(e2);
        CorrelationSnapshot snapshot = correlationService.correlate(e3);

        assertThat(snapshot.recentHitCount()).isEqualTo(3);
        assertThat(snapshot.distinctDecoyCount()).isEqualTo(1);
        assertThat(snapshot.highestRiskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("3. Three events from IP A hitting three different decoys: recentHitCount = 3, distinctDecoyCount = 3")
    void testThreeEventsThreeDecoys() {
        Instant now = Instant.now();
        TelemetryEvent e1 = new TelemetryEvent("decoy-1", "192.168.1.100", "/api/test1", RiskLevel.LOW, now);
        TelemetryEvent e2 = new TelemetryEvent("decoy-2", "192.168.1.100", "/api/test2", RiskLevel.MEDIUM, now.plusMillis(100));
        TelemetryEvent e3 = new TelemetryEvent("decoy-3", "192.168.1.100", "/api/test3", RiskLevel.HIGH, now.plusMillis(200));

        correlationService.correlate(e1);
        correlationService.correlate(e2);
        CorrelationSnapshot snapshot = correlationService.correlate(e3);

        assertThat(snapshot.recentHitCount()).isEqualTo(3);
        assertThat(snapshot.distinctDecoyCount()).isEqualTo(3);
        assertThat(snapshot.highestRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("4. Events from IP B do not affect IP A's snapshot")
    void testIpIsolation() {
        Instant now = Instant.now();
        TelemetryEvent eIpA = new TelemetryEvent("decoy-1", "10.0.0.1", "/api/test", RiskLevel.LOW, now);
        TelemetryEvent eIpB1 = new TelemetryEvent("decoy-2", "10.0.0.2", "/api/test", RiskLevel.CRITICAL, now);
        TelemetryEvent eIpB2 = new TelemetryEvent("decoy-3", "10.0.0.2", "/api/test", RiskLevel.HIGH, now.plusMillis(100));

        correlationService.correlate(eIpA);
        correlationService.correlate(eIpB1);
        CorrelationSnapshot snapshotB = correlationService.correlate(eIpB2);

        CorrelationSnapshot snapshotA = correlationService.getCorrelationSnapshot("10.0.0.1");

        assertThat(snapshotA.recentHitCount()).isEqualTo(1);
        assertThat(snapshotA.distinctDecoyCount()).isEqualTo(1);
        assertThat(snapshotA.highestRiskLevel()).isEqualTo(RiskLevel.LOW);

        assertThat(snapshotB.recentHitCount()).isEqualTo(2);
        assertThat(snapshotB.distinctDecoyCount()).isEqualTo(2);
        assertThat(snapshotB.highestRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    @DisplayName("5. Highest risk is determined correctly: LOW + HIGH + MEDIUM -> highestRiskLevel = HIGH")
    void testHighestRiskLevelCalculation() {
        Instant now = Instant.now();
        TelemetryEvent e1 = new TelemetryEvent("decoy-1", "172.16.0.5", "/api/1", RiskLevel.LOW, now);
        TelemetryEvent e2 = new TelemetryEvent("decoy-2", "172.16.0.5", "/api/2", RiskLevel.HIGH, now.plusMillis(100));
        TelemetryEvent e3 = new TelemetryEvent("decoy-3", "172.16.0.5", "/api/3", RiskLevel.MEDIUM, now.plusMillis(200));

        correlationService.correlate(e1);
        correlationService.correlate(e2);
        CorrelationSnapshot snapshot = correlationService.correlate(e3);

        assertThat(snapshot.recentHitCount()).isEqualTo(3);
        assertThat(snapshot.highestRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("6. Events older than the 5-minute window are excluded")
    void testRollingWindowExclusion() {
        Instant t0 = Instant.parse("2026-08-23T12:00:00Z");
        Instant t6Min = t0.plus(Duration.ofMinutes(6)); // 6 minutes after t0

        TelemetryEvent oldEvent = new TelemetryEvent("decoy-old", "192.168.1.50", "/api/old", RiskLevel.CRITICAL, t0);
        TelemetryEvent newEvent = new TelemetryEvent("decoy-new", "192.168.1.50", "/api/new", RiskLevel.LOW, t6Min);

        correlationService.correlate(oldEvent);
        CorrelationSnapshot snapshotAfter6Min = correlationService.correlate(newEvent);

        // The old event (6 minutes ago) should be purged by the 5-minute window
        assertThat(snapshotAfter6Min.recentHitCount()).isEqualTo(1);
        assertThat(snapshotAfter6Min.distinctDecoyCount()).isEqualTo(1);
        assertThat(snapshotAfter6Min.highestRiskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("7. Key TTL is refreshed to 10 minutes")
    void testKeyTtlSet() {
        Instant now = Instant.now();
        TelemetryEvent event = new TelemetryEvent("decoy-1", "192.168.1.100", "/api/test", RiskLevel.LOW, now);

        correlationService.correlate(event);

        verify(redisTemplate).expire(eq("honeymesh:correlation:source:192.168.1.100"), eq(Duration.ofMinutes(10)));
    }
}
