package com.honeymesh.threatengine.service;

import com.honeymesh.threatengine.event.TelemetryEvent;
import com.honeymesh.threatengine.event.ThreatAssessmentEvent;
import com.honeymesh.threatengine.listener.TelemetryListener;
import com.honeymesh.threatengine.model.CorrelationSnapshot;
import com.honeymesh.threatengine.model.RiskLevel;
import com.honeymesh.threatengine.model.ThreatAssessment;
import com.honeymesh.threatengine.model.ThreatLevel;
import com.honeymesh.threatengine.publisher.ThreatAssessmentPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryIdempotencyServicePhase8Test {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CorrelationService correlationService;

    @Mock
    private ThreatScoringService threatScoringService;

    @Mock
    private BlocklistService blocklistService;

    @Mock
    private ThreatAssessmentPublisher threatAssessmentPublisher;

    @Mock
    private ThreatAssessmentHistoryService historyService;

    private TelemetryIdempotencyService idempotencyService;
    private TelemetryListener telemetryListener;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        idempotencyService = new TelemetryIdempotencyService(redisTemplate);
        telemetryListener = new TelemetryListener(
                redisTemplate,
                correlationService,
                threatScoringService,
                blocklistService,
                threatAssessmentPublisher,
                historyService,
                idempotencyService
        );
    }

    @Test
    @DisplayName("Case 1 & 5: First delivery acquires atomic claim via SET NX")
    void testFirstDeliveryAcquiresClaim() {
        TelemetryEvent event = new TelemetryEvent("decoy-1", "10.0.0.1", "/api/v1/auth", RiskLevel.HIGH, Instant.parse("2026-08-23T10:00:00Z"));
        String fingerprint = idempotencyService.generateFingerprint(event);
        String expectedKey = "honeymesh:processed:telemetry:" + fingerprint;

        when(valueOperations.setIfAbsent(eq(expectedKey), eq("PROCESSING"), eq(TelemetryIdempotencyService.PROCESSING_TTL)))
                .thenReturn(true);

        boolean claimed = idempotencyService.tryClaim(event);

        assertThat(claimed).isTrue();
        verify(valueOperations).setIfAbsent(eq(expectedKey), eq("PROCESSING"), eq(TelemetryIdempotencyService.PROCESSING_TTL));
    }

    @Test
    @DisplayName("Case 2: Exact duplicate event returns false and is skipped by TelemetryListener")
    void testExactDuplicateIsSkipped() {
        Instant now = Instant.parse("2026-08-23T10:00:00Z");
        TelemetryEvent event = new TelemetryEvent("decoy-1", "10.0.0.1", "/api/v1/auth", RiskLevel.HIGH, now);
        String key = idempotencyService.getKey(idempotencyService.generateFingerprint(event));

        // First call claims successfully
        when(valueOperations.setIfAbsent(eq(key), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);

        CorrelationSnapshot snapshot = new CorrelationSnapshot(1, 1, RiskLevel.HIGH);
        ThreatAssessment assessment = new ThreatAssessment(65, ThreatLevel.HIGH, List.of("High risk"));
        when(correlationService.correlate(event)).thenReturn(snapshot);
        when(threatScoringService.assess(snapshot)).thenReturn(assessment);

        // Process first time -> succeeds
        telemetryListener.onTelemetryEvent(event);
        verify(threatAssessmentPublisher, times(1)).publish(any());
        verify(valueOperations).set(eq(key), eq("DONE"), eq(TelemetryIdempotencyService.COMPLETED_TTL));

        // Process exact duplicate -> skipped
        telemetryListener.onTelemetryEvent(event);
        verify(threatAssessmentPublisher, times(1)).publish(any()); // count remains 1
    }

    @Test
    @DisplayName("Case 3: Two legitimate requests with different occurredAt produce different fingerprints")
    void testLegitimateRequestsDifferentTimestamps() {
        TelemetryEvent eventA = new TelemetryEvent("decoy-1", "10.0.0.1", "/api/v1/auth", RiskLevel.HIGH, Instant.parse("2026-08-23T10:00:01Z"));
        TelemetryEvent eventB = new TelemetryEvent("decoy-1", "10.0.0.1", "/api/v1/auth", RiskLevel.HIGH, Instant.parse("2026-08-23T10:00:03Z"));

        String fpA = idempotencyService.generateFingerprint(eventA);
        String fpB = idempotencyService.generateFingerprint(eventB);

        assertThat(fpA).isNotEqualTo(fpB);
    }

    @Test
    @DisplayName("Case 4: Events differing in riskLevel or decoyId produce different fingerprints")
    void testDifferentRiskOrDecoy() {
        Instant now = Instant.parse("2026-08-23T10:00:00Z");
        TelemetryEvent eventA = new TelemetryEvent("decoy-1", "10.0.0.1", "/api/v1/auth", RiskLevel.LOW, now);
        TelemetryEvent eventB = new TelemetryEvent("decoy-2", "10.0.0.1", "/api/v1/auth", RiskLevel.LOW, now);
        TelemetryEvent eventC = new TelemetryEvent("decoy-1", "10.0.0.1", "/api/v1/auth", RiskLevel.CRITICAL, now);

        String fpA = idempotencyService.generateFingerprint(eventA);
        String fpB = idempotencyService.generateFingerprint(eventB);
        String fpC = idempotencyService.generateFingerprint(eventC);

        assertThat(fpA).isNotEqualTo(fpB);
        assertThat(fpA).isNotEqualTo(fpC);
        assertThat(fpB).isNotEqualTo(fpC);
    }

    @Test
    @DisplayName("Case 6: Processing failure releases the PROCESSING claim key")
    void testProcessingFailureReleasesClaim() {
        TelemetryEvent event = new TelemetryEvent("decoy-1", "10.0.0.1", "/api/v1/auth", RiskLevel.HIGH, Instant.now());
        String key = idempotencyService.getKey(idempotencyService.generateFingerprint(event));

        when(valueOperations.setIfAbsent(eq(key), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
        when(correlationService.correlate(event)).thenThrow(new RuntimeException("Simulated Redis failure"));

        assertThatThrownBy(() -> telemetryListener.onTelemetryEvent(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated Redis failure");

        verify(redisTemplate).delete(key);
    }

    @Test
    @DisplayName("Case 7: Completed marker is set to DONE with 24-hour TTL")
    void testCompletedMarkerSetToDone() {
        TelemetryEvent event = new TelemetryEvent("decoy-1", "10.0.0.1", "/api/v1/auth", RiskLevel.LOW, Instant.now());
        String key = idempotencyService.getKey(idempotencyService.generateFingerprint(event));

        idempotencyService.markCompleted(event);

        verify(valueOperations).set(eq(key), eq("DONE"), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("Case 8: Processing marker uses 5-minute TTL")
    void testProcessingMarkerUsesShortTtl() {
        TelemetryEvent event = new TelemetryEvent("decoy-1", "10.0.0.1", "/api/v1/auth", RiskLevel.LOW, Instant.now());
        String key = idempotencyService.getKey(idempotencyService.generateFingerprint(event));

        when(valueOperations.setIfAbsent(eq(key), eq("PROCESSING"), eq(Duration.ofMinutes(5)))).thenReturn(true);

        boolean claimed = idempotencyService.tryClaim(event);

        assertThat(claimed).isTrue();
        verify(valueOperations).setIfAbsent(eq(key), eq("PROCESSING"), eq(Duration.ofMinutes(5)));
    }
}
